package com.chris.vox.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/**
 * JVM-Unit-Tests fuer die Positions-Logik des schwebenden Knopfs.
 */
class BubblePositionTest {

    private val w = 252 // 96-dp-Container bei 2.625x
    private val h = 252
    private val screenW = 1080
    private val screenH = 2400

    @Test fun masseEntsprechenDerUxSpec() {
        // §5.1/§5.2: Knopf 68, Container 96, Abbrechen-Ziel 72, Rand 96, Magnet-Radius 56.
        assertEquals(68, BubblePosition.BUBBLE_SIZE_DP)
        assertEquals(96, BubblePosition.BUBBLE_FRAME_DP)
        assertEquals(72, BubblePosition.CANCEL_SIZE_DP)
        assertEquals(96, BubblePosition.CANCEL_MARGIN_DP)
        assertEquals(56, BubblePosition.CANCEL_HIT_RADIUS_DP)
    }

    @Test fun containerHatPlatzFuerDenPuls() {
        // Puls-Ring skaliert bis 1,35 x 68 = 91,8 dp — muss in den Container passen.
        assertTrue(BubblePosition.BUBBLE_SIZE_DP * 1.35f <= BubblePosition.BUBBLE_FRAME_DP)
    }

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

/** JVM-Unit-Tests fuer die Trefferflaeche des Abbrechen-Ziels (Magnet-Radius 56 dp). */
class CancelTargetTest {

    private val density = 2.625f

    // Der Service vergleicht den 96-dp-Container des Knopfs mit dem 72-dp-Kreis des Ziels.
    private val bw = (BubblePosition.BUBBLE_FRAME_DP * density).roundToInt() // 252
    private val bh = bw
    private val cw = (BubblePosition.CANCEL_SIZE_DP * density).roundToInt() // 189
    private val ch = cw
    private val cx = (1080 - cw) / 2
    private val cy = 2400 - (BubblePosition.CANCEL_MARGIN_DP * density).roundToInt() - ch
    private val radius = (BubblePosition.CANCEL_HIT_RADIUS_DP * density).toInt() // 147

    /** Knopf-Position, bei der beide Mittelpunkte exakt uebereinanderliegen (Ganzzahl-Halbierung wie im Code). */
    private val centeredX = cx + cw / 2 - bw / 2
    private val centeredY = cy + ch / 2 - bh / 2

    private fun over(bubbleX: Int, bubbleY: Int) =
        BubblePosition.isOverCancel(bubbleX, bubbleY, bw, bh, cx, cy, cw, ch, radius)

    @Test fun deckungsgleichIstEinTreffer() {
        assertTrue(over(centeredX, centeredY))
    }

    @Test fun knappDanebenIstNochEinTreffer() {
        // Der Finger verdeckt den Knopf — 100 px schraeg (= 141 px Abstand) liegen im Radius.
        assertTrue(over(centeredX + 100, centeredY - 100))
    }

    @Test fun weitEntferntIstKeinTreffer() {
        assertFalse(over(50, 300))
    }

    @Test fun genauAmRandDesRadius() {
        // Mittelpunkte exakt radius auseinander -> zaehlt noch als Treffer.
        assertTrue(over(centeredX + radius, centeredY))
        assertFalse(over(centeredX + radius + 1, centeredY))
    }

    @Test fun radiusIstKleinerAlsVorher() {
        // Regression: 72 dp Magnet-Radius liess den Knopf schon "haften", wenn er nur
        // ueber dem Label des Ziels stand; 56 dp = Kreis-Radius 36 + 20 dp Toleranz.
        assertFalse(over(centeredX + (72 * density).toInt(), centeredY))
    }
}
