package com.chris.whisperbar

import java.io.ByteArrayOutputStream

/**
 * Erzeugt 16-bit-PCM-WAV — fuer den API-Upload. Rein (ohne Android), damit JVM-unit-testbar.
 *
 * Der Kopf ist getrennt vom Datenteil abrufbar, damit lange Aufnahmen direkt aus einer
 * Datei in die Verbindung gestroemt werden koennen, statt komplett im Speicher zu liegen.
 */
object WavEncoder {

    const val HEADER_SIZE = 44

    /** WAV-Kopf fuer [dataLen] Bytes PCM16 Mono. */
    fun header(dataLen: Int, sampleRate: Int = AudioUtils.SAMPLE_RATE): ByteArray {
        val out = ByteArrayOutputStream(HEADER_SIZE)

        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))

        ascii("RIFF"); le32(36 + dataLen); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)          // PCM, 1 Kanal
        le32(sampleRate); le32(sampleRate * 2)             // byte rate = sr * 1 * 2
        le16(2); le16(16)                                  // block align, bits/sample
        ascii("data"); le32(dataLen)
        return out.toByteArray()
    }

    /** Float-Samples [-1,1] als little-endian PCM16. */
    fun pcmBytes(samples: FloatArray): ByteArray {
        val pcm = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val s = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[i * 2] = (s and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return pcm
    }

    fun encode(samples: FloatArray, sampleRate: Int = AudioUtils.SAMPLE_RATE): ByteArray {
        val pcm = pcmBytes(samples)
        val head = header(pcm.size, sampleRate)
        val out = ByteArrayOutputStream(head.size + pcm.size)
        out.write(head)
        out.write(pcm)
        return out.toByteArray()
    }
}
