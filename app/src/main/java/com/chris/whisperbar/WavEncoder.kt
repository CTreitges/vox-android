package com.chris.whisperbar

import java.io.ByteArrayOutputStream

/**
 * Kodiert 16-bit-PCM-WAV aus Float-Samples ([-1,1]) — fuer den API-Upload.
 * Rein (ohne Android), damit JVM-unit-testbar.
 */
object WavEncoder {

    fun encode(samples: FloatArray, sampleRate: Int = AudioUtils.SAMPLE_RATE): ByteArray =
        encode(AudioSlice.of(samples), sampleRate)

    /** Kodiert nur den Ausschnitt [audio] — ohne den Puffer vorher zu kopieren. */
    fun encode(audio: AudioSlice, sampleRate: Int = AudioUtils.SAMPLE_RATE): ByteArray {
        val pcm = ByteArray(audio.length * 2)
        val src = audio.data
        for (i in 0 until audio.length) {
            val s = (src[audio.offset + i].coerceIn(-1f, 1f) * 32767f).toInt()
            pcm[i * 2] = (s and 0xFF).toByte()
            pcm[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        val dataLen = pcm.size
        val out = ByteArrayOutputStream(44 + dataLen)

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
        out.write(pcm)
        return out.toByteArray()
    }
}
