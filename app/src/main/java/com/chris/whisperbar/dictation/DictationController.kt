package com.chris.whisperbar.dictation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.chris.whisperbar.ApiNotConfiguredException
import com.chris.whisperbar.AudioRecorder
import com.chris.whisperbar.DictationSettings
import com.chris.whisperbar.ModelNotAvailableException
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.TextPolisher
import com.chris.whisperbar.WhisperEngine
import java.util.concurrent.Executors

/** Was gerade passiert — steuert die komplette Darstellung in Tastatur und Blase. */
enum class DictationPhase { IDLE, RECORDING, WORKING }

/**
 * Die eine Diktat-Pipeline: Aufnahme -> Stille trimmen -> Whisper -> Text polieren.
 *
 * Vorher lag diese Logik doppelt in der Tastatur und im schwebenden Knopf — inklusive
 * je eigener Fehlerbehandlung, die schon auseinandergelaufen war. Jetzt gibt es eine
 * Implementierung, die beide UIs ueber [Listener] bedienen.
 *
 * ### Bedienkonzept
 * Ein einziger Knopf beherrscht beides, ohne dass man etwas umstellen muss:
 *  - **Halten und sprechen** — beim Loslassen wird transkribiert (klassisch).
 *  - **Kurz tippen** — die Aufnahme laeuft freihaendig weiter und endet nach einer
 *    Sprechpause von selbst (oder beim naechsten Tippen).
 *
 * Unterschieden wird allein an der Druckdauer ([TAP_MS]), es gibt keinen Moduswechsel.
 */
