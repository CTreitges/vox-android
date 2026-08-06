package com.chris.whisperbar

import android.content.Context
import android.content.SharedPreferences

/**
 * Duenner SharedPreferences-Wrapper fuer die App-Einstellungen.
 *
 * Prozessweiter Singleton ([get]): Tastatur, schwebender Knopf und die Engine lesen
 * dieselbe Instanz. Frueher wurde pro Diktat mehrfach ein neues `Prefs` gebaut —
 * jedes Mal ein `getSharedPreferences`-Aufruf im heissen Pfad.
 */
class Prefs private constructor(private val sp: SharedPreferences) {

    /** Erkennungssprache: "auto" oder ISO-Code ("de", "en", "es", "fr", "it"). Default: Deutsch. */
    var language: String
        get() = sp.getString(KEY_LANGUAGE, "de") ?: "de"
        set(v) = sp.edit().putString(KEY_LANGUAGE, v).apply()

    /** Gewaehltes Whisper-Modell. Default: SMALL (gebuendelt). */
    var model: WhisperModel
        get() = WhisperModel.fromId(sp.getString(KEY_MODEL, WhisperModel.SMALL.id))
        set(v) = sp.edit().putString(KEY_MODEL, v.id).apply()

    /** Cloud-API statt On-Device nutzen (bessere Qualität, SENDET Audio an den Anbieter). */
    var useApi: Boolean
        get() = sp.getBoolean(KEY_USE_API, false)
        set(v) = sp.edit().putBoolean(KEY_USE_API, v).apply()

    var apiBaseUrl: String
        get() = sp.getString(KEY_API_URL, DEFAULT_API_URL) ?: DEFAULT_API_URL
        set(v) = sp.edit().putString(KEY_API_URL, v).apply()

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_KEY, v).apply()

    var apiModel: String
        get() = sp.getString(KEY_API_MODEL, DEFAULT_API_MODEL) ?: DEFAULT_API_MODEL
        set(v) = sp.edit().putString(KEY_API_MODEL, v).apply()

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

    // --- Bedienung ----------------------------------------------------------

    /**
     * Freihand-Modus: einmal tippen startet, die Aufnahme endet nach einer Sprechpause
     * von selbst. Aus = klassisches Halten (Push-to-talk). Halten funktioniert immer,
     * auch im Freihand-Modus.
     */
    var handsFree: Boolean
        get() = sp.getBoolean(KEY_HANDS_FREE, true)
        set(v) = sp.edit().putBoolean(KEY_HANDS_FREE, v).apply()

    /** Pausenlaenge in ms, nach der das Freihand-Diktat endet. 0 = nie automatisch. */
    var autoStopMs: Int
        get() = sp.getInt(KEY_AUTO_STOP_MS, DEFAULT_AUTO_STOP_MS)
        set(v) = sp.edit().putInt(KEY_AUTO_STOP_MS, v).apply()

    /** Kurzes Vibrieren bei Start/Ende/Fehler. */
    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(v) = sp.edit().putBoolean(KEY_HAPTICS, v).apply()

    /**
     * Turbo: deckelt den Whisper-Encoder auf die tatsaechliche Audiolaenge statt immer
     * 30 s zu rechnen. Kurze Diktate werden dadurch ein Vielfaches schneller.
     */
    var fastMode: Boolean
        get() = sp.getBoolean(KEY_FAST_MODE, true)
        set(v) = sp.edit().putBoolean(KEY_FAST_MODE, v).apply()

    // --- Position des schwebenden Knopfs ------------------------------------

    var bubbleX: Int
        get() = sp.getInt(KEY_BUBBLE_X, Int.MIN_VALUE)
        set(v) = sp.edit().putInt(KEY_BUBBLE_X, v).apply()

    var bubbleY: Int
        get() = sp.getInt(KEY_BUBBLE_Y, Int.MIN_VALUE)
        set(v) = sp.edit().putInt(KEY_BUBBLE_Y, v).apply()

    /** Schwebenden Knopf nach einem Neustart automatisch wieder starten. */
    var bubbleAutoStart: Boolean
        get() = sp.getBoolean(KEY_BUBBLE_AUTOSTART, false)
        set(v) = sp.edit().putBoolean(KEY_BUBBLE_AUTOSTART, v).apply()

    fun polishOptions() = PolishOptions(
        removeFillers = removeFillers,
        autoCapitalize = autoCapitalize,
        language = language,
    )

    /**
     * Alle fuer ein Diktat relevanten Werte in einem Rutsch — wird einmal beim Start
     * der Aufnahme gelesen, damit der Hintergrund-Thread danach nichts mehr aus den
     * Preferences ziehen muss.
     */
    fun dictationSettings() = DictationSettings(
        language = language,
        polish = polishOptions(),
        trailingSpace = trailingSpace,
        handsFree = handsFree,
        autoStopMs = autoStopMs,
        haptics = haptics,
    )

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_MODEL = "model"
        private const val KEY_USE_API = "use_api"
        private const val KEY_API_URL = "api_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_MODEL = "api_model"
        private const val KEY_REMOVE_FILLERS = "remove_fillers"
        private const val KEY_AUTO_CAP = "auto_capitalize"
        private const val KEY_TRAILING_SPACE = "trailing_space"
        private const val KEY_HANDS_FREE = "hands_free"
        private const val KEY_AUTO_STOP_MS = "auto_stop_ms"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_FAST_MODE = "fast_mode"
        private const val KEY_BUBBLE_X = "bubble_x"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val KEY_BUBBLE_AUTOSTART = "bubble_autostart"

        const val DEFAULT_API_URL = "https://api.openai.com/v1"
        const val DEFAULT_API_MODEL = "whisper-1"
        const val DEFAULT_AUTO_STOP_MS = 1_200

        /** Auswahl fuer die Pausen-Automatik: Anzeigename -> ms. */
        val AUTO_STOP_CHOICES = listOf(
            "Sofort (0,8 s)" to 800,
            "Normal (1,2 s)" to 1_200,
            "Entspannt (2 s)" to 2_000,
            "Aus — selbst beenden" to 0,
        )

        @Volatile private var instance: Prefs? = null

        /** Prozessweite Instanz; teilt eine einzige SharedPreferences-Referenz. */
        fun get(context: Context): Prefs = instance ?: synchronized(this) {
            instance ?: Prefs(
                context.applicationContext
                    .getSharedPreferences("whisperbar", Context.MODE_PRIVATE),
            ).also { instance = it }
        }

        /** Sprachen fuer die Einstellungs-Auswahl. Erste = Default. */
        val LANGUAGES = listOf(
            "auto" to "Automatisch",
            "de" to "Deutsch",
            "en" to "Englisch",
            "es" to "Spanisch",
            "fr" to "Französisch",
            "it" to "Italienisch",
        )
    }
}

/** Unveraenderliche Momentaufnahme der Diktat-Einstellungen. */
data class DictationSettings(
    val language: String,
    val polish: PolishOptions,
    val trailingSpace: Boolean,
    val handsFree: Boolean,
    val autoStopMs: Int,
    val haptics: Boolean,
)
