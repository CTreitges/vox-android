package com.chris.whisperloom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Unit-Tests fuer die Stille-Trimmung (rein, ohne Android). */
class AudioUtilsTest {

    @Test fun leererInputBleibtLeer() {
        assertEquals(0, AudioUtils.trimSilence(FloatArray(0)).size)
    }

    @Test fun nurStilleBleibtUnveraendert() {
        val silence = FloatArray(1000) { 0f }
        assertEquals(1000, AudioUtils.trimSilence(silence).size)
    }

    @Test fun stilleVorneUndHintenWirdMitRandGetrimmt() {
        // Lautes Signal in der Mitte (Index 5000..6000), sonst Stille. 100ms Rand = 1600 Samples.
        val a = FloatArray(16_000)
        for (i in 5000..6000) a[i] = 0.5f
        val trimmed = AudioUtils.trimSilence(a)
        // start = 5000-1600 = 3400, end = 6000+1600 = 7600 -> 4201 Samples
        assertEquals(4201, trimmed.size)
    }

    @Test fun durchgehendesSignalBleibtVollstaendig() {
        val a = FloatArray(8000) { 0.4f }
        assertEquals(8000, AudioUtils.trimSilence(a).size)
    }

    @Test fun getrimmtIstNieLaengerAlsInput() {
        val a = FloatArray(4000).also { it[2000] = 0.9f }
        assertTrue(AudioUtils.trimSilence(a).size <= a.size)
    }
}
