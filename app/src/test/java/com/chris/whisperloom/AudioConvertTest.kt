package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Unit-Tests fuer die PCM-Umrechnungen geteilter Audios. */
class AudioConvertTest {

    @Test fun monoBleibtUnveraendert() {
        val input = shortArrayOf(1, 2, 3, 4)
        assertTrue(shortArrayOf(1, 2, 3, 4).contentEquals(
            AudioConvert.downmixToMono(input, 4, 1)))
    }

    @Test fun stereoWirdGemittelt() {
        // Frames: (10,20) -> 15, (30,50) -> 40
        val input = shortArrayOf(10, 20, 30, 50)
        assertTrue(shortArrayOf(15, 40).contentEquals(
            AudioConvert.downmixToMono(input, 4, 2)))
    }

    @Test fun downmixBeachtetDieBelegteLaenge() {
        // Puffer groesser als die belegten 4 Werte -> Rest wird ignoriert.
        val input = shortArrayOf(10, 20, 30, 50, 99, 99)
        assertEquals(2, AudioConvert.downmixToMono(input, 4, 2).size)
    }

    @Test fun gleicheRateWirdNichtAngefasst() {
        val input = shortArrayOf(1, 2, 3)
        assertTrue(input.contentEquals(AudioConvert.resampleLinear(input, 3, 16000, 16000)))
    }

    @Test fun halbierenLiefertHalbeLaenge() {
        val input = ShortArray(100) { (it * 10).toShort() }
        val out = AudioConvert.resampleLinear(input, 100, 32000, 16000)
        assertEquals(50, out.size)
        assertEquals(0, out[0].toInt())
        assertEquals(20, out[1].toInt()) // jeder zweite Wert
    }

    @Test fun hochrechnenInterpoliert() {
        val input = shortArrayOf(0, 100)
        val out = AudioConvert.resampleLinear(input, 2, 8000, 16000)
        assertEquals(4, out.size)
        assertEquals(0, out[0].toInt())
        assertEquals(50, out[1].toInt()) // Mitte zwischen 0 und 100
    }

    @Test fun resamplerLaeuftNichtUeberDasEndeHinaus() {
        val input = shortArrayOf(5, 6, 7)
        val out = AudioConvert.resampleLinear(input, 3, 8000, 48000)
        assertEquals(18, out.size)
        assertEquals(7, out.last().toInt())
    }

    @Test fun leereEingabeBleibtLeer() {
        assertEquals(0, AudioConvert.resampleLinear(ShortArray(0), 0, 8000, 16000).size)
    }

    @Test fun rmsVonStilleIstNull() {
        assertEquals(0f, AudioConvert.rms(ShortArray(10), 0, 10), 0.0001f)
    }

    @Test fun rmsVonVollausschlagIstEins() {
        val loud = ShortArray(10) { 32767 }
        assertEquals(1f, AudioConvert.rms(loud, 0, 10), 0.001f)
    }

    @Test fun rmsClamptAufDieArrayLaenge() {
        val s = ShortArray(4) { 32767 }
        assertEquals(1f, AudioConvert.rms(s, 0, 999), 0.001f)
        assertEquals(0f, AudioConvert.rms(s, 4, 4), 0.0001f)
    }
}
