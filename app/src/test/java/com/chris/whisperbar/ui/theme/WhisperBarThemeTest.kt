package com.chris.whisperbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Compose-Smoke auf der JVM. Robolectric 4.16.1: SDK 35 laeuft mit JDK 17, SDK 36 braeuchte JDK 21.
// SDK explizit pinnen, damit ein spaeterer targetSdk-Sprung die Tests nicht still bricht.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WhisperBarThemeTest {

    // v2-API (Compose 1.12): StandardTestDispatcher statt Unconfined; die v1-createComposeRule ist deprecated.
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersTextInsideTheme() {
        compose.setContent {
            WhisperBarTheme { Text("Hallo WhisperBar") }
        }
        compose.onNodeWithText("Hallo WhisperBar").assertIsDisplayed()
    }

    @Test
    fun colorSchemeUsesSpecTokens() {
        var background: Color? = null
        var primary: Color? = null
        var surfaceContainerHigh: Color? = null
        var outlineVariant: Color? = null
        compose.setContent {
            WhisperBarTheme {
                background = MaterialTheme.colorScheme.background
                primary = MaterialTheme.colorScheme.primary
                surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
                outlineVariant = MaterialTheme.colorScheme.outlineVariant
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFF0E1116), background)
        assertEquals(Color(0xFF6FD9C7), primary)
        assertEquals(Color(0xFF242A32), surfaceContainerHigh)
        assertEquals(Color(0xFF3A424D), outlineVariant)
    }

    @Test
    fun wbColorsProvideRecordingToken() {
        var recording: Color? = null
        var warningContainer: Color? = null
        compose.setContent {
            WhisperBarTheme {
                recording = MaterialTheme.wb.recording
                warningContainer = MaterialTheme.wb.warningContainer
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFFFF4D4D), recording)
        assertEquals(Color(0xFF4A3600), warningContainer)
    }

    @Test
    fun wbColorsFallBackToPaletteOutsideTheme() {
        var recording: Color? = null
        compose.setContent { recording = MaterialTheme.wb.recording }
        compose.waitForIdle()
        assertEquals(WbPalette.recording, recording)
    }

    // "Eine Farb-Wahrheit": WbPalette (Kotlin) darf nicht von colors.xml wegdriften.
    @Test
    fun paletteMatchesColorResources() {
        val expected = mapOf(
            "wb_background" to WbPalette.background,
            "wb_surface" to WbPalette.surface,
            "wb_surfaceContainerLowest" to WbPalette.surfaceContainerLowest,
            "wb_surfaceContainerLow" to WbPalette.surfaceContainerLow,
            "wb_surfaceContainer" to WbPalette.surfaceContainer,
            "wb_surfaceContainerHigh" to WbPalette.surfaceContainerHigh,
            "wb_surfaceContainerHighest" to WbPalette.surfaceContainerHighest,
            "wb_surfaceBright" to WbPalette.surfaceBright,
            "wb_onSurface" to WbPalette.onSurface,
            "wb_onSurfaceVariant" to WbPalette.onSurfaceVariant,
            "wb_outline" to WbPalette.outline,
            "wb_outlineVariant" to WbPalette.outlineVariant,
            "wb_primary" to WbPalette.primary,
            "wb_onPrimary" to WbPalette.onPrimary,
            "wb_primaryContainer" to WbPalette.primaryContainer,
            "wb_onPrimaryContainer" to WbPalette.onPrimaryContainer,
            "wb_secondary" to WbPalette.secondary,
            "wb_onSecondary" to WbPalette.onSecondary,
            "wb_secondaryContainer" to WbPalette.secondaryContainer,
            "wb_onSecondaryContainer" to WbPalette.onSecondaryContainer,
            "wb_tertiary" to WbPalette.tertiary,
            "wb_onTertiary" to WbPalette.onTertiary,
            "wb_tertiaryContainer" to WbPalette.tertiaryContainer,
            "wb_onTertiaryContainer" to WbPalette.onTertiaryContainer,
            "wb_error" to WbPalette.error,
            "wb_onError" to WbPalette.onError,
            "wb_errorContainer" to WbPalette.errorContainer,
            "wb_onErrorContainer" to WbPalette.onErrorContainer,
            "wb_recording" to WbPalette.recording,
            "wb_onRecording" to WbPalette.onRecording,
            "wb_recordingContainer" to WbPalette.recordingContainer,
            "wb_recordingText" to WbPalette.recordingText,
            "wb_success" to WbPalette.success,
            "wb_onSuccess" to WbPalette.onSuccess,
            "wb_successContainer" to WbPalette.successContainer,
            "wb_onSuccessContainer" to WbPalette.onSuccessContainer,
            "wb_warning" to WbPalette.warning,
            "wb_onWarning" to WbPalette.onWarning,
            "wb_warningContainer" to WbPalette.warningContainer,
            "wb_onWarningContainer" to WbPalette.onWarningContainer,
        )
        val actual = mutableMapOf<String, Color>()
        compose.setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            for (name in expected.keys) {
                // getIdentifier nur im Test: die Namen kommen aus der Spec-Liste oben, nicht aus R.
                val id = context.resources.getIdentifier(name, "color", context.packageName)
                actual[name] = if (id == 0) Color.Unspecified else colorResource(id)
            }
        }
        compose.waitForIdle()
        assertEquals(expected, actual)
    }
}
