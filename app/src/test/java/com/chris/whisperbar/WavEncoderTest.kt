package com.chris.whisperbar

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM-Unit-Tests fuer den WAV-Encoder (API-Upload). */
class WavEncoderTest {

    private fun le16(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)
    private fun le32(b: ByteArray, o: Int) = (b[o].toInt() and 0xFF) or
        ((b[o + 1].toInt() and 0xFF) shl 8) or
        ((b[o + 2].toInt() and 0xFF) shl 16) or
        ((b[o + 3].toInt() and 0xFF) shl 24)
    private fun ascii(b: ByteArray, o: Int, n: Int) = String(b, o, n, Charsets.US_ASCII)

    @Test fun headerGroesseUndFelder() {
        val wav = WavEncoder.encode(FloatArray(10), 16_000)
        assertEquals(44 + 20, wav.size)                 // 10 Samples * 2 Byte + 44 Header
        assertEquals("RIFF", ascii(wav, 0, 4))
        assertEquals("WAVE", ascii(wav, 8, 4))
        assertEquals("fmt ", ascii(wav, 12, 4))
        assertEquals("data", ascii(wav, 36, 4))
        assertEquals(1, le16(wav, 20))                  // PCM
        assertEquals(1, le16(wav, 22))                  // mono
        assertEquals(16_000, le32(wav, 24))             // sample rate
        assertEquals(16, le16(wav, 34))                 // bits/sample
        assertEquals(20, le32(wav, 40))                 // data-Laenge
    }

    @Test fun leererInputNurHeader() {
        assertEquals(44, WavEncoder.encode(FloatArray(0), 16_000).size)
    }

    @Test fun sampleWertKorrektKodiert() {
        val wav = WavEncoder.encode(floatArrayOf(1.0f), 16_000)
        // 1.0 -> 32767 -> little-endian 0xFF 0x7F
        assertEquals(0xFF, wav[44].toInt() and 0xFF)
        assertEquals(0x7F, wav[45].toInt() and 0xFF)
    }
}
