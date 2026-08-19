package com.chris.whisperbar.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Anweisung an die Textveredelung. Sie entscheidet ueber die
 * Textqualitaet — vor allem darf sie nie zum Uebersetzen oder Ergaenzen auffordern.
 */
class RefinePromptTest {

    @Test fun deutschUndEnglischUnterscheidenSich() {
        assertTrue(RefinePrompt.build(german = true, smartFillers = false).contains("Zeichensetzung"))
        assertTrue(RefinePrompt.build(german = false, smartFillers = false).contains("punctuation"))
    }

    @Test fun ohneSmartFillersKeineFuellwortAnweisung() {
        val p = RefinePrompt.build(german = true, smartFillers = false)
        assertFalse(p.contains("Fuellwoerter"))
    }

    @Test fun mitSmartFillersEntscheidetDieKiSelbst() {
        val p = RefinePrompt.build(german = true, smartFillers = true)
        assertTrue(p.contains("Fuellwoerter"))
        // Konservativ bleiben ist Teil der Anweisung — sonst verschwinden echte Woerter.
        assertTrue(p.contains("Im Zweifel"))
    }

    @Test fun englischeVarianteMitSmartFillers() {
        val p = RefinePrompt.build(german = false, smartFillers = true)
        assertTrue(p.contains("filler words"))
        assertTrue(p.contains("When in doubt"))
    }

    @Test fun niemalsUebersetzenOderErgaenzen() {
        for (german in listOf(true, false)) {
            for (smart in listOf(true, false)) {
                val p = RefinePrompt.build(german, smart)
                if (german) {
                    assertTrue(p.contains("uebersetze nicht"))
                    assertTrue(p.contains("ergaenze nichts"))
                } else {
                    assertTrue(p.contains("do not translate"))
                    assertTrue(p.contains("do not add"))
                }
            }
        }
    }
}
