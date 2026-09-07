package com.chris.whisperbar.ui.share

import com.chris.whisperbar.SharedTranscript
import org.junit.Assert.assertEquals
import org.junit.Test

/** Reiner Text fuer Kopieren/Teilen — ohne Android. */
class ShareTextTest {

    private fun transcript(
        source: String,
        cleaned: List<String>,
        verbatim: List<String> = cleaned,
        durationMs: Long = 5000,
    ) = SharedTranscript(
        source = source,
        verbatimText = verbatim.joinToString("\n\n"),
        cleanedText = cleaned.joinToString("\n\n"),
        paragraphsVerbatim = verbatim,
        paragraphsCleaned = cleaned,
        durationMs = durationMs,
        backendLabel = "Test",
    )

    private val a = transcript("a.ogg", cleaned = listOf("A eins.", "A zwei."), verbatim = listOf("Ähm A eins.", "A zwei."))
    private val b = transcript("b.ogg", cleaned = listOf("B eins."), durationMs = 62_000)

    @Test fun eineDateiOhneUeberschrift() {
        assertEquals("A eins.\n\nA zwei.", ShareText.plain(listOf(a), hideFillers = true, withHeadings = false))
    }

    @Test fun mehrereDateienMitUeberschriftUndLeerzeile() {
        assertEquals(
            "— a.ogg · 0:05 —\n\nA eins.\n\nA zwei.\n\n— b.ogg · 1:02 —\n\nB eins.",
            ShareText.plain(listOf(a, b), hideFillers = true, withHeadings = true),
        )
    }

    @Test fun schalterWaehltDieWortgetreueFassung() {
        assertEquals("Ähm A eins.\n\nA zwei.", ShareText.plain(listOf(a), hideFillers = false, withHeadings = false))
        assertEquals(listOf("Ähm A eins.", "A zwei."), ShareText.paragraphs(a, hideFillers = false))
        assertEquals(listOf("A eins.", "A zwei."), ShareText.paragraphs(a, hideFillers = true))
    }

    @Test fun leeresErgebnisWirdPlatzhalter() {
        val empty = transcript("leer.ogg", cleaned = emptyList(), durationMs = 0)
        assertEquals("(nichts erkannt)", ShareText.plain(listOf(empty), true, false, emptyText = "(nichts erkannt)"))
        assertEquals("— leer.ogg · 0:00 —\n\n(nichts erkannt)", ShareText.plain(listOf(empty), true, true, "(nichts erkannt)"))
        assertEquals("— leer.ogg · 0:00 —", ShareText.plain(listOf(empty), true, true))
    }

    @Test fun ohneErgebnisseLeer() {
        assertEquals("", ShareText.plain(emptyList(), true, true))
    }
}
