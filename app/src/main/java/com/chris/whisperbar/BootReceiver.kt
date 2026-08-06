package com.chris.whisperbar

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.chris.whisperbar.overlay.FloatingMicService

/**
 * Startet den schwebenden Diktat-Knopf nach einem Neustart wieder — aber nur, wenn der
 * Nutzer das ausdruecklich eingeschaltet hat (Standard: aus).
 *
 * Der Start kann vom System abgelehnt werden (Vordergrunddienste vom Typ "microphone"
 * duerfen je nach Android-Version nicht aus dem Hintergrund starten). Das ist kein
 * Fehlerfall: dann bleibt der Knopf eben aus, bis man die App einmal oeffnet.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = Prefs.get(context)
        if (!prefs.bubbleAutoStart) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        if (!Settings.canDrawOverlays(context)) return

        runCatching { FloatingMicService.start(context) }
            .onFailure { Log.i(TAG, "Autostart abgelehnt: ${it.message}") }
    }

    private companion object {
        const val TAG = "WB-Boot"
    }
}
