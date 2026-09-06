package com.chris.whisperbar.ui.share

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.SharedTranscript
import com.chris.whisperbar.UnsupportedAudioException
import com.chris.whisperbar.api.ApiNotConfiguredException
import com.chris.whisperbar.whisper.OfflineNotAvailableException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * Ablauf-Logik der Share-Ansicht mit Fake-Erkenner, Inline-Executor und Inline-Post:
 * alles laeuft synchron, Zwischenzustaende werden im Fake beobachtet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareControllerTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val uriA: Uri = Uri.parse("content://test/a.ogg")
    private val uriB: Uri = Uri.parse("content://test/b.ogg")

    @Before fun clearPrefs() {
        ctx.getSharedPreferences("whisperbar", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun transcript(source: String, verbatim: String = "Hallo Welt.", cleaned: String = verbatim, ms: Long = 5000) =
        SharedTranscript(
            source = source,
            verbatimText = verbatim,
            cleanedText = cleaned,
            paragraphsVerbatim = listOf(verbatim),
            paragraphsCleaned = listOf(cleaned),
            durationMs = ms,
            backendLabel = "Test",
        )

    private fun controller(uris: List<Uri>, transcriber: ShareTranscriber) = ShareController(
        context = ctx,
        uris = uris,
        transcriber = transcriber,
        nameOf = { _, uri -> uri.lastPathSegment.orEmpty() },
        executor = Executor { it.run() },
        post = { it() },
    )

    private fun byName() = ShareTranscriber { _, uri, _, _ -> transcript(uri.lastPathSegment!!) }

    @Test fun ohneDateiKeinLauf() {
        val c = controller(emptyList(), ShareTranscriber { _, _, _, _ -> fail("darf nicht laufen"); error("x") })
        c.start()
        assertEquals(SharePhase.NO_FILE, c.state.phase)
        assertEquals("", c.plainText())
    }

    @Test fun teilergebnisseSofortDannFertig() {
        val seen = mutableListOf<ShareUiState>()
        lateinit var c: ShareController
        c = controller(listOf(uriA, uriB), ShareTranscriber { _, uri, onProgress, _ ->
            seen.add(c.state)
            onProgress(0, 1, "Wird übertragen …")
            transcript(uri.lastPathSegment!!)
        })
        assertEquals(SharePhase.LOADING, c.state.phase)
        c.start()

        // Beim Start der zweiten Datei: Namen bekannt, erstes Ergebnis da, Phase noch LADEN.
        assertEquals(2, seen.size)
        assertEquals(listOf("a.ogg", "b.ogg"), seen[0].files.map { it.name })
        assertEquals(SharePhase.LOADING, seen[1].phase)
        assertEquals("a.ogg", seen[1].files[0].result?.source)
        assertNull(seen[1].files[1].result)

        assertEquals(SharePhase.DONE, c.state.phase)
        assertNull(c.state.progress)
        assertFalse(c.state.hasErrors)
        assertEquals(listOf("a.ogg", "b.ogg"), c.state.results.map { it.source })
        assertEquals(10_000L, c.state.totalDurationMs)
    }

    @Test fun fortschrittWirdDurchgereicht() {
        val seen = mutableListOf<ShareProgress?>()
        lateinit var c: ShareController
        c = controller(listOf(uriA, uriB), ShareTranscriber { _, uri, onProgress, _ ->
            onProgress(1, 3, "Wird übertragen …")
            seen.add(c.state.progress)
            transcript(uri.lastPathSegment!!)
        })
        c.start()
        assertEquals(
            listOf(ShareProgress(0, 2, 1, 3, "Wird übertragen …"), ShareProgress(1, 2, 1, 3, "Wird übertragen …")),
            seen,
        )
    }

    @Test fun fehlerJeDateiUndEinzelRetry() {
        var failB = true
        var calls = 0
        val c = controller(listOf(uriA, uriB), ShareTranscriber { _, uri, _, _ ->
            calls++
            if (uri == uriB && failB) throw UnsupportedAudioException("Kaputt")
            transcript(uri.lastPathSegment!!)
        })
        c.start()
        assertEquals(SharePhase.DONE, c.state.phase)
        assertTrue(c.state.hasErrors)
        assertEquals("Kaputt", c.state.files[1].error)
        assertEquals("a.ogg", c.state.files[0].result?.source)
        assertEquals(1, c.state.results.size)

        failB = false
        c.retryFile(1)
        assertEquals(SharePhase.DONE, c.state.phase)
        assertFalse(c.state.hasErrors)
        assertEquals("b.ogg", c.state.files[1].result?.source)
        assertEquals(3, calls) // Datei 1 wurde nicht noch einmal erkannt
    }

    @Test fun alleFehlgeschlagenDannAllesErneut() {
        var fail = true
        val c = controller(listOf(uriA, uriB), ShareTranscriber { _, uri, _, _ ->
            if (fail) throw UnsupportedAudioException("Kaputt ${uri.lastPathSegment}")
            transcript(uri.lastPathSegment!!)
        })
        c.start()
        assertEquals(SharePhase.ALL_FAILED, c.state.phase)
        assertEquals("Kaputt a.ogg", c.state.failure)
        assertNull(c.state.progress)

        fail = false
        c.retryAll()
        assertEquals(SharePhase.DONE, c.state.phase)
        assertNull(c.state.failure)
        assertEquals(2, c.state.results.size)
    }

    @Test fun fehlerOhneMeldungBekommtErsatztext() {
        val c = controller(listOf(uriA), ShareTranscriber { _, _, _, _ -> throw IllegalStateException() })
        c.start()
        assertEquals(SharePhase.ALL_FAILED, c.state.phase)
        assertEquals("Unbekannter Fehler: IllegalStateException", c.state.failure)
    }

    @Test fun keinZugangOnlineUndOffline() {
        val online = controller(listOf(uriA, uriB), ShareTranscriber { _, _, _, _ -> throw ApiNotConfiguredException() })
        online.start()
        assertEquals(SharePhase.NOT_CONFIGURED, online.state.phase)
        assertNull(online.state.progress)

        val offline = controller(listOf(uriA), ShareTranscriber { _, _, _, _ -> throw OfflineNotAvailableException() })
        offline.start()
        assertEquals(SharePhase.NOT_CONFIGURED, offline.state.phase)
    }

    @Test fun abbruchVerwirftSpaeteErgebnisse() {
        lateinit var c: ShareController
        c = controller(listOf(uriA), ShareTranscriber { _, _, _, isCancelled ->
            assertFalse(isCancelled())
            c.cancel()
            assertTrue(isCancelled())
            transcript("a.ogg")
        })
        c.start()
        assertEquals(SharePhase.LOADING, c.state.phase)
        assertNull(c.state.files[0].result)
    }

    @Test fun schalterSchreibtPrefsUndWirktAufPlainText() {
        val c = controller(listOf(uriA), ShareTranscriber { _, _, _, _ ->
            transcript("a.ogg", verbatim = "Ähm hallo Welt.", cleaned = "Hallo Welt.")
        })
        c.start()
        assertTrue(c.state.hideFillers)
        assertEquals("Hallo Welt.", c.plainText())

        c.setHideFillers(false)
        assertFalse(c.state.hideFillers)
        assertFalse(Prefs(ctx).shareHideFillers)
        assertEquals("Ähm hallo Welt.", c.plainText())
    }

    @Test fun schalterStartetMitPrefWert() {
        Prefs(ctx).shareHideFillers = false
        val c = controller(listOf(uriA), byName())
        assertFalse(c.state.hideFillers)
    }

    @Test fun plainTextMitUeberschriftenNurBeiMehrerenDateien() {
        val one = controller(listOf(uriA), byName())
        one.start()
        assertEquals("Hallo Welt.", one.plainText())

        val two = controller(listOf(uriA, uriB), byName())
        two.start()
        assertEquals("— a.ogg · 0:05 —\n\nHallo Welt.\n\n— b.ogg · 0:05 —\n\nHallo Welt.", two.plainText())
    }
}
