package com.chris.whisperbar

import android.content.Context
import android.content.Intent

/**
 * Zentrale Navigation aus Overlay, IME und Notification in die App (UX-Spec §1.2).
 *
 * Ziele sind VORERST die alten XML-Activities. Das Compose-Paket (WP4) ersetzt nur die
 * Zielklassen durch MainActivity — die Extras `route`/`step` bleiben, die Aufrufer auch.
 */
object AppNav {
    const val EXTRA_ROUTE = "route"
    const val EXTRA_STEP = "step"

    const val ROUTE_HOME = "home"
    const val ROUTE_SETUP = "setup"
    const val ROUTE_SETTINGS = "settings"

    /** Home (H): Notification-Tipp. */
    fun home(ctx: Context): Intent = intent(ctx, SetupActivity::class.java, ROUTE_HOME)

    /** Einrichtungs-Assistent (W), optional direkt auf Schritt [step] (1..7). */
    fun setup(ctx: Context, step: Int? = null): Intent =
        intent(ctx, SetupActivity::class.java, ROUTE_SETUP).apply {
            if (step != null) putExtra(EXTRA_STEP, step)
        }

    /** Einstellungen (E): IME-Zahnrad. */
    fun settings(ctx: Context): Intent = intent(ctx, SettingsActivity::class.java, ROUTE_SETTINGS)

    private fun intent(ctx: Context, target: Class<*>, route: String): Intent =
        Intent(ctx, target)
            .putExtra(EXTRA_ROUTE, route)
            // Aufrufer sind Services (kein Activity-Kontext) -> eigener Task noetig.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
