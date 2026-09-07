package com.chris.whisperbar.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperbar.Engine
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.RefineMode
import com.chris.whisperbar.ui.home.HomeScreen
import com.chris.whisperbar.ui.nav.NavState
import com.chris.whisperbar.ui.nav.Screen
import com.chris.whisperbar.ui.nav.SystemStatus
import com.chris.whisperbar.ui.settings.HelpScreen
import com.chris.whisperbar.ui.settings.ModelsScreen
import com.chris.whisperbar.ui.settings.RecognitionScreen
import com.chris.whisperbar.whisper.DownloadState
import com.chris.whisperbar.whisper.ModelCatalog
import com.chris.whisperbar.whisper.ModelDownloads
import com.chris.whisperbar.whisper.ModelStore
import java.io.RandomAccessFile
import com.chris.whisperbar.ui.settings.SettingsHubScreen
import com.chris.whisperbar.ui.settings.TextSettingsScreen
import com.chris.whisperbar.ui.state.AppEnv
import com.chris.whisperbar.ui.state.LocalAppEnv
import com.chris.whisperbar.ui.state.PrefsState
import com.chris.whisperbar.ui.theme.WhisperBarTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose-Semantik-Tests der Hauptscreens (Robolectric, kein Bitmap-Rendering): Router,
 * Assistent-Schritte 1/2a, E2, E4, B3, E5, Hub. Hohes Fenster, damit scrollende Spalten und
 * LazyColumns alles komponieren.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h2400dp-xxhdpi")
class MainFlowTest {

    @get:Rule
    val compose = createComposeRule()

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs

