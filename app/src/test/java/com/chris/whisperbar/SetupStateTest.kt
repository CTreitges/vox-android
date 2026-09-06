package com.chris.whisperbar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM-Unit-Tests fuer "ist die App eingerichtet?" — davon haengt der Start-Screen ab. */
class SetupStateTest {

    @Test fun onlineBrauchtMikroUndZugang() {
        assertTrue(SetupState.isReady(Engine.ONLINE, sttComplete = true, offlineModelPresent = false, micGranted = true))
        assertFalse(SetupState.isReady(Engine.ONLINE, sttComplete = false, offlineModelPresent = true, micGranted = true))
        assertFalse(SetupState.isReady(Engine.ONLINE, sttComplete = true, offlineModelPresent = true, micGranted = false))
    }

    @Test fun offlineBrauchtMikroUndModell() {
        assertTrue(SetupState.isReady(Engine.OFFLINE, sttComplete = false, offlineModelPresent = true, micGranted = true))
        assertFalse(SetupState.isReady(Engine.OFFLINE, sttComplete = true, offlineModelPresent = false, micGranted = true))
        assertFalse(SetupState.isReady(Engine.OFFLINE, sttComplete = true, offlineModelPresent = true, micGranted = false))
    }

    @Test fun keineEngineGewaehltIstNieBereit() {
        assertFalse(SetupState.isReady(null, sttComplete = true, offlineModelPresent = true, micGranted = true))
    }

    @Test fun zugangVollstaendig() {
        assertTrue(SetupState.sttComplete("https://api.openai.com/v1", "sk", needsKey = true))
        assertFalse(SetupState.sttComplete("https://api.openai.com/v1", "", needsKey = true))
        assertFalse(SetupState.sttComplete("https://api.openai.com/v1", "  ", needsKey = true))
        assertTrue(SetupState.sttComplete("http://192.168.1.50:8000/v1", "", needsKey = false))
        assertFalse(SetupState.sttComplete("", "", needsKey = false))
        assertFalse(SetupState.sttComplete("  ", "sk", needsKey = true))
    }
}
