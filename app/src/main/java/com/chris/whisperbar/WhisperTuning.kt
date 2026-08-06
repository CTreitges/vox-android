package com.chris.whisperbar

import java.io.File

/**
 * Reine (Android-freie) Heuristiken, die bestimmen, WIE schnell whisper.cpp laeuft.
 * Bewusst ohne Framework-Abhaengigkeiten, damit sie auf der JVM unit-testbar sind.
 *
 * Zwei Stellschrauben mit dem groessten Hebel:
 *
 *  1. [audioCtxFor] — Whisper padded jedes Audio intern auf 30 s und laesst den Encoder
 *     ueber alle 1500 Positionen laufen, egal ob man 2 s oder 25 s gesprochen hat.
 *     `audio_ctx` deckelt diese Positionen. Bei typischen Diktat-Laengen (2–6 s)
 *     spart das den groessten Teil der Encoder-Arbeit.
 *  2. [threadsFor] — mehr Threads sind auf big.LITTLE-Telefonen NICHT schneller:
 *     whisper synchronisiert pro Layer, also bestimmt der langsamste Little-Core das
 *     Tempo. Nur die schnellen Kerne zaehlen.
 */
object WhisperTuning {

    /** Encoder-Positionen fuer volle 30 s (Whisper-Modellkonstante). */
    const val FULL_AUDIO_CTX = 1500

    /** Darunter leidet die Qualitaet spuerbar — nie weiter kuerzen. */
    const val MIN_AUDIO_CTX = 512

    /** Encoder-Positionen pro Sekunde Audio (1500 / 30 s). */
    private const val CTX_PER_SECOND = 50

    /** Sicherheitsrand, damit das Ende eines Satzes nicht abgeschnitten wird. */
    private const val CTX_MARGIN = 128

    /** Maximale Threadzahl — darueber bringt whisper.cpp auf Telefonen nichts mehr. */
    const val MAX_THREADS = 6

    /**
     * Passender `audio_ctx`-Wert fuer [sampleCount] Samples bei [sampleRate].
     *
     * Ergebnis ist auf ein Vielfaches von 64 aufgerundet (freundlich fuer die
     * SIMD-Kernel) und liegt immer in `[MIN_AUDIO_CTX, FULL_AUDIO_CTX]`.
     * Ist [fast] false, wird immer der volle Kontext genutzt (maximale Qualitaet).
     */
    fun audioCtxFor(
        sampleCount: Int,
        sampleRate: Int = AudioUtils.SAMPLE_RATE,
        fast: Boolean = true,
    ): Int {
        if (!fast) return FULL_AUDIO_CTX
        if (sampleCount <= 0 || sampleRate <= 0) return MIN_AUDIO_CTX
        // Aufrunden: lieber eine Position zu viel als eine Silbe zu wenig.
        val seconds = (sampleCount + sampleRate - 1) / sampleRate
        val needed = seconds * CTX_PER_SECOND + CTX_MARGIN
        val rounded = ((needed + 63) / 64) * 64
        return rounded.coerceIn(MIN_AUDIO_CTX, FULL_AUDIO_CTX)
    }

    /**
     * Threadzahl fuer die Inferenz. [perfCores] sind die Kerne des schnellsten Clusters
     * (siehe [CpuInfo.performanceCores]); ist der Wert unbrauchbar, faellt die Heuristik
     * auf [totalCores] zurueck.
     */
    fun threadsFor(totalCores: Int, perfCores: Int): Int {
        val total = totalCores.coerceAtLeast(1)
        val base = if (perfCores in 1..total) perfCores else (total - 1)
        return base.coerceIn(1, MAX_THREADS)
    }
}

/**
 * Liest die CPU-Topologie einmalig aus sysfs. Auf big.LITTLE-Telefonen haben die
 * schnellen Kerne eine deutlich hoehere `cpuinfo_max_freq` — wir zaehlen alle Kerne
 * innerhalb von 15 % der Hoechstfrequenz als "schnell".
 */
object CpuInfo {

    /** Kerne, die mindestens diesen Anteil der Hoechstfrequenz erreichen. */
    private const val FAST_RATIO = 0.85

    val totalCores: Int by lazy { Runtime.getRuntime().availableProcessors().coerceAtLeast(1) }

    /** Anzahl schneller Kerne, oder 0 wenn sysfs nichts hergibt. */
    val performanceCores: Int by lazy { readPerformanceCores() }

    /** Threadzahl fuer whisper.cpp — einmal berechnet, danach kostenlos. */
    val inferenceThreads: Int by lazy { WhisperTuning.threadsFor(totalCores, performanceCores) }

    private fun readPerformanceCores(): Int {
        val freqs = ArrayList<Long>(totalCores)
        for (cpu in 0 until totalCores) {
            val f = runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq")
                    .readText().trim().toLong()
            }.getOrNull() ?: continue
            if (f > 0) freqs.add(f)
        }
        if (freqs.isEmpty()) return 0
        val max = freqs.max()
        return freqs.count { it >= max * FAST_RATIO }
    }
}
