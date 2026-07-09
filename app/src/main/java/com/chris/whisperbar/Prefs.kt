package com.chris.whisperbar

import android.content.Context

/**
 * Duenner SharedPreferences-Wrapper fuer die App-Einstellungen.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("whisperbar", Context.MODE_PRIVATE)

    /** Erkennungssprache: "auto" oder ISO-Code ("de", "en", "es", "fr", "it"). */
    var language: String
        get() = sp.getString(KEY_LANGUAGE, "auto") ?: "auto"
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    var removeFillers: Boolean
        get() = sp.getBoolean(KEY_REMOVE_FILLERS, true)
        set(v) = sp.edit().putBoolean(KEY_REMOVE_FILLERS, v).apply()

    var autoCapitalize: Boolean
        get() = sp.getBoolean(KEY_AUTO_CAP, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_CAP, v).apply()

    /** Nach jedem Diktat ein Leerzeichen anhaengen (fluessiges Weiterdiktieren). */
    var trailingSpace: Boolean
        get() = sp.getBoolean(KEY_TRAILING_SPACE, true)
        set(v) = sp.edit().putBoolean(KEY_TRAILING_SPACE, v).apply()

    fun polishOptions() = PolishOptions(
        removeFillers = removeFillers,
        autoCapitalize = autoCapitalize,
        language = language,
    )

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_REMOVE_FILLERS = "remove_fillers"
        private const val KEY_AUTO_CAP = "auto_capitalize"
        private const val KEY_TRAILING_SPACE = "trailing_space"

        /** Sprachen fuer die Einstellungs-Auswahl. Erste = Default. */
        val LANGUAGES = listOf(
            "auto" to "Automatisch erkennen",
            "de" to "Deutsch",
            "en" to "Englisch",
            "es" to "Spanisch",
            "fr" to "Französisch",
            "it" to "Italienisch",
        )
    }
}
