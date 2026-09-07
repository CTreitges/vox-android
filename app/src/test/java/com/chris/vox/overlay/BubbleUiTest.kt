package com.chris.vox.overlay

import com.chris.vox.Formats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Aufnahme-Timer nutzt dieselbe Formatierung wie die Ergebnis-Ueberschrift geteilter
 * Audios; die Faelle selbst deckt FormatsTest ab.
 */
class BubbleUiTest {

    @Test fun timerNutztDieGemeinsameFormatierung() {
        for (ms in listOf(0L, 999L, 7_000L, 60_000L, 725_000L, -5_000L)) {
            assertEquals(Formats.duration(ms), BubbleUi.formatDuration(ms))
        }
    }

    @Test fun timerTextHatPunktUndDauer() {
        // §5.1: Label "● 0:07"
        assertEquals("● 0:07", BubbleUi.timerText(7_000))
        assertEquals("● 0:00", BubbleUi.timerText(0))
        assertEquals("● 12:05", BubbleUi.timerText(725_000))
        assertEquals("● 0:00", BubbleUi.timerText(-5_000))
    }

    @Test fun punktBlinktMitEinemHertz() {
        // An in der ersten halben Sekunde, aus in der zweiten — je Sekunde ein Blink.
        assertTrue(BubbleUi.dotVisible(0))
        assertTrue(BubbleUi.dotVisible(499))
        assertFalse(BubbleUi.dotVisible(500))
        assertFalse(BubbleUi.dotVisible(999))
        assertTrue(BubbleUi.dotVisible(1_000))
        assertFalse(BubbleUi.dotVisible(7_700))
    }

    @Test fun negativeZeitZeigtDenPunkt() {
        assertTrue(BubbleUi.dotVisible(-1))
    }
}
