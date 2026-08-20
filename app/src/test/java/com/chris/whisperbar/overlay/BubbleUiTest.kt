package com.chris.whisperbar.overlay

import com.chris.whisperbar.Formats
import org.junit.Assert.assertEquals
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
}
