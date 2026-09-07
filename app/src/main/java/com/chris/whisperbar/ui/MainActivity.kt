package com.chris.whisperbar.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.ui.nav.RouteRequest
import com.chris.whisperbar.ui.nav.SystemStatus
import com.chris.whisperbar.ui.state.AppEnv
import com.chris.whisperbar.ui.state.PrefsState
import com.chris.whisperbar.ui.theme.WhisperBarTheme

/**
 * Einzige Compose-Activity (UX-Spec §0.2): Router (§1.2), Home, Einrichtungs-Assistent,
 * Einstellungen. Deep-Links kommen per `route`/`step`-Extra (AppNav) — auch in onNewIntent
 * (launchMode singleTask). Der Systemstatus wird in onResume neu gelesen, weil Systemdialoge
 * Berechtigungen ausserhalb der App aendern.
 */
class MainActivity : ComponentActivity() {

    private lateinit var env: AppEnv

    /** Noch nicht verarbeiteter Deep-Link; WhisperBarApp setzt ihn nach dem Navigieren zurueck. */
    private var route: RouteRequest? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fest dunkel: helle Icons auf transparenten Systemleisten (Spec §0.2).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        env = AppEnv(PrefsState(Prefs(this)), SystemStatus.read(this)) { SystemStatus.read(this) }
        route = RouteRequest.initial(intent, savedInstanceState)
        setContent {
            WhisperBarTheme {
                WhisperBarApp(env = env, route = route, onRouteConsumed = { route = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route = RouteRequest.from(intent)
    }

    override fun onResume() {
        super.onResume()
        env.refreshStatus()
    }
}
