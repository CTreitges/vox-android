package com.chris.whisperbar

import org.junit.Assert.assertEquals
import org.junit.Test

/** JVM-Unit-Tests fuer die Dauer-Formatierung (Aufnahme-Timer + Ergebnis-Ueberschrift). */
class FormatsTest {

    @Test fun sekundenWerdenZweistelligAufgefuellt() {
        assertEquals("0:07", Formats.duration(7_000))
    }

    @Test fun nullIstNullNullNull() {
        assertEquals("0:00", Formats.duration(0))
    }

    @Test fun angefangeneSekundeZaehltNochNicht() {
        assertEquals("0:00", Formats.duration(999))
    }

    @Test fun minutenUeberlauf() {
        assertEquals("1:00", Formats.duration(60_000))
        assertEquals("1:23", Formats.duration(83_400))
        assertEquals("12:05", Formats.duration(725_000))
    }

    @Test fun negativeDauerFaelltAufNullZurueck() {
        assertEquals("0:00", Formats.duration(-5_000))
    }
}
