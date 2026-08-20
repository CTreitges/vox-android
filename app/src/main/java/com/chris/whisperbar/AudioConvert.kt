package com.chris.whisperbar

import kotlin.math.sqrt

/**
 * Reine (Android-freie) PCM-Umrechnungen — JVM-unit-testbar.
 *
 * Geteilte Audios kommen in beliebiger Abtastrate und Kanalzahl aus dem Decoder;
 * die Erkennung will 16 kHz Mono.
 */
object AudioConvert {

    const val TARGET_RATE = AudioUtils.SAMPLE_RATE

    /**
     * Mischt verschachtelte Mehrkanal-Samples zu Mono. [valueCount] ist die Anzahl der
     * belegten Werte in [interleaved] (nicht der Frames).
     */
    fun downmixToMono(interleaved: ShortArray, valueCount: Int, channels: Int): ShortArray {
        require(channels >= 1) { "channels muss >= 1 sein" }
        if (channels == 1) return interleaved.copyOf(valueCount)
        val frames = valueCount / channels
        val out = ShortArray(frames)
        for (f in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) sum += interleaved[f * channels + c]
            out[f] = (sum / channels).toShort()
        }
        return out
    }

    /**
     * Lineare Interpolation auf eine andere Abtastrate. Fuer Sprache voellig ausreichend
     * und ohne Filter-Abhaengigkeit.
     */
    fun resampleLinear(input: ShortArray, count: Int, fromRate: Int, toRate: Int): ShortArray {
        require(fromRate > 0 && toRate > 0) { "Abtastraten muessen positiv sein" }
        if (count <= 0) return ShortArray(0)
        if (fromRate == toRate) return input.copyOf(count)
        // Long, damit lange Aufnahmen nicht ueberlaufen.
        val outCount = (count.toLong() * toRate / fromRate).toInt().coerceAtLeast(1)
        val out = ShortArray(outCount)
        val step = fromRate.toDouble() / toRate
        for (i in 0 until outCount) {
            val pos = i * step
            val idx = pos.toInt()
            if (idx >= count - 1) {
                out[i] = input[count - 1]
            } else {
                val frac = pos - idx
                val a = input[idx].toDouble()
                val b = input[idx + 1].toDouble()
                out[i] = (a + (b - a) * frac).toInt().toShort()
            }
        }
        return out
    }

    /** Effektivwert (0..1) eines Bereichs — Grundlage fuer das Finden leiser Stellen. */
    fun rms(samples: ShortArray, from: Int, to: Int): Float {
        val end = to.coerceAtMost(samples.size)
        if (from >= end) return 0f
        var sum = 0.0
        for (i in from until end) {
            val v = samples[i].toDouble() / 32768.0
            sum += v * v
        }
        return sqrt(sum / (end - from)).toFloat()
    }
}
