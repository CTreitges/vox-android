package com.chris.whisperbar

/** Reine (Android-freie) Formatierungs-Helfer — JVM-unit-testbar. */
object Formats {

    /** Dauer als m:ss, z. B. 7000 ms -> "0:07". Negatives wird zu "0:00". */
    fun duration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
