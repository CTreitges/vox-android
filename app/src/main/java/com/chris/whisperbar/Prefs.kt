package com.chris.whisperbar

import android.content.Context

/**
 * Duenner SharedPreferences-Wrapper fuer die App-Einstellungen.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("whisperbar", Context.MODE_PRIVATE)

    /** Erkennungssprache: "auto" oder ISO-Code ("de", "en", "es", "fr", "it"). Default: Deutsch. */
    var language: String
        get() = sp.getString(KEY_LANGUAGE, "de") ?: "de"
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    // --- Transkriptions-API --------------------------------------------------

    var apiBaseUrl: String
        get() = sp.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        set(v) = sp.edit().putString(KEY_API_URL, v).apply()

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_KEY, v).apply()

    var apiModel: String
        get() = sp.getString(KEY_API_MODEL, DEFAULT_API_MODEL) ?: DEFAULT_API_MODEL
        set(v) = sp.edit().putString(KEY_API_MODEL, v).apply()

    /**
     * Kontext fuer die Erkennung (Eigennamen, Fachbegriffe, gewuenschte Schreibweisen).
     * Geht als `prompt` an die API — kostet nichts extra.
     */
    var apiPrompt: String
        get() = sp.getString(KEY_API_PROMPT, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_PROMPT, v).apply()

    // --- Nachbearbeitung -----------------------------------------------------

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

    /** Zweiter API-Aufruf: Sprachmodell glaettet Zeichensetzung und Grammatik. */
    var llmPolish: Boolean
        get() = sp.getBoolean(KEY_LLM_POLISH, false)
        set(v) = sp.edit().putBoolean(KEY_LLM_POLISH, v).apply()

    var llmModel: String
        get() = sp.getString(KEY_LLM_MODEL, DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(v) = sp.edit().putString(KEY_LLM_MODEL, v).apply()

    /**
     * Statt fester Wortliste entscheidet das Sprachmodell selbst, welche Fuellwoerter,
     * Versprecher und Wiederholungen weg koennen. Wirkt nur mit [llmPolish].
     */
    var smartFillers: Boolean
        get() = sp.getBoolean(KEY_SMART_FILLERS, false)
        set(v) = sp.edit().putBoolean(KEY_SMART_FILLERS, v).apply()

    // --- Schwebender Knopf ---------------------------------------------------

    /** Zuletzt gemerkte Position des schwebenden Knopfs (Bildschirm-Pixel). */
    var floatX: Int
        get() = sp.getInt(KEY_FLOAT_X, DEFAULT_FLOAT_X)
        set(v) = sp.edit().putInt(KEY_FLOAT_X, v).apply()

    var floatY: Int
        get() = sp.getInt(KEY_FLOAT_Y, DEFAULT_FLOAT_Y)
        set(v) = sp.edit().putInt(KEY_FLOAT_Y, v).apply()

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_API_URL = "api_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_API_PROMPT = "api_prompt"
        private const val KEY_REMOVE_FILLERS = "remove_fillers"
        private const val KEY_AUTO_CAP = "auto_capitalize"
        private const val KEY_TRAILING_SPACE = "trailing_space"
        private const val KEY_LLM_POLISH = "llm_polish"
        private const val KEY_LLM_MODEL = "llm_model"
        private const val KEY_SMART_FILLERS = "smart_fillers"
        private const val KEY_FLOAT_X = "float_x"
        private const val KEY_FLOAT_Y = "float_y"

        const val DEFAULT_API_URL = "https://api.openai.com/v1"

        /**
         * Aktuelles Transkriptionsmodell statt des alten whisper-1 — deutlich bessere
         * Zeichensetzung und Eigennamen bei gleichem Endpunkt. Frei aenderbar, damit
         * Groq & Co. (z. B. whisper-large-v3-turbo) weiter funktionieren.
         */
        const val DEFAULT_API_MODEL = "gpt-4o-transcribe"

        /** Guenstiges Modell fuer die optionale Textveredelung. */
        const val DEFAULT_LLM_MODEL = "gpt-4o-mini"

        /** Startposition des schwebenden Knopfs, wenn noch nichts verschoben wurde. */
        const val DEFAULT_FLOAT_X = 24
        const val DEFAULT_FLOAT_Y = 320

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
