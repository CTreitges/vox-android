package com.chris.whisperbar.overlay

/** Position des schwebenden Knopfs in Bildschirm-Pixeln. */
data class BubblePos(val x: Int, val y: Int)

/**
 * Reine (Android-freie) Positions-Logik fuer den schwebenden Knopf — JVM-unit-testbar.
 */
object BubblePosition {

    /**
     * Haelt den Knopf vollstaendig auf dem Bildschirm. Ohne das kann er ueber den Rand
     * hinaus geschoben werden (das Overlay nutzt FLAG_LAYOUT_NO_LIMITS) und ist dann
     * nicht mehr erreichbar — auch nach Drehen des Geraets.
     *
     * Ist der Knopf breiter/hoeher als der Bildschirm, gewinnt die linke/obere Kante.
     */
    fun clamp(x: Int, y: Int, width: Int, height: Int, screenW: Int, screenH: Int): BubblePos {
        val maxX = (screenW - width).coerceAtLeast(0)
        val maxY = (screenH - height).coerceAtLeast(0)
        return BubblePos(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    /**
     * Ob der Knopf ueber dem Abbrechen-Ziel schwebt. Verglichen werden die
     * Mittelpunkte — der Treffer-Radius ist bewusst grosszuegig, weil der Finger
     * den Knopf beim Ziehen verdeckt.
     */
    fun isOverCancel(
        bubbleX: Int, bubbleY: Int, bubbleW: Int, bubbleH: Int,
        cancelX: Int, cancelY: Int, cancelW: Int, cancelH: Int,
        radius: Int,
    ): Boolean {
        val dx = (bubbleX + bubbleW / 2) - (cancelX + cancelW / 2)
        val dy = (bubbleY + bubbleH / 2) - (cancelY + cancelH / 2)
        return dx * dx + dy * dy <= radius * radius
    }
}
