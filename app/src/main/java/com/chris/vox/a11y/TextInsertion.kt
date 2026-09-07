package com.chris.vox.a11y

/** Ergebnis einer Einfuege-Berechnung: neuer Feldinhalt + neue Cursor-Position. */
data class Insertion(val text: String, val cursor: Int)

/**
 * Reine (Android-freie) Einfuege-Logik fuer [TextInserterAccessibilityService] —
 * JVM-unit-testbar, damit das Zusammensetzen von Feldinhalt + Diktat ohne Emulator
 * geprueft werden kann.
 */
object TextInsertion {

    /**
     * Setzt [insert] an der Auswahl [selStart]..[selEnd] in [old] ein (ersetzt eine
     * vorhandene Auswahl) und liefert den neuen Feldinhalt samt Cursor-Position.
     *
     * Unbrauchbare Auswahl-Indizes (z. B. -1, wenn das Feld keine Cursor-Info liefert)
     * werden als "ans Ende anhaengen" behandelt.
     */
    fun compute(old: String, selStart: Int, selEnd: Int, insert: String): Insertion {
        var start = selStart
        var end = selEnd
        if (start !in 0..old.length || end !in 0..old.length) {
            start = old.length
            end = old.length
        }
        val lo = minOf(start, end)
        val hi = maxOf(start, end)

        // Fuehrendes Leerzeichen, wenn direkt an ein Wort angefuegt wird.
        val needsSpace = lo > 0 && !old[lo - 1].isWhitespace() &&
            insert.isNotEmpty() && !insert[0].isWhitespace()
        val ins = if (needsSpace) " $insert" else insert

        return Insertion(old.substring(0, lo) + ins + old.substring(hi), lo + ins.length)
    }
}
