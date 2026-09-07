package com.chris.whisperbar.ui.state

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperbar.Engine
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.RefineMode
import com.chris.whisperbar.api.AccessResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Der Compose-Spiegel schreibt sofort durch (Spec §1.3: kein Speichern-Button). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrefsStateTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun clear() {
        ctx.getSharedPreferences("whisperbar", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun schreibtSofortDurch() {
        val state = PrefsState(Prefs(ctx))
        state.engine = Engine.OFFLINE
        state.refineMode = RefineMode.BEAUTIFY
        state.customFillers = setOf("Sozusagen", "halt ")
        state.sttProviderId = "groq"
        val fresh = Prefs(ctx)
        assertEquals(Engine.OFFLINE, fresh.engine)
        assertEquals(RefineMode.BEAUTIFY, fresh.refineMode)
        assertEquals(setOf("sozusagen", "halt"), fresh.customFillers)
        assertEquals("groq", fresh.sttProviderId)
        // Der Spiegel liefert die normalisierte Fassung nicht selbst — Anzeige ist klein geschrieben ueber den Setter-Aufrufer.
        assertEquals("https://api.groq.com/openai/v1", state.sttAccess().baseUrl)
    }

    @Test fun llmUseOwnFolgtDemProviderFeld() {
        val state = PrefsState(Prefs(ctx))
        assertFalse(state.llmUseOwn)
        state.llmProviderId = "openai"
        assertTrue(state.llmUseOwn)
        state.llmProviderId = AccessResolver.LLM_SAME
        assertFalse(state.llmUseOwn)
    }

    @Test fun positionZuruecksetzen() {
        val prefs = Prefs(ctx)
        prefs.floatX = 500
        prefs.floatY = 900
        PrefsState(prefs).resetBubblePosition()
        assertEquals(Prefs.DEFAULT_FLOAT_X, Prefs(ctx).floatX)
        assertEquals(Prefs.DEFAULT_FLOAT_Y, Prefs(ctx).floatY)
    }
}
