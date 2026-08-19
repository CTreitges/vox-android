package com.chris.whisperbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Entscheidung, welche Nachbearbeitungs-Stufe greift.
 */
class PolishPlanTest {

    private fun plan(
        removeFillers: Boolean = true,
        llmPolish: Boolean = false,
        smartFillers: Boolean = false,
    ) = PolishPlan.options(
        removeFillers = removeFillers,
        autoCapitalize = true,
        language = "de",
        llmPolish = llmPolish,
        smartFillers = smartFillers,
    )

    @Test fun ohneKiGreiftDieWortliste() {
        assertTrue(plan(removeFillers = true).removeFillers)
    }

    @Test fun ausGeschaltetBleibtAus() {
        assertFalse(plan(removeFillers = false).removeFillers)
    }

    @Test fun kiEntscheidungSchaltetDieWortlisteAb() {
        // Sonst wuerde zweimal gefiltert und das "im Zweifel behalten" der KI
        // waere wieder ausgehebelt.
        assertFalse(plan(removeFillers = true, llmPolish = true, smartFillers = true).removeFillers)
    }

    @Test fun glaettenAlleinLaesstDieWortlisteAktiv() {
        assertTrue(plan(removeFillers = true, llmPolish = true, smartFillers = false).removeFillers)
    }

    @Test fun intelligenteFilterOhneGlaettenBleibtWirkungslos() {
        // smartFillers braucht den zweiten Aufruf — ohne ihn muss die Wortliste ran.
        assertTrue(plan(removeFillers = true, llmPolish = false, smartFillers = true).removeFillers)
    }

    @Test fun uebrigeOptionenWerdenDurchgereicht() {
        val o = plan()
        assertTrue(o.autoCapitalize)
        org.junit.Assert.assertEquals("de", o.language)
    }
}
