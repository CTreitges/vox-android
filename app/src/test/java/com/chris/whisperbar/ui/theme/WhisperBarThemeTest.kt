package com.chris.whisperbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
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
    fun colorSchemeBackgroundIsBackgroundToken() {
        var background: Color? = null
        compose.setContent {
            WhisperBarTheme { background = MaterialTheme.colorScheme.background }
        }
        compose.waitForIdle()
        assertEquals(Background, background)
    }
}
