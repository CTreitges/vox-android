package com.chris.whisperloom.ime

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Reine (Android-freie) Rechnung fuer das Pegelband der IME (UX-Spec §5.3): 21 Balken,
 * Hoehen 0..1 (die View bildet das auf 4–24 dp ab), Huellkurve mit Attack 50 ms und
 * Release 250 ms. Die mittleren Balken sind am hoechsten, die aeusseren fallen weich ab.
 */
object LevelBand {

    const val BARS = 21
    const val ATTACK_MS = 50f
    const val RELEASE_MS = 250f

    /** Anteil, den die aeussersten Balken vom Pegel zeigen. */
    private const val EDGE_WEIGHT = 0.3f

    /** Gewicht je Balken: 1,0 in der Mitte, EDGE_WEIGHT am Rand (Kosinus-Fenster). */
    fun profile(index: Int): Float {
        val center = (BARS - 1) / 2f
        val t = (index - center) / center // -1..1
        return EDGE_WEIGHT + (1f - EDGE_WEIGHT) * ((1f + cos(Math.PI * t).toFloat()) / 2f)
    }

    /**
     * Neue Balkenhoehen aus dem aktuellen Pegel [level] (0..1), den vorigen Hoehen und der
     * vergangenen Zeit. Steigen geht schnell (Attack), Fallen langsam (Release) — sonst
     * flackert die Anzeige im Takt der Audio-Puffer.
     */
    fun heights(level: Float, previous: FloatArray, dtMs: Long): FloatArray {
        val l = level.coerceIn(0f, 1f)
        val out = FloatArray(BARS)
        for (i in 0 until BARS) {
            val target = l * profile(i)
            val prev = previous.getOrElse(i) { 0f }
            val tau = if (target > prev) ATTACK_MS else RELEASE_MS
            val coef = 1f - exp(-dtMs.coerceAtLeast(0) / tau)
            out[i] = (prev + (target - prev) * coef).coerceIn(0f, 1f)
        }
        return out
    }

    /** Spitzenwert des Recorders (0..1) auf einen sichtbaren Pegel abbilden: leise Sprache waere sonst unsichtbar. */
    fun fromAmplitude(peak: Float): Float = sqrt(peak.coerceIn(0f, 1f))
}
