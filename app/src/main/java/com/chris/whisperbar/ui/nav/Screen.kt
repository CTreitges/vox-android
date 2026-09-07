package com.chris.whisperbar.ui.nav

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import com.chris.whisperbar.AppNav

/** Die Screens der MainActivity (UX-Spec §1.1). [key] ist stabil je Screen-Typ (Uebergangs-Animation). */
sealed class Screen(val key: String) {
    data object Home : Screen("home")

    /** [step] 0 = Willkommen (W1), 1..7 = Schritte, 8 = Fertig (W9). */
    data class Setup(val step: Int) : Screen("setup") {
        companion object {
            const val WELCOME = 0
            const val DONE = 8
        }
    }

    data object SettingsHub : Screen("settings")
    data object Recognition : Screen("recognition")
    data object TextSettings : Screen("text")
    data object ButtonKeyboard : Screen("button")
    data object Models : Screen("models")

    /** [section] 1..7 = initial geoeffneter Hilfe-Abschnitt. */
    data class Help(val section: Int = 1) : Screen("help")

    fun encode(): String = when (this) {
        is Setup -> "$key:$step"
        is Help -> "$key:$section"
        else -> key
    }

    companion object {
        fun decode(s: String): Screen {
            val arg = s.substringAfter(':', "").toIntOrNull()
            return when (s.substringBefore(':')) {
                "setup" -> Setup(arg ?: Setup.WELCOME)
                "settings" -> SettingsHub
                "recognition" -> Recognition
                "text" -> TextSettings
                "button" -> ButtonKeyboard
                "models" -> Models
                "help" -> Help(arg ?: 1)
                else -> Home
            }
        }
    }
}

/** Einfacher Back-Stack ohne Navigation-Lib (Spec §0.2); ueberlebt Rotation/Prozess-Tod per Saver. */
class NavState(initial: List<Screen>) {

    private val stack = mutableStateListOf<Screen>().also { it.addAll(initial) }

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (canPop) stack.removeAt(stack.lastIndex)
    }

    fun replaceTop(screen: Screen) {
        stack[stack.lastIndex] = screen
    }

    fun replaceAll(vararg screens: Screen) {
        stack.clear()
        stack.addAll(screens)
    }

    fun snapshot(): List<Screen> = stack.toList()

    companion object {
        val Saver = listSaver<NavState, String>(
            save = { it.snapshot().map(Screen::encode) },
            restore = { NavState(it.map(Screen::decode)) },
        )
    }
}

@Composable
fun rememberNavState(initial: () -> List<Screen>): NavState =
    rememberSaveable(saver = NavState.Saver) { NavState(initial()) }

/** Deep-Link-Wunsch aus dem Start-Intent (AppNav) — `route` home|settings|setup, optional `step` 1..7. */
data class RouteRequest(val route: String, val step: Int? = null) {
    companion object {
        /** Alias in der Manifest-Datei: Ziel von method.xml (settingsActivity) und alten Intents. */
        const val SETTINGS_ALIAS = "com.chris.whisperbar.SettingsActivity"

        /**
         * Deep-Link fuer onCreate: nur beim echten Erststart. Nach Rotation/Prozess-Tod liefert
         * getIntent() denselben Deep-Link noch einmal — der per rememberSaveable wiederhergestellte
         * Back-Stack (Spec §0.2) darf dann nicht durch replaceAll() ueberschrieben werden.
         */
        fun initial(intent: Intent?, savedInstanceState: Bundle?): RouteRequest? =
            if (savedInstanceState == null) from(intent) else null

        fun from(intent: Intent?): RouteRequest? {
            if (intent == null) return null
            val route = intent.getStringExtra(AppNav.EXTRA_ROUTE)
                ?: (if (intent.component?.className == SETTINGS_ALIAS) AppNav.ROUTE_SETTINGS else null)
                ?: return null
            val step = intent.getIntExtra(AppNav.EXTRA_STEP, -1)
                .takeIf { it in SetupRouter.STEP_ENGINE..SetupRouter.STEP_KEYBOARD }
            return RouteRequest(route, step)
        }
    }
}
