package com.chris.whisperloom.api

import com.chris.whisperloom.AudioUtils
import com.chris.whisperloom.WavEncoder
import java.io.ByteArrayOutputStream
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
    /** Originale Samples, falls vorhanden — erspart [readSamples] den Umweg ueber PCM. */
    private val samples: FloatArray? = null,
    val writePcm: (OutputStream) -> Unit,
) {
    /** Gesamtgroesse der hochgeladenen Datei (Kopf + Daten). */
    val totalBytes: Int get() = WavEncoder.HEADER_SIZE + pcmByteCount

    /**
     * Die Samples als Float [-1, 1] — fuer die Offline-Erkennung, die kein WAV, sondern
     * einen Puffer will. Beim Diktat die Originale; bei gestreamten Stuecken wird das
     * PCM einmal in den Speicher gelesen (ein 5-Minuten-Stueck ≈ 19 MB Float).
     */
    fun readSamples(): FloatArray {
        samples?.let { return it }
        val pcm = ByteArrayOutputStream(pcmByteCount).also(writePcm).toByteArray()
        return WavEncoder.samples(pcm)
    }

    companion object {
        fun fromSamples(samples: FloatArray, sampleRate: Int = AudioUtils.SAMPLE_RATE): WavUpload {
            val pcm = WavEncoder.pcmBytes(samples)
            return WavUpload(pcm.size, sampleRate, samples) { it.write(pcm) }
        }
    }
}
