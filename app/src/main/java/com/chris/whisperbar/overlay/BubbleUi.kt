package com.chris.whisperbar.overlay

import com.chris.whisperbar.Formats

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

    /** Aufnahmedauer als m:ss, z. B. 7000 ms -> "0:07". */
    fun formatDuration(millis: Long): String = Formats.duration(millis)
}
