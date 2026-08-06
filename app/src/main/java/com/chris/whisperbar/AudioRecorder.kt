package com.chris.whisperbar

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log
import kotlin.math.abs
import kotlin.math.min

/**
 * Nimmt Mikrofon-Audio als 16 kHz Mono auf und liefert es direkt als `FloatArray`
 * ([AudioSlice], Whisper-Format [-1, 1]).
 *
 * Wichtig fuer die gefuehlte Geschwindigkeit: die PCM16->Float-Wandlung passiert
 * **waehrend** der Aufnahme im Aufnahme-Thread, nicht erst beim Loslassen. Frueher
 * lief hier `ByteArrayOutputStream` -> `toByteArray()` -> `FloatArray` — drei
 * Durchlaeufe ueber die kompletten Daten, alle nach dem Loslassen und damit direkt
 * in der Wartezeit des Nutzers. Jetzt bleibt beim Stoppen nur noch ein `join`.
 *
 * Zusaetzlich:
 *  - [onLevel] wird gedrosselt (max. ~30/s) aufgerufen, statt bei jedem Lesevorgang.
 *  - [onSilence] meldet Sprechpausen fuer die automatische Diktat-Beendigung (VAD).
 *  - Harte Laengenbegrenzung ([MAX_SECONDS]), damit ein vergessenes Diktat nicht
 *    unbegrenzt Speicher frisst.
 *
 * Braucht die RECORD_AUDIO-Berechtigung (der Aufrufer stellt sie sicher).
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = AudioUtils.SAMPLE_RATE
        private const val TAG = "WB-AudioRecorder"
        private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** Aufnahmen darunter sind fast immer versehentliche Taps. */
        const val MIN_SECONDS = 0.3f

        /** Obergrenze; danach stoppt die Aufnahme selbst (≈ 7,7 MB Puffer). */
        const val MAX_SECONDS = 120

        /** Startgroesse des Puffers — deckt ein normales Diktat ohne Wachstum ab. */
        private const val INITIAL_SECONDS = 12

        /** Hoechstens so oft pro Sekunde den Pegel melden (UI braucht nicht mehr). */
        private const val LEVEL_HZ = 30

        /** Unter diesem RMS gilt ein Block als Stille. */
        private const val SILENCE_RMS = 0.012f

        /** So laut muss es einmal gewesen sein, damit die Pause als "fertig" zaehlt. */
        private const val SPEECH_RMS = 0.03f
    }

    @Volatile private var recording = false
    private var thread: Thread? = null

    /** Wachsender Float-Puffer; nur der Aufnahme-Thread schreibt, danach Barriere ueber [thread.join]. */
    private var buffer = FloatArray(SAMPLE_RATE * INITIAL_SECONDS)
    @Volatile private var written = 0

    /** Pegel 0..1 fuer die Visualisierung — gedrosselt, immer vom Aufnahme-Thread. */
    var onLevel: ((Float) -> Unit)? = null

    /**
     * Wird einmal gerufen, wenn nach echtem Sprechen [silenceTimeoutMs] lang Stille war.
     * Der Aufrufer entscheidet, ob er deshalb stoppt (Freihand-Modus).
     */
    var onSilence: (() -> Unit)? = null

    /** Wird gerufen, wenn [MAX_SECONDS] erreicht sind und die Aufnahme selbst endet. */
    var onMaxDuration: (() -> Unit)? = null

    /** Pausenlaenge bis [onSilence] feuert. <= 0 schaltet die Erkennung ab. */
    @Volatile var silenceTimeoutMs: Int = 0

    val isRecording: Boolean get() = recording

    /** Bisher aufgenommene Sekunden (fuer die Live-Anzeige). */
    val seconds: Float get() = written.toFloat() / SAMPLE_RATE

    @SuppressLint("MissingPermission") // Aufrufer stellt die Berechtigung sicher.
    fun start(): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            Log.e(TAG, "getMinBufferSize fehlgeschlagen: $minBuf")
            return false
        }
        val bufferSize = minBuf * 2
        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE, CHANNEL, ENCODING, bufferSize,
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Keine RECORD_AUDIO-Berechtigung", e)
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord nicht initialisiert")
            recorder.release()
            return false
        }
        enableEffects(recorder.audioSessionId)

        written = 0
        recording = true
        recorder.startRecording()

        thread = Thread {
            // Audio-Prioritaet: verhindert Aussetzer, wenn die UI gerade animiert.
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            captureLoop(recorder, bufferSize / 2)
        }.apply { name = "wb-capture"; start() }
        return true
    }

    private fun captureLoop(recorder: AudioRecord, blockSize: Int) {
        val buf = ShortArray(blockSize)
        val limit = SAMPLE_RATE * MAX_SECONDS
        var lastLevelAt = 0L
        var silentMs = 0
        var sawSpeech = false
        var silenceFired = false
        try {
            while (recording) {
                val read = recorder.read(buf, 0, buf.size)
                if (read <= 0) continue
                // Nie mehr uebernehmen, als bis zur Hoechstdauer hineinpasst — sonst
                // liefe der letzte Block ueber das Ende des gedeckelten Puffers hinaus.
                val n = minOf(read, limit - written)
                if (n <= 0) {
                    recording = false
                    onMaxDuration?.invoke()
                    break
                }

                ensureCapacity(written + n)
                val dst = buffer
                var at = written
                var peak = 0f
                var energy = 0.0
                for (i in 0 until n) {
                    val f = buf[i] / 32768f
                    dst[at++] = f
                    val a = abs(f)
                    if (a > peak) peak = a
                    energy += f.toDouble() * f
                }
                written = at

                val rms = kotlin.math.sqrt(energy / n).toFloat()
                val blockMs = n * 1000 / SAMPLE_RATE

                // --- Pegel, gedrosselt ---
                val now = System.currentTimeMillis()
                if (now - lastLevelAt >= 1000 / LEVEL_HZ) {
                    lastLevelAt = now
                    // Mischung aus Spitze und RMS: reagiert schnell, zappelt aber nicht.
                    onLevel?.invoke(min(1f, peak * 0.4f + rms * 3.5f))
                }

                // --- Sprechpausen-Erkennung ---
                val timeout = silenceTimeoutMs
                if (timeout > 0 && !silenceFired) {
                    if (rms >= SPEECH_RMS) {
                        sawSpeech = true
                        silentMs = 0
                    } else if (rms < SILENCE_RMS) {
                        silentMs += blockMs
                        if (sawSpeech && silentMs >= timeout) {
                            silenceFired = true
                            onSilence?.invoke()
                        }
                    } else {
                        silentMs = 0
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Aufnahme-Loop-Fehler", e)
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            recorder.release()
        }
    }

    /**
     * Verdoppelt den Puffer bei Bedarf — amortisiert konstante Kosten pro Sample.
     * [needed] liegt durch die Deckelung im Aufnahme-Loop nie ueber der Hoechstdauer.
     */
    private fun ensureCapacity(needed: Int) {
        if (needed <= buffer.size) return
        val limit = SAMPLE_RATE * MAX_SECONDS
        var size = buffer.size
        while (size < needed) size *= 2
        buffer = buffer.copyOf(size.coerceIn(needed, limit))
    }

    /**
     * Stoppt die Aufnahme und liefert das Ergebnis als Sicht auf den internen Puffer —
     * ohne Kopie. Die Sicht ist gueltig, bis [start] das naechste Mal laeuft.
     */
    fun stop(): AudioSlice {
        if (!recording && thread == null) return AudioSlice.EMPTY
        recording = false
        try { thread?.join(2_000) } catch (_: InterruptedException) {}
        thread = null
        return AudioSlice(buffer, 0, written)
    }

    /** Aufnahme abbrechen, Samples verwerfen. */
    fun cancel() {
        recording = false
        try { thread?.join(1_000) } catch (_: InterruptedException) {}
        thread = null
        written = 0
    }

    /**
     * Aktiviert die Hardware-Vorverarbeitung, wo sie existiert. Kostet nichts und
     * bringt bei Freisprech-Abstand spuerbar sauberere Erkennung.
     */
    private fun enableEffects(sessionId: Int) {
        runCatching {
            if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(sessionId)?.enabled = true
        }
        runCatching {
            if (AutomaticGainControl.isAvailable()) AutomaticGainControl.create(sessionId)?.enabled = true
        }
        runCatching {
            if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(sessionId)?.enabled = true
        }
    }
}
