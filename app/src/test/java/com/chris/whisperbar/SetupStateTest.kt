package com.chris.whisperbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Unit-Tests fuer "Zugang vollstaendig?" — Baustein von SetupRouter.recognitionReady und TranscriptionEngine.isConfigured. */
class SetupStateTest {

    @Test fun zugangVollstaendig() {
        assertTrue(SetupState.sttComplete("https://api.openai.com/v1", "sk", needsKey = true))
        assertFalse(SetupState.sttComplete("https://api.openai.com/v1", "", needsKey = true))
        assertFalse(SetupState.sttComplete("https://api.openai.com/v1", "  ", needsKey = true))
        assertTrue(SetupState.sttComplete("http://192.168.1.50:8000/v1", "", needsKey = false))
        assertFalse(SetupState.sttComplete("", "", needsKey = false))
        assertFalse(SetupState.sttComplete("  ", "sk", needsKey = true))
    }
}
