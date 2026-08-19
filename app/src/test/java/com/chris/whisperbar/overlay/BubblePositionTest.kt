package com.chris.whisperbar.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Positions-Logik des schwebenden Knopfs.
 */
class BubblePositionTest {

    private val w = 168 // ~64dp Bubble bei 2.625x
    private val h = 168
    private val screenW = 1080
    private val screenH = 2400

    @Test fun positionInnerhalbBleibtUnveraendert() {
        assertEquals(BubblePos(300, 900), BubblePosition.clamp(300, 900, w, h, screenW, screenH))
    }

    @Test fun negativeWerteWerdenAufNullGezogen() {
        assertEquals(BubblePos(0, 0), BubblePosition.clamp(-50, -200, w, h, screenW, screenH))
    }

    @Test fun rechterUndUntererRandBleibenErreichbar() {
        // Regression: ohne Clamping laesst FLAG_LAYOUT_NO_LIMITS die Bubble aus dem
        // Bildschirm rutschen — sie ist dann nicht mehr antippbar.
        assertEquals(
            BubblePos(screenW - w, screenH - h),
            BubblePosition.clamp(9999, 9999, w, h, screenW, screenH),
        )
    }

    @Test fun nachDrehungWirdDieAlteQuerPositionKorrigiert() {
        // Gemerkt im Querformat (x=2100), gestartet im Hochformat.
        assertEquals(BubblePos(screenW - w, 500), BubblePosition.clamp(2100, 500, w, h, screenW, screenH))
    }

    @Test fun unbekannteGroesseClamptAufBildschirmkante() {
        // Beim Wiederherstellen steht die Bubble-Groesse noch nicht fest (0/0).
        assertEquals(BubblePos(screenW, 320), BubblePosition.clamp(5000, 320, 0, 0, screenW, screenH))
    }

    @Test fun bubbleGroesserAlsBildschirmLandetLinksOben() {
        assertEquals(BubblePos(0, 0), BubblePosition.clamp(50, 50, 2000, 3000, screenW, screenH))
    }
}

/** JVM-Unit-Tests fuer die Trefferflaeche des Abbrechen-Ziels. */
class CancelTargetTest {

    private val bw = 168
    private val bh = 200
    private val cx = 456
    private val cy = 2100
    private val cw = 168
    private val ch = 168
    private val radius = 189

    private fun over(bubbleX: Int, bubbleY: Int) =
        BubblePosition.isOverCancel(bubbleX, bubbleY, bw, bh, cx, cy, cw, ch, radius)

    @Test fun deckungsgleichIstEinTreffer() {
        assertTrue(over(cx, cy + (ch - bh) / 2))
    }

    @Test fun knappDanebenIstNochEinTreffer() {
        // Der Finger verdeckt den Knopf — die Flaeche ist bewusst grosszuegig.
        assertTrue(over(cx + 100, cy - 100))
    }

    @Test fun weitEntferntIstKeinTreffer() {
        assertFalse(over(50, 300))
    }

    @Test fun genauAmRandDesRadius() {
        // Mittelpunkte exakt radius auseinander -> zaehlt noch als Treffer.
        val bubbleY = cy + (ch - bh) / 2
        assertTrue(over(cx + radius, bubbleY))
        assertFalse(over(cx + radius + 1, bubbleY))
    }
}
