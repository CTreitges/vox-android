package com.chris.whisperbar

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Geworfen, wenn eine geteilte Datei kein abspielbares Audio enthaelt. */
class UnsupportedAudioException(message: String) : RuntimeException(message)

/**
 * Ergebnis des Entpackens: die PCM-Datei plus ein grobes Lautstaerke-Profil, an dem
 * lange Aufnahmen an leisen Stellen geteilt werden koennen.
 */
data class DecodedAudio(
    /** 16 kHz Mono PCM16, little-endian, ohne WAV-Kopf. */
    val pcmFile: File,
    val frameCount: Int,
    /** RMS je [framesPerProfileEntry] Frames. */
    val profile: FloatArray,
    val framesPerProfileEntry: Int,
) {
    val durationMs: Long get() = frameCount * 1000L / AudioConvert.TARGET_RATE
}

/**
 * Packt eine geteilte Audiodatei (WhatsApp-Sprachnachricht, Aufnahme-App, Messenger …)
 * mit Androids eigenen Decodern nach 16 kHz Mono PCM16 aus.
 *
 * Warum nicht die Originaldatei hochladen: die Transkriptions-API nennt mp3, mp4, mpeg,
 * mpga, m4a, wav und webm als unterstuetzte Formate — WhatsApp-Sprachnachrichten sind
 * aber Opus im OGG-Container. Lokal dekodieren deckt alles ab, was das Geraet abspielen
 * kann, und erlaubt nebenbei das Stueckeln langer Aufnahmen.
 *
 * Geschrieben wird in eine Datei statt in den Speicher: eine halbe Stunde Sprache waeren
 * sonst gut 57 MB im Heap.
 */
object AudioDecoder {

    /** Ein Profil-Eintrag deckt 100 ms ab — fein genug fuer Sprechpausen, klein im Speicher. */
    private const val PROFILE_MS = 100
    private const val TIMEOUT_US = 10_000L

    /**
     * @param onProgress 0..100, grob nach abgearbeiteter Spieldauer.
     * @param isCancelled wird regelmaessig abgefragt; true bricht sauber ab.
     * @throws UnsupportedAudioException wenn keine Audiospur gefunden oder decodiert werden kann.
     */
    fun decodeToPcm(
        context: Context,
        uri: Uri,
        target: File,
        onProgress: (Int) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): DecodedAudio {
        val extractor = MediaExtractor()
        try {
            try {
                extractor.setDataSource(context, uri, null)
            } catch (e: IOException) {
                throw UnsupportedAudioException("Datei konnte nicht gelesen werden: ${e.message}")
            }

            val track = audioTrack(extractor)
                ?: throw UnsupportedAudioException("Keine Audiospur in der Datei gefunden")
            extractor.selectTrack(track)
            val inputFormat = extractor.getTrackFormat(track)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw UnsupportedAudioException("Unbekanntes Audioformat")
            val totalUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }

            val codec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: IOException) {
                throw UnsupportedAudioException("Kein Decoder fuer $mime")
            }

            return try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                drain(codec, extractor, inputFormat, target, totalUs, onProgress, isCancelled)
            } catch (e: IllegalStateException) {
                throw UnsupportedAudioException("Decoder-Fehler bei $mime: ${e.message}")
            } catch (e: MediaCodec.CodecException) {
                throw UnsupportedAudioException("Decoder-Fehler bei $mime: ${e.message}")
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun audioTrack(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun drain(
        codec: MediaCodec,
        extractor: MediaExtractor,
        inputFormat: MediaFormat,
        target: File,
        totalUs: Long,
        onProgress: (Int) -> Unit,
        isCancelled: () -> Boolean,
    ): DecodedAudio {
        // Startwerte aus dem Eingabeformat; der Decoder meldet die echten Werte spaeter
        // ueber INFO_OUTPUT_FORMAT_CHANGED.
        var sampleRate = inputFormat.optInt(MediaFormat.KEY_SAMPLE_RATE, AudioConvert.TARGET_RATE)
        var channels = inputFormat.optInt(MediaFormat.KEY_CHANNEL_COUNT, 1)
        var pcmFloat = false

        val framesPerEntry = AudioConvert.TARGET_RATE * PROFILE_MS / 1000
        val profile = ArrayList<Float>(256)
        var pending = ShortArray(framesPerEntry)
        var pendingCount = 0
        var frameCount = 0
        var lastPercent = -1

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        BufferedOutputStream(target.outputStream(), 64 * 1024).use { out ->
            val outBytes = ByteArray(8192)

            fun flushProfileBlock() {
                if (pendingCount == 0) return
                profile.add(AudioConvert.rms(pending, 0, pendingCount))
                pendingCount = 0
            }

            fun writeMono(mono: ShortArray) {
                var i = 0
                while (i < mono.size) {
                    val n = minOf(outBytes.size / 2, mono.size - i)
                    for (k in 0 until n) {
                        val s = mono[i + k].toInt()
                        outBytes[k * 2] = (s and 0xFF).toByte()
                        outBytes[k * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                        pending[pendingCount++] = mono[i + k]
                        if (pendingCount == framesPerEntry) flushProfileBlock()
                    }
                    out.write(outBytes, 0, n * 2)
                    frameCount += n
                    i += n
                }
            }

            while (!outputDone) {
                if (isCancelled()) throw UnsupportedAudioException("Abgebrochen")

                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        sampleRate = f.optInt(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
                        channels = f.optInt(MediaFormat.KEY_CHANNEL_COUNT, channels)
                        pcmFloat = f.optInt(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT) ==
                            AudioFormat.ENCODING_PCM_FLOAT
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> {
                        if (outIndex >= 0) {
                            if (info.size > 0) {
                                val buf = codec.getOutputBuffer(outIndex)!!
                                val raw = readSamples(buf, info, pcmFloat)
                                val mono = AudioConvert.downmixToMono(raw, raw.size, channels)
                                writeMono(AudioConvert.resampleLinear(
                                    mono, mono.size, sampleRate, AudioConvert.TARGET_RATE,
                                ))
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                            if (totalUs > 0) {
                                val pct = ((info.presentationTimeUs * 100) / totalUs)
                                    .toInt().coerceIn(0, 100)
                                if (pct != lastPercent) { lastPercent = pct; onProgress(pct) }
                            }
                        }
                    }
                }
            }
            flushProfileBlock()
        }

        return DecodedAudio(
            pcmFile = target,
            frameCount = frameCount,
            profile = profile.toFloatArray(),
            framesPerProfileEntry = framesPerEntry,
        )
    }

    /** Decoder-Ausgabe als PCM16-Shorts, egal ob er 16-bit oder Float liefert. */
    private fun readSamples(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        pcmFloat: Boolean,
    ): ShortArray {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        return if (pcmFloat) {
            val fb = buffer.asFloatBuffer()
            ShortArray(fb.remaining()) { (fb.get(it).coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
        } else {
            val sb = buffer.asShortBuffer()
            ShortArray(sb.remaining()) { sb.get(it) }
        }
    }

    private fun MediaFormat.optInt(key: String, fallback: Int): Int =
        if (containsKey(key)) getInteger(key) else fallback
}
