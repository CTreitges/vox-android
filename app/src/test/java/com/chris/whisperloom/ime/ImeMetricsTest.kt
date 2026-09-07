package com.chris.whisperloom.ime

import com.chris.whisperloom.overlay.BubbleState
import com.chris.whisperloom.overlay.BubbleVisual
import com.chris.whisperloom.overlay.BubbleVisuals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tastenhoehe und Level-Zuordnung der Mikro-Taste (UX-Spec §5.3). */
class ImeMetricsTest {

    @Test fun tastenWachsenAbFontScale13() {
        assertEquals(48, ImeMetrics.keyHeightDp(1.0f))
        assertEquals(48, ImeMetrics.keyHeightDp(1.15f))
        assertEquals(48, ImeMetrics.keyHeightDp(1.29f))
        assertEquals(56, ImeMetrics.keyHeightDp(1.3f))
        assertEquals(56, ImeMetrics.keyHeightDp(2.0f))
    }

    @Test fun levelDerMikroTasteFolgtDerFuellung() {
        // Level-List in mic_button_bg.xml: 0 SURFACE, 1 RECORDING, 2 PRIMARY_CONTAINER, 3 ERROR_CONTAINER.
        assertEquals(0, ImeMetrics.micFillLevel(BubbleVisuals.visualFor(BubbleState.IDLE)))
        assertEquals(1, ImeMetrics.micFillLevel(BubbleVisuals.visualFor(BubbleState.RECORDING)))
        assertEquals(2, ImeMetrics.micFillLevel(BubbleVisuals.visualFor(BubbleState.SENDING)))
        assertEquals(3, ImeMetrics.micFillLevel(BubbleVisuals.visualFor(BubbleState.ERROR)))
        assertEquals(4, BubbleVisual.Fill.values().size)
    }
}