    /** Alles, was Home braucht: Mikrofon, Overlay, Bedienungshilfe. */
    private val readyStatus = SystemStatus(micGranted = true, canDrawOverlays = true, a11yRunning = true)

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperbar", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
    }

    private fun env(status: SystemStatus = SystemStatus()) = AppEnv(PrefsState(prefs), status) { status }

    private fun app(env: AppEnv) {
        compose.setContent { WhisperBarTheme { WhisperBarApp(env) } }
        compose.waitForIdle()
    }

    private fun screen(env: AppEnv, content: @Composable (NavState) -> Unit) {
        val nav = NavState(listOf(Screen.Home, Screen.SettingsHub))
        compose.setContent {
            WhisperBarTheme { CompositionLocalProvider(LocalAppEnv provides env) { content(nav) } }
        }
        compose.waitForIdle()
    }

    // --- Router ----------------------------------------------------------------

    @Test fun routerZeigtWillkommenBeimAllererstenStart() {
        app(env())
        compose.onNodeWithText("Diktiere in jede App.").assertIsDisplayed()
        compose.onNodeWithText("Los geht's").assertIsDisplayed()
    }

    @Test fun routerZeigtSchritt1WennEngineFehlt() {
        prefs.welcomeSeen = true
        app(env())
        compose.onNodeWithText("Wie soll WhisperBar Sprache erkennen?").assertIsDisplayed()
        compose.onNodeWithText("Weiter").assertIsNotEnabled()
    }

    @Test fun routerZeigtHomeMitHeroWennEingerichtet() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        app(env(readyStatus))
        compose.onNodeWithText("Mikro-Knopf starten").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("Online · OpenAI · GPT Transcribe (empfohlen)").assertIsDisplayed()
        compose.onNodeWithText("Mikrofon ✓ · Über Apps ✓ · Bedienungshilfe ✓").assertIsDisplayed()
    }

    @Test fun homeSperrtHeroUndZeigtChipBeiSpaeteremMikrofonEntzug() {
        prefs.engine = Engine.ONLINE
        prefs.apiKey = "sk-test"
        // Home bleibt bei spaeterem Entzug offen (kein Rauswurf): Hero gesperrt + klickbarer Warn-Chip.
        screen(env(readyStatus.copy(micGranted = false))) { HomeScreen(it) }
        compose.onNodeWithText("Mikro-Knopf starten").assertIsNotEnabled()
        compose.onNodeWithText("Mikrofon fehlt — beheben").assertIsDisplayed()
        compose.onNodeWithText("Pflicht-Berechtigung fehlt").assertExists()
    }

    // --- Assistent ---------------------------------------------------------------

    @Test fun schritt1AuswahlSchreibtEngine() {
        prefs.welcomeSeen = true
        app(env())
        compose.onNodeWithText("Online-Dienst").performClick()
        compose.waitForIdle()
        assertEquals(Engine.ONLINE, Prefs(ctx).engine)
        compose.onNodeWithText("Weiter").assertIsEnabled()
    }

    @Test fun schritt2aAnbieterWechselSetztPresetUndErstesModell() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE
        app(env())
        compose.onNodeWithText("Zugang zum Dienst").assertIsDisplayed()
        compose.onNodeWithText("https://api.openai.com/v1").assertExists()
        compose.onNodeWithTag("dropdown:Anbieter").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Groq (kostenlos)").performClick()
        compose.waitForIdle()
        assertEquals("groq", Prefs(ctx).sttProviderId)
        assertEquals("", Prefs(ctx).apiBaseUrl)
        assertEquals("", Prefs(ctx).apiModel)
        compose.onNodeWithText("https://api.groq.com/openai/v1").assertExists()
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("Whisper Large v3 Turbo")
    }

    @Test fun eigenesModellUebernimmtFreieId() {
        prefs.welcomeSeen = true
        prefs.engine = Engine.ONLINE
        app(env())
        compose.onNodeWithTag("dropdown:Modell").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Eigenes Modell …").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Modell-ID").performTextInput("mein-modell")
        compose.onNodeWithText("Übernehmen").performClick()
        compose.waitForIdle()
        assertEquals("mein-modell", Prefs(ctx).apiModel)
        compose.onNodeWithTag("dropdown:Modell").assertTextContains("mein-modell")
    }

    // --- E2 Text -----------------------------------------------------------------

    @Test fun textStufeSchreibtRefineModeUndSchaltetSmartFillersFrei() {
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsNotEnabled()
        compose.onNodeWithText("Glätten").performClick()
        compose.waitForIdle()
        assertEquals(RefineMode.POLISH, Prefs(ctx).refineMode)
        compose.onNodeWithText("Füllwörter intelligent entfernen").assertIsEnabled()
    }

    @Test fun fuellwoerterSheetFuegtEigenesWortHinzu() {
        screen(env()) { TextSettingsScreen(it) }
        compose.onNodeWithText("Liste bearbeiten").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Noch keine eigenen Wörter").assertExists()
        compose.onNodeWithText("Wort hinzufügen").performTextInput("sozusagen")
        compose.onNodeWithContentDescription("Wort hinzufügen").performClick()
        compose.waitForIdle()
        assertTrue("sozusagen" in Prefs(ctx).customFillers)
        compose.onNodeWithText("sozusagen").assertExists()
    }

    // --- E4 Offline-Modelle -------------------------------------------------------

    @Test fun modelleZeigenVierEintraegeEmpfehlungUndGrossGedimmt() {
        screen(env(SystemStatus(totalRamBytes = 4L shl 30))) { ModelsScreen(it) }
        listOf("Tiny", "Base", "Small", "Large v3 Turbo").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Empfohlen").assertExists()
        compose.onNodeWithText("Für dieses Gerät zu groß").assertExists()
        compose.onNodeWithContentDescription("Large v3 Turbo herunterladen").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Small herunterladen").assertIsEnabled()
        compose.onNodeWithText("Noch kein Modell geladen").assertExists()
    }

    @Test fun modellZeileIstEinAuswaehlbaresElementMitNamen() {
        // Review SPEC-3: TalkBack liest "Base, Optionsfeld, …" statt viermal nur "Optionsfeld".
        val store = ModelStore(ctx)
        store.ensureDir()
        RandomAccessFile(store.file(ModelCatalog.BASE), "rw").use { it.setLength(ModelCatalog.BASE.bytes) }
        try {
            screen(env()) { ModelsScreen(it) }
            compose.onNode(isSelectable() and hasText("Small")).assertIsNotEnabled().assertIsSelected() // Default, nicht installiert
            compose.onNode(isSelectable() and hasText("Base")).assertIsEnabled().assertIsNotSelected().performClick()
            compose.waitForIdle()
            assertEquals("base", Prefs(ctx).offlineModel)
            compose.onNode(isSelectable() and hasText("Base")).assertIsSelected()
            // Loeschen bleibt ein eigener, beschrifteter Knoten ausserhalb der Radio-Zeile.
            compose.onNodeWithContentDescription("Base löschen").assertIsEnabled()
        } finally {
            store.delete(ModelCatalog.BASE)
        }
    }

    @Test fun erneutIstWaehrendAnderemDownloadGesperrt() {
        // Review KOR-6: der Dienst ignoriert einen zweiten Start still — der Knopf darf ihn gar nicht anbieten.
        ModelDownloads.update("tiny", DownloadState.Failed("Netzwerkfehler beim Laden", retryable = true))
        try {
            screen(env()) { ModelsScreen(it) }
            compose.onNodeWithText("Erneut").assertIsEnabled()
            ModelDownloads.update("base", DownloadState.Running(1_000, 60_000_000, 500))
            compose.waitForIdle()
            compose.onNodeWithText("Erneut").assertIsNotEnabled()
        } finally {
            ModelDownloads.clear("tiny")
            ModelDownloads.clear("base")
        }
    }

    @Test fun kontextFeldSagtBeiMistralDassNichtsMitgeschicktWird() {
        // Review API-2: kein context_bias umgesetzt — kein Wortlisten-Versprechen in der Oberflaeche.
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "mistral"
        prefs.apiKey = "k"
        screen(env()) { RecognitionScreen(it) }
        compose.onNodeWithText("Dieser Anbieter nimmt keinen Kontext entgegen — das Feld wirkt nur bei anderen Anbietern und offline.")
            .assertExists()
        compose.onNodeWithText("Kontext-Wörter (kommagetrennt)").assertDoesNotExist()
    }

    @Test fun eigenerServerMarkiertLeeresModellAlsFehler() {
        // Review API-7: kein Modell-Default beim eigenen Server -> Pflichtfeld.
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "http://192.168.1.5:8000/v1"
        screen(env()) { RecognitionScreen(it) }
        val modelField = compose.onNode(hasSetTextAction() and hasText("Modell"))
        modelField.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        modelField.performTextInput("whisper-1")
        compose.waitForIdle()
        compose.onNode(hasSetTextAction() and hasText("Modell")).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
    }

    // --- E5 Hilfe, Hub -------------------------------------------------------------

    @Test fun hilfeHatSiebenAbschnitte() {
        screen(env()) { HelpScreen(1, it) }
        listOf(
            "So funktioniert's", "Einrichtung Schritt für Schritt", "API-Key bekommen", "Eigener Server",
            "Offline-Modus", "Datenschutz", "Wenn etwas nicht klappt",
        ).forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun hubZeigtSechsZeilen() {
        prefs.engine = Engine.ONLINE
        screen(env()) { SettingsHubScreen(it) }
        listOf("Erkennung", "Text", "Knopf & Tastatur", "Offline-Modelle", "Anleitung & Hilfe", "Über WhisperBar")
            .forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Version 3.0.0").assertExists()
    }
}
