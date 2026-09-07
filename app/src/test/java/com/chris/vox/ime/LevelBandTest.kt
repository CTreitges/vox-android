package com.chris.vox.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/** Pegelband-Rechnung (UX-Spec §5.3): 21 Balken, Attack 50 ms, Release 250 ms — reine Logik. */
class LevelBandTest {

    private val zeros = FloatArray(LevelBand.BARS)
    private val ones = FloatArray(LevelBand.BARS) { 1f }
    private val center = (LevelBand.BARS - 1) / 2

    @Test fun einundzwanzigBalken() {
        assertEquals(21, LevelBand.BARS)
        assertEquals(21, LevelBand.heights(0.5f, zeros, 16).size)
    }

    @Test fun stilleBleibtFlach() {
        val h = LevelBand.heights(0f, zeros, 16)
        assertTrue(h.all { it == 0f })
    }

    @Test fun profilIstSymmetrischMitMaximumInDerMitte() {
        assertEquals(1f, LevelBand.profile(center), 1e-6f)
        for (i in 0 until LevelBand.BARS) {
            assertEquals(LevelBand.profile(i), LevelBand.profile(LevelBand.BARS - 1 - i), 1e-6f)
            assertTrue(LevelBand.profile(i) <= 1f)
            assertTrue(LevelBand.profile(i) >= 0.3f)
        }
        assertTrue(LevelBand.profile(0) < LevelBand.profile(5))
        assertTrue(LevelBand.profile(5) < LevelBand.profile(center))
    }

    @Test fun attackNach50MsErreicht63Prozent() {
        // Huellkurve erster Ordnung: nach einer Zeitkonstante 1 - e^-1.
        val h = LevelBand.heights(1f, zeros, 50)
        assertEquals(1f - exp(-1f), h[center], 1e-4f)
    }

    @Test fun releaseIstLangsamerAlsAttack() {
        val up = LevelBand.heights(1f, zeros, 50)[center]     // 0 -> 1 nach 50 ms
        val down = 1f - LevelBand.heights(0f, ones, 50)[center] // 1 -> 0 nach 50 ms
        assertTrue("Anstieg $up sollte groesser sein als Abfall $down", up > down)
        assertEquals(exp(-50f / 250f), LevelBand.heights(0f, ones, 50)[center], 1e-4f)
    }

    @Test fun langeZeitErreichtDasZiel() {
        val h = LevelBand.heights(0.8f, zeros, 10_000)
        for (i in 0 until LevelBand.BARS) assertEquals(0.8f * LevelBand.profile(i), h[i], 1e-3f)
    }

    @Test fun keineZeitKeineAenderung() {
        val prev = FloatArray(LevelBand.BARS) { 0.4f }
        val h = LevelBand.heights(1f, prev, 0)
        for (i in 0 until LevelBand.BARS) assertEquals(0.4f, h[i], 1e-6f)
    }

    @Test fun werteBleibenZwischenNullUndEins() {
        for (level in listOf(-1f, 0f, 0.5f, 1f, 3f)) {
            val h = LevelBand.heights(level, ones, 1_000)
            assertTrue(h.all { it in 0f..1f })
        }
    }

    @Test fun kuerzeresVorherigesArrayIstErlaubt() {
        // Robust gegen ein falsch dimensioniertes previous (z. B. leer nach Reset).
        val h = LevelBand.heights(1f, FloatArray(0), 1_000)
        assertEquals(LevelBand.BARS, h.size)
        assertEquals(1f, h[center], 1e-3f)
    }

    @Test fun amplitudeWirdWurzelfoermigAngehoben() {
        // Leise Sprache (Peak 0,25) soll die halbe Hoehe zeigen, nicht ein Viertel.
        assertEquals(0f, LevelBand.fromAmplitude(0f), 1e-6f)
        assertEquals(0.5f, LevelBand.fromAmplitude(0.25f), 1e-6f)
        assertEquals(1f, LevelBand.fromAmplitude(1f), 1e-6f)
        assertEquals(1f, LevelBand.fromAmplitude(4f), 1e-6f)
        assertEquals(0f, LevelBand.fromAmplitude(-1f), 1e-6f)
    }
}
