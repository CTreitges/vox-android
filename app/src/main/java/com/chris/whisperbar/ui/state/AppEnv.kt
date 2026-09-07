package com.chris.whisperbar.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.chris.whisperbar.ui.nav.SystemStatus

/**
 * Alles, was die Screens von aussen brauchen: die Einstellungen ([PrefsState]) und der
 * Systemstatus ([SystemStatus]), den die Activity in onResume neu liest (Systemdialoge
 * aendern Berechtigungen ausserhalb der App, UX-Spec §1.2).
 */
class AppEnv(
    val prefs: PrefsState,
    initialStatus: SystemStatus,
    private val reader: () -> SystemStatus,
) {
    var status: SystemStatus by mutableStateOf(initialStatus)
        private set

    fun refreshStatus() {
        status = reader()
    }
}

val LocalAppEnv = staticCompositionLocalOf<AppEnv> {
    error("LocalAppEnv fehlt — WhisperBarApp (oder der Test) muss ihn bereitstellen")
}
