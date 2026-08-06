package com.chris.whisperbar

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

    // --- Index-Variante: trimmt ohne zu kopieren ---------------------------

    @Test fun sliceTrimmenKopiertNicht() {
        val a = FloatArray(16_000)
        for (i in 5000..6000) a[i] = 0.5f
        val trimmed = AudioUtils.trimSilence(AudioSlice.of(a))
        assertTrue("Es darf dasselbe Array bleiben", trimmed.data === a)
        assertEquals(3400, trimmed.offset)
        assertEquals(4201, trimmed.length)
    }

    @Test fun sliceTrimmenBleibtImmerInnerhalbDerSicht() {
        // Nur der mittlere Bereich ist "aufgenommen"; davor/danach liegt Fremd-Inhalt,
        // den die Trimmung nicht anfassen darf.
        val a = FloatArray(10_000) { 0.9f }
        for (i in 4000..6000) a[i] = 0f
        a[5000] = 0.5f
        val view = AudioSlice(a, 4000, 2001)
        val trimmed = AudioUtils.trimSilence(view)
        assertTrue("Start darf nicht vor die Sicht rutschen", trimmed.offset >= view.offset)
        assertTrue(
            "Ende darf nicht hinter die Sicht rutschen",
            trimmed.offset + trimmed.length <= view.offset + view.length,
        )
    }

    @Test fun leereSliceBleibtLeer() {
        assertEquals(0, AudioUtils.trimSilence(AudioSlice.EMPTY).length)
    }
}
