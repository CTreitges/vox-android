package com.chris.vox.a11y

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Einfuege-Logik der Bedienungshilfe. Laeuft ohne
 * Android/Emulator (Gradle `test`-Task).
 */
class TextInsertionTest {

    @Test fun leeresFeldBekommtNurDenDiktattext() {
        // Regression: Bei leerem Feld liefert getText() den Hint ("Nachricht", "Google").
        // Der Service filtert ihn raus -> hier kommt "" an und darf NICHTS voranstellen.
        val r = TextInsertion.compute("", 0, 0, "Hallo Welt")
        assertEquals("Hallo Welt", r.text)
        assertEquals(10, r.cursor)
    }

    @Test fun leeresFeldMitUnbrauchbarerAuswahl() {
        // Felder ohne Cursor-Info melden -1/-1.
        val r = TextInsertion.compute("", -1, -1, "Hallo")
        assertEquals("Hallo", r.text)
        assertEquals(5, r.cursor)
    }

    @Test fun anhaengenSetztLeerzeichen() {
        val r = TextInsertion.compute("Guten", 5, 5, "Morgen")
        assertEquals("Guten Morgen", r.text)
        assertEquals(12, r.cursor)
    }

    @Test fun keinDoppeltesLeerzeichen() {
        val r = TextInsertion.compute("Guten ", 6, 6, "Morgen")
        assertEquals("Guten Morgen", r.text)
        assertEquals(12, r.cursor)
    }

    @Test fun einfuegenInDerMitte() {
        val r = TextInsertion.compute("Guten Tag", 5, 5, "en")
        assertEquals("Guten en Tag", r.text)
        assertEquals(8, r.cursor)
    }

    @Test fun auswahlWirdErsetzt() {
        val r = TextInsertion.compute("Guten Tag", 6, 9, "Abend")
        assertEquals("Guten Abend", r.text)
        assertEquals(11, r.cursor)
    }

    @Test fun rueckwaertsAuswahlWirdNormalisiert() {
        val r = TextInsertion.compute("Guten Tag", 9, 6, "Abend")
        assertEquals("Guten Abend", r.text)
        assertEquals(11, r.cursor)
    }

    @Test fun auswahlAusserhalbFaelltAufAnhaengenZurueck() {
        val r = TextInsertion.compute("Guten", 99, 99, "Tag")
        assertEquals("Guten Tag", r.text)
        assertEquals(9, r.cursor)
    }

    @Test fun cursorAmAnfangBekommtKeinLeerzeichen() {
        val r = TextInsertion.compute("Tag", 0, 0, "Guten")
        assertEquals("GutenTag", r.text)
        assertEquals(5, r.cursor)
    }
}
