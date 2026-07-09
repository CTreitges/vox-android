package com.chris.whisperbar

import kotlin.math.abs

/**
 * Reine (Android-freie) Audio-Hilfen — JVM-unit-testbar.
 */
object AudioUtils {

    const val SAMPLE_RATE = 16_000

    /**
     * Schneidet fuehrende/abschliessende Stille weg (kuerzeres Audio -> schnellere
     * Transkription), laesst [padMs] ms Rand stehen, damit keine Silben abgeschnitten werden.
     * Nur-Stille bleibt unveraendert.
     */
    fun trimSilence(samples: FloatArray, threshold: Float = 0.008f, padMs: Int = 100): FloatArray {
        if (samples.isEmpty()) return samples
        var start = 0
        var end = samples.size - 1
        while (start < end && abs(samples[start]) < threshold) start++
        while (end > start && abs(samples[end]) < threshold) end--
        if (end <= start) return samples // nur Stille -> unveraendert
        val pad = SAMPLE_RATE * padMs / 1000
        start = (start - pad).coerceAtLeast(0)
        end = (end + pad).coerceAtMost(samples.size - 1)
        return samples.copyOfRange(start, end + 1)
    }
}
