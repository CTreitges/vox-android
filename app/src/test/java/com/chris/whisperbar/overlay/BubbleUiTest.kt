package com.chris.whisperbar.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM-Unit-Tests fuer die Anzeige-Helfer des schwebenden Knopfs. */
class BubbleUiTest {

    @Test fun sekundenWerdenZweistelligAufgefuellt() {
        assertEquals("0:07", BubbleUi.formatDuration(7_000))
    }

    @Test fun nullIstNullNullNull() {
        assertEquals("0:00", BubbleUi.formatDuration(0))
    }

    @Test fun angefangeneSekundeZaehltNochNicht() {
        assertEquals("0:00", BubbleUi.formatDuration(999))
    }

    @Test fun minutenUeberlauf() {
        assertEquals("1:00", BubbleUi.formatDuration(60_000))
        assertEquals("1:23", BubbleUi.formatDuration(83_400))
        assertEquals("12:05", BubbleUi.formatDuration(725_000))
    }

    @Test fun negativeDauerFaelltAufNullZurueck() {
        // Kann beim Uhr-Rueckstellen passieren; darf nicht "-1:-5" anzeigen.
        assertEquals("0:00", BubbleUi.formatDuration(-5_000))
    }
}