class DictationController(
    context: Context,
    private val listener: Listener,
) {

    interface Listener {
        /** Phasenwechsel — immer auf dem Main-Thread. */
        fun onPhase(phase: DictationPhase)

        /** Pegel 0..1 waehrend der Aufnahme (max. ~30/s) — Main-Thread. */
        fun onLevel(level: Float)

        /** Fertiger, polierter Text (nie leer) — Main-Thread. */
        fun onResult(text: String)

        /** Fehler als String-Ressource — Main-Thread. */
        fun onError(messageRes: Int)
    }

    private val app = context.applicationContext
    private val prefs = Prefs.get(app)
    private val recorder = AudioRecorder()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wb-dictation") }
    private val main = Handler(Looper.getMainLooper())
    private val vibrator = app.getSystemService(Vibrator::class.java)

    @Volatile private var phaseInternal = DictationPhase.IDLE

    /** Momentaufnahme der Einstellungen bei Aufnahmestart — der IO-Thread liest danach nichts mehr. */
    private var settings: DictationSettings = prefs.dictationSettings()

    private var pressedAt = 0L
    private var stopOnRelease = false

    val phase: DictationPhase get() = phaseInternal
    val isRecording: Boolean get() = phaseInternal == DictationPhase.RECORDING

    init {
        recorder.onLevel = { level -> main.post { listener.onLevel(level) } }
        recorder.onSilence = { main.post { stop() } }
        recorder.onMaxDuration = { main.post { stop() } }
    }

    // --- Gesten -------------------------------------------------------------

    /** Finger auf dem Knopf. */
    fun onPressDown() {
        when (phaseInternal) {
            DictationPhase.WORKING -> return
            DictationPhase.RECORDING -> {
                // Laeuft bereits freihaendig -> dieser Druck beendet das Diktat.
                stopOnRelease = true
            }
            DictationPhase.IDLE -> {
                pressedAt = SystemClock.uptimeMillis()
                stopOnRelease = false
                start()
            }
        }
    }

    /** Finger vom Knopf (auch bei ACTION_CANCEL). */
    fun onPressUp() {
        if (stopOnRelease) {
            stopOnRelease = false
            stop()
            return
        }
        if (phaseInternal != DictationPhase.RECORDING) return

        val held = SystemClock.uptimeMillis() - pressedAt
        if (settings.handsFree && held < TAP_MS) {
            // Kurzer Tipp: freihaendig weiterlaufen lassen und auf eine Sprechpause warten.
            recorder.silenceTimeoutMs = settings.autoStopMs
            return
        }
        stop()
    }

    /** Direktes Umschalten ohne Halten-Erkennung (schwebender Knopf). */
    fun toggle() {
        when (phaseInternal) {
            DictationPhase.WORKING -> return
            DictationPhase.RECORDING -> stop()
            DictationPhase.IDLE -> {
                start()
                recorder.silenceTimeoutMs = settings.autoStopMs
            }
        }
    }

    // --- Ablauf -------------------------------------------------------------

    private fun start() {
        if (!hasMicPermission()) {
            listener.onError(R.string.kb_need_permission)
            return
        }
        settings = prefs.dictationSettings()
        recorder.silenceTimeoutMs = 0 // erst beim Loslassen als Freihand markieren
        if (!recorder.start()) {
            listener.onError(R.string.kb_error)
            return
        }
        setPhase(DictationPhase.RECORDING)
        buzz(HAPTIC_START)
        // Modell parallel zur Aufnahme laden — bis der Nutzer fertig gesprochen hat,
        // ist es warm und die Transkription startet ohne Ladezeit.
        io.submit { WhisperEngine.preload(app) }
    }

    /** Aufnahme beenden und transkribieren. Idempotent. */
    fun stop() {
        if (phaseInternal != DictationPhase.RECORDING) return
        setPhase(DictationPhase.WORKING)
        buzz(HAPTIC_STOP)

        val snapshot = settings
        io.submit {
            try {
                // stop() joint den Aufnahme-Thread — bewusst hier und nie auf dem
                // Main-Thread, sonst friert die Oberflaeche beim Loslassen ein.
                val audio = recorder.stop()
                if (audio.seconds() < AudioRecorder.MIN_SECONDS) {
                    finish(null) // versehentlicher Tap — kommentarlos verwerfen
                    return@submit
                }
                val raw = WhisperEngine.transcribe(app, audio, snapshot.language)
                var text = TextPolisher.polish(raw, snapshot.polish)
                if (text.isBlank()) {
                    finish(null)
                    return@submit
                }
                if (snapshot.trailingSpace) text += " "
                finish(text)
            } catch (e: ModelNotAvailableException) {
                fail(R.string.model_not_loaded)
            } catch (e: ApiNotConfiguredException) {
                fail(R.string.api_not_configured)
            } catch (e: Exception) {
                Log.e(TAG, "Transkription fehlgeschlagen", e)
                fail(R.string.kb_error)
            }
        }
    }

    /** Laufende Aufnahme verwerfen, ohne zu transkribieren. */
    fun cancel() {
        stopOnRelease = false
        if (phaseInternal == DictationPhase.RECORDING) buzz(HAPTIC_CANCEL)
        recorder.cancel()
        setPhase(DictationPhase.IDLE)
    }

    private fun finish(text: String?) = main.post {
        setPhase(DictationPhase.IDLE)
        if (text != null) listener.onResult(text)
    }

    private fun fail(messageRes: Int) = main.post {
        setPhase(DictationPhase.IDLE)
        listener.onError(messageRes)
    }

    private fun setPhase(next: DictationPhase) {
        if (phaseInternal == next) return
        phaseInternal = next
        if (Looper.myLooper() == Looper.getMainLooper()) listener.onPhase(next)
        else main.post { listener.onPhase(next) }
    }

    // --- Sonstiges ----------------------------------------------------------

    fun hasMicPermission(): Boolean =
        app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun buzz(ms: Long) {
        if (!settings.haptics) return
        runCatching {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    /** Aufraeumen beim Ende der Tastatur bzw. des Dienstes. */
    fun shutdown() {
        recorder.cancel()
        io.shutdown()
    }

    companion object {
        private const val TAG = "WB-Dictation"

        /** Kuerzer gedrueckt = Tipp (Freihand), laenger = Halten (Push-to-talk). */
        const val TAP_MS = 350L

        private const val HAPTIC_START = 18L
        private const val HAPTIC_STOP = 12L
        private const val HAPTIC_CANCEL = 30L
    }
}
