package com.chris.whisperloom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Activity-Verdrahtung: ohne Audio-Intent landet man im Zustand KEINE DATEI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareTranscribeActivityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ShareTranscribeActivity>()

    @Test fun ohneAudioZeigtKeineDatei() {
        compose.waitForIdle()
        compose.onNodeWithText("Keine Audiodatei erhalten").assertIsDisplayed()
        compose.onNodeWithText("Schließen").assertIsDisplayed()
    }
}
