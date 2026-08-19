package com.chris.whisperbar.overlay

/**
 * Zustaende des schwebenden Knopfs. Im API-Betrieb dauert die Uebertragung
 * spuerbar — ohne sichtbaren Unterschied zwischen "nimmt auf" und "sendet gerade"
 * tippt man mitten in die laufende Anfrage.
 */
enum class BubbleState {
    /** Bereit. Tippen startet die Aufnahme. */
    IDLE,

    /** Nimmt auf. Tippen beendet und sendet, Ziehen auf das ✕ verwirft. */
    RECORDING,

    /** Anfrage laeuft. Tippen tut nichts. */
    SENDING,

    /** Fehlgeschlagen, Audio ist gepuffert. Tippen versucht es erneut. */
    ERROR,
}

/** Reine (Android-freie) Anzeige-Helfer — JVM-unit-testbar. */
object BubbleUi {

    /** Aufnahmedauer als m:ss, z. B. 7000 ms -> "0:07". Negatives wird zu "0:00". */
    fun formatDuration(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
