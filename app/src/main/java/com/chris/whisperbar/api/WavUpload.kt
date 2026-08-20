package com.chris.whisperbar.api

import com.chris.whisperbar.AudioUtils
import com.chris.whisperbar.WavEncoder
import java.io.OutputStream

/**
 * Ein WAV-Upload: wie viele PCM-Bytes kommen und wer sie schreibt.
 *
 * Diese Indirektion existiert, damit eine lange geteilte Sprachnachricht direkt aus der
 * entpackten PCM-Datei in die Verbindung gestroemt werden kann — sonst laege eine
 * Zehn-Minuten-Aufnahme mehrfach komplett im Speicher.
 */
class WavUpload(
    val pcmByteCount: Int,
    val sampleRate: Int = AudioUtils.SAMPLE_RATE,
    val writePcm: (OutputStream) -> Unit,
) {
    /** Gesamtgroesse der hochgeladenen Datei (Kopf + Daten). */
    val totalBytes: Int get() = WavEncoder.HEADER_SIZE + pcmByteCount

    companion object {
        fun fromSamples(samples: FloatArray, sampleRate: Int = AudioUtils.SAMPLE_RATE): WavUpload {
            val pcm = WavEncoder.pcmBytes(samples)
            return WavUpload(pcm.size, sampleRate) { it.write(pcm) }
        }
    }
}
