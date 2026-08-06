package com.chris.whisperbar

import kotlin.math.abs

/**
 * Ein Stueck Audio als Sicht auf einen groesseren Puffer — Offset + Laenge statt
 * einer eigenen Kopie. Der Aufnahme-Puffer wird so einmal befuellt und danach
 * ohne weitere Kopie bis in den JNI-Aufruf durchgereicht.
 */
data class AudioSlice(val data: FloatArray, val offset: Int, val length: Int) {

    val isEmpty: Boolean get() = length <= 0

    /** Laenge in Sekunden (fuer Logging / Heuristiken). */
    fun seconds(sampleRate: Int = AudioUtils.SAMPLE_RATE): Float =
        if (sampleRate <= 0) 0f else length.toFloat() / sampleRate

    /** Nur fuer Pfade, die zwingend ein eigenes Array brauchen (z. B. WAV-Encoding). */
    fun toFloatArray(): FloatArray =
        if (offset == 0 && length == data.size) data
        else data.copyOfRange(offset, offset + length)

    // data class + FloatArray: equals/hashCode explizit, sonst Referenzvergleich im Array.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioSlice) return false
        return offset == other.offset && length == other.length && data.contentEquals(other.data)
    }

    override fun hashCode(): Int =
        (data.contentHashCode() * 31 + offset) * 31 + length

    companion object {
        val EMPTY = AudioSlice(FloatArray(0), 0, 0)

        fun of(samples: FloatArray) = AudioSlice(samples, 0, samples.size)
    }
}

/**
 * Reine (Android-freie) Audio-Hilfen — JVM-unit-testbar.
 */
object AudioUtils {

    const val SAMPLE_RATE = 16_000

    /**
     * Schneidet fuehrende/abschliessende Stille weg (kuerzeres Audio -> schnellere
     * Transkription) und laesst [padMs] ms Rand stehen, damit keine Silben abgeschnitten
     * werden. Arbeitet rein auf Indizes: es wird **nichts kopiert**.
     *
     * Nur-Stille bleibt unveraendert (der Aufrufer entscheidet, ob er verwirft).
     */
    fun trimSilence(
        slice: AudioSlice,
        threshold: Float = 0.008f,
        padMs: Int = 100,
    ): AudioSlice {
        if (slice.isEmpty) return slice
        val data = slice.data
        val lo = slice.offset
        val hi = slice.offset + slice.length - 1

        var start = lo
        var end = hi
        while (start < end && abs(data[start]) < threshold) start++
        while (end > start && abs(data[end]) < threshold) end--
        if (end <= start) return slice // nur Stille -> unveraendert

        val pad = SAMPLE_RATE * padMs / 1000
        start = (start - pad).coerceAtLeast(lo)
        end = (end + pad).coerceAtMost(hi)
        return AudioSlice(data, start, end - start + 1)
    }

    /** Bequemlichkeits-Ueberladung fuer Aufrufer, die ein ganzes Array haben. */
    fun trimSilence(samples: FloatArray, threshold: Float = 0.008f, padMs: Int = 100): FloatArray =
        trimSilence(AudioSlice.of(samples), threshold, padMs).toFloatArray()
}
