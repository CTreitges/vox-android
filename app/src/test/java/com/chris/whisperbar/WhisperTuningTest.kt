package com.chris.whisperbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Tempo-Heuristiken. Die audio_ctx-Rechnung bestimmt direkt,
 * wie viel Encoder-Arbeit whisper.cpp leistet — sie darf nie unter die Qualitaetsgrenze
 * fallen und nie ueber den Modell-Kontext hinauslaufen.
 */
class WhisperTuningTest {

    private val sr = AudioUtils.SAMPLE_RATE

    @Test fun kurzeAufnahmeLandetAufDerUntergrenze() {
        // 2 s -> 100 Positionen + Rand liegt unter der Untergrenze.
        assertEquals(WhisperTuning.MIN_AUDIO_CTX, WhisperTuning.audioCtxFor(sr * 2))
    }

    @Test fun mittlereAufnahmeSkaliertMit() {
        // 10 s -> 500 + 128 = 628, aufgerundet auf ein Vielfaches von 64 -> 640.
        assertEquals(640, WhisperTuning.audioCtxFor(sr * 10))
    }

    @Test fun langeAufnahmeNutztDenVollenKontext() {
        assertEquals(WhisperTuning.FULL_AUDIO_CTX, WhisperTuning.audioCtxFor(sr * 30))
        assertEquals(WhisperTuning.FULL_AUDIO_CTX, WhisperTuning.audioCtxFor(sr * 120))
    }

    @Test fun ergebnisBleibtImmerInDenGrenzen() {
        for (seconds in 0..60) {
            val ctx = WhisperTuning.audioCtxFor(sr * seconds)
            assertTrue(
                "audio_ctx=$ctx bei ${seconds}s ausserhalb der Grenzen",
                ctx in WhisperTuning.MIN_AUDIO_CTX..WhisperTuning.FULL_AUDIO_CTX,
            )
        }
    }

    @Test fun ergebnisWaechstMonotonMitDerLaenge() {
        var last = 0
        for (seconds in 0..40) {
            val ctx = WhisperTuning.audioCtxFor(sr * seconds)
            assertTrue("audio_ctx darf nicht schrumpfen", ctx >= last)
            last = ctx
        }
    }

    @Test fun ohneTurboImmerVollerKontext() {
        assertEquals(WhisperTuning.FULL_AUDIO_CTX, WhisperTuning.audioCtxFor(sr * 2, fast = false))
    }

    @Test fun ungueltigeEingabenLiefernDieUntergrenze() {
        assertEquals(WhisperTuning.MIN_AUDIO_CTX, WhisperTuning.audioCtxFor(0))
        assertEquals(WhisperTuning.MIN_AUDIO_CTX, WhisperTuning.audioCtxFor(-1))
        assertEquals(WhisperTuning.MIN_AUDIO_CTX, WhisperTuning.audioCtxFor(sr, sampleRate = 0))
    }

    @Test fun threadsFolgenDenSchnellenKernen() {
        // 8 Kerne, davon 4 schnell -> 4 Threads (Little-Cores wuerden nur bremsen).
        assertEquals(4, WhisperTuning.threadsFor(totalCores = 8, perfCores = 4))
        assertEquals(2, WhisperTuning.threadsFor(totalCores = 2, perfCores = 2))
    }

    @Test fun ohneCpuInfoWirdGeschaetztUndGedeckelt() {
        // Ohne sysfs-Angabe: alle Kerne bis auf einen, aber hoechstens MAX_THREADS.
        assertEquals(WhisperTuning.MAX_THREADS, WhisperTuning.threadsFor(totalCores = 8, perfCores = 0))
        assertEquals(3, WhisperTuning.threadsFor(totalCores = 4, perfCores = 0))
        assertEquals(1, WhisperTuning.threadsFor(totalCores = 1, perfCores = 0))
    }

    @Test fun threadsSindImmerSinnvollBegrenzt() {
        for (total in 1..16) {
            for (perf in 0..total) {
                val t = WhisperTuning.threadsFor(total, perf)
                assertTrue("threads=$t bei total=$total perf=$perf", t in 1..WhisperTuning.MAX_THREADS)
            }
        }
    }
}
