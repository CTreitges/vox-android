package com.chris.vox.ui.theme

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
class VoxThemeTest {

    // v2-API (Compose 1.12): StandardTestDispatcher statt Unconfined; die v1-createComposeRule ist deprecated.
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersTextInsideTheme() {
        compose.setContent {
            VoxTheme { Text("Hallo Vox") }
        }
        compose.onNodeWithText("Hallo Vox").assertIsDisplayed()
    }

    @Test
    fun colorSchemeUsesSpecTokens() {
        var background: Color? = null
        var primary: Color? = null
        var surfaceContainerHigh: Color? = null
        var outlineVariant: Color? = null
        compose.setContent {
            VoxTheme {
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
    fun voxColorsProvideRecordingToken() {
        var recording: Color? = null
        var warningContainer: Color? = null
        compose.setContent {
            VoxTheme {
                recording = MaterialTheme.vox.recording
                warningContainer = MaterialTheme.vox.warningContainer
            }
        }
        compose.waitForIdle()
        assertEquals(Color(0xFFFF4D4D), recording)
        assertEquals(Color(0xFF4A3600), warningContainer)
    }

    @Test
    fun voxColorsFallBackToPaletteOutsideTheme() {
        var recording: Color? = null
        compose.setContent { recording = MaterialTheme.vox.recording }
        compose.waitForIdle()
        assertEquals(VoxPalette.recording, recording)
    }

    // "Eine Farb-Wahrheit": VoxPalette (Kotlin) darf nicht von colors.xml wegdriften.
    @Test
    fun paletteMatchesColorResources() {
        val expected = mapOf(
            "vox_background" to VoxPalette.background,
            "vox_surface" to VoxPalette.surface,
            "vox_surfaceContainerLowest" to VoxPalette.surfaceContainerLowest,
            "vox_surfaceContainerLow" to VoxPalette.surfaceContainerLow,
            "vox_surfaceContainer" to VoxPalette.surfaceContainer,
            "vox_surfaceContainerHigh" to VoxPalette.surfaceContainerHigh,
            "vox_surfaceContainerHighest" to VoxPalette.surfaceContainerHighest,
            "vox_surfaceBright" to VoxPalette.surfaceBright,
            "vox_onSurface" to VoxPalette.onSurface,
            "vox_onSurfaceVariant" to VoxPalette.onSurfaceVariant,
            "vox_outline" to VoxPalette.outline,
            "vox_outlineVariant" to VoxPalette.outlineVariant,
            "vox_primary" to VoxPalette.primary,
            "vox_onPrimary" to VoxPalette.onPrimary,
            "vox_primaryContainer" to VoxPalette.primaryContainer,
            "vox_onPrimaryContainer" to VoxPalette.onPrimaryContainer,
            "vox_secondary" to VoxPalette.secondary,
            "vox_onSecondary" to VoxPalette.onSecondary,
            "vox_secondaryContainer" to VoxPalette.secondaryContainer,
            "vox_onSecondaryContainer" to VoxPalette.onSecondaryContainer,
            "vox_tertiary" to VoxPalette.tertiary,
            "vox_onTertiary" to VoxPalette.onTertiary,
            "vox_tertiaryContainer" to VoxPalette.tertiaryContainer,
            "vox_onTertiaryContainer" to VoxPalette.onTertiaryContainer,
            "vox_error" to VoxPalette.error,
            "vox_onError" to VoxPalette.onError,
            "vox_errorContainer" to VoxPalette.errorContainer,
            "vox_onErrorContainer" to VoxPalette.onErrorContainer,
            "vox_recording" to VoxPalette.recording,
            "vox_onRecording" to VoxPalette.onRecording,
            "vox_recordingContainer" to VoxPalette.recordingContainer,
            "vox_recordingText" to VoxPalette.recordingText,
            "vox_success" to VoxPalette.success,
            "vox_onSuccess" to VoxPalette.onSuccess,
            "vox_successContainer" to VoxPalette.successContainer,
            "vox_onSuccessContainer" to VoxPalette.onSuccessContainer,
            "vox_warning" to VoxPalette.warning,
            "vox_onWarning" to VoxPalette.onWarning,
            "vox_warningContainer" to VoxPalette.warningContainer,
            "vox_onWarningContainer" to VoxPalette.onWarningContainer,
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
