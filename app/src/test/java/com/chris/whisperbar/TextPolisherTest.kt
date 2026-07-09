package com.chris.whisperbar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die reine Textveredelung. Laeuft ohne Android/Emulator
 * (Gradle `test`-Task), deckt Fuellwort-Entfernung, Gross-Schreibung,
 * Whitespace- und Satzzeichen-Normalisierung ab.
 */
class TextPolisherTest {

    private val full = PolishOptions(removeFillers = true, autoCapitalize = true, language = "auto")

    @Test fun leererInputBleibtLeer() {
        assertEquals("", TextPolisher.polish("", full))
        assertEquals("", TextPolisher.polish("   \n  ", full))
    }

    @Test fun whitespaceWirdNormalisiert() {
        assertEquals("Hallo Welt", TextPolisher.polish("  hallo   welt  ", full))
        assertEquals("Hallo Welt", TextPolisher.polish("hallo\n\twelt", full))
    }

    @Test fun ersterBuchstabeGross() {
        assertEquals("Das ist ein Test.", TextPolisher.polish("das ist ein Test.", full))
    }

    @Test fun grossNachSatzende() {
        assertEquals(
            "Hallo. Wie geht's? Gut!",
            TextPolisher.polish("hallo. wie geht's? gut!", full),
        )
    }

    @Test fun deutscheFuellwoerterEntfernt() {
        assertEquals(
            "Ich denke das ist gut.",
            TextPolisher.polish("ich ähm denke äh das ist gut.", PolishOptions(language = "de")),
        )
    }

    @Test fun fuellwortGrossgeschriebenAmSatzanfangEntfernt() {
        // "Ähm" am Anfang muss weg, danach wird korrekt gross geschrieben.
        assertEquals(
            "Also gut.",
            TextPolisher.polish("Ähm also gut.", PolishOptions(language = "de")),
        )
    }

    @Test fun englischeFuellwoerterEntfernt() {
        assertEquals(
            "I think this works.",
            TextPolisher.polish("i um think uh this works.", PolishOptions(language = "en")),
        )
    }

    @Test fun echteWoerterBleibenErhalten() {
        // "um" ist hier Teilstring von "umsonst" / eigenstaendiges dt. Wort -> nicht anfassen bei de.
        assertEquals(
            "Das war umsonst.",
            TextPolisher.polish("das war umsonst.", PolishOptions(language = "de")),
        )
    }

    @Test fun fuellwortInWortGrenzeNichtEntfernt() {
        // "erm" darf nicht aus "Determinante" gerissen werden.
        assertEquals(
            "Determinante",
            TextPolisher.polish("Determinante", PolishOptions(language = "en")),
        )
    }

    @Test fun leerzeichenVorSatzzeichenEntfernt() {
        assertEquals(
            "Hallo, Welt!",
            TextPolisher.polish("hallo , welt !", full),
        )
    }

    @Test fun fuellwortEntfernungAbschaltbar() {
        assertEquals(
            "Ich ähm denke.",
            TextPolisher.polish("ich ähm denke.", PolishOptions(removeFillers = false, language = "de")),
        )
    }

    @Test fun grossSchreibungAbschaltbar() {
        assertEquals(
            "das bleibt klein.",
            TextPolisher.polish("das bleibt klein.", PolishOptions(autoCapitalize = false)),
        )
    }
}
