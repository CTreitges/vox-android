package com.chris.vox

import org.junit.Assert.assertArrayEquals
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

/** JVM-Unit-Tests fuer den getrennt abrufbaren WAV-Kopf (Streaming langer Aufnahmen). */
class WavHeaderTest {

    @Test fun kopfHatDieRichtigeLaenge() {
        assertEquals(WavEncoder.HEADER_SIZE, WavEncoder.header(0).size)
    }

    @Test fun kopfTraegtRiffUndDatenlaenge() {
        val h = WavEncoder.header(1000, 16_000)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))
        fun le32(at: Int) = (h[at].toInt() and 0xFF) or ((h[at + 1].toInt() and 0xFF) shl 8) or
            ((h[at + 2].toInt() and 0xFF) shl 16) or ((h[at + 3].toInt() and 0xFF) shl 24)
        assertEquals(36 + 1000, le32(4))   // RIFF-Groesse
        assertEquals(1000, le32(40))       // data-Groesse
        assertEquals(16_000, le32(24))     // Abtastrate
        assertEquals(32_000, le32(28))     // Byte-Rate = 16000 * 1 * 2
    }

    @Test fun kopfPlusDatenErgibtGenauDieKodierteDatei() {
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 1f)
        val whole = WavEncoder.encode(samples)
        val parts = WavEncoder.header(WavEncoder.pcmBytes(samples).size) + WavEncoder.pcmBytes(samples)
        assertArrayEquals(parts, whole)
    }
}

/** JVM-Unit-Tests fuer den Rueckweg PCM16 -> Float (Offline-Erkennung liest Uploads zurueck). */
class WavSamplesTest {

    @Test fun vollausschlagUndNull() {
        val s = WavEncoder.samples(byteArrayOf(0xFF.toByte(), 0x7F, 0, 0, 0, 0x80.toByte()))
        assertEquals(3, s.size)
        assertEquals(32767f / 32768f, s[0], 1e-6f)
        assertEquals(0f, s[1], 0f)
        assertEquals(-1f, s[2], 0f)
    }

    @Test fun ungeradeByteanzahlIgnoriertDenRest() {
        assertEquals(1, WavEncoder.samples(byteArrayOf(1, 0, 5)).size)
    }
}
