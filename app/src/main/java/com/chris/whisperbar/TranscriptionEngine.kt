package com.chris.whisperbar

import android.content.Context
import com.chris.whisperbar.api.ApiNotConfiguredException
import com.chris.whisperbar.api.ApiTranscriber
import com.chris.whisperbar.api.TextRefiner

/**
 * Einziger Weg vom Audio zum fertigen Text. Reihenfolge:
 *
 *  1. Stille am Anfang/Ende wegschneiden (kleinerer Upload)
 *  2. Transkription ueber die konfigurierte OpenAI-kompatible API
 *  3. optional: Sprachmodell glaettet Zeichensetzung/Grammatik
 *  4. Nachbearbeitung (Fuellwoerter, Gross-Schreibung, Whitespace)
 *
 * Blockierend — immer aus einem Hintergrund-Thread aufrufen.
 */
object TranscriptionEngine {

    /** Ob ueberhaupt diktiert werden kann (API-Key hinterlegt). */
    fun isConfigured(context: Context): Boolean =
        Prefs(context.applicationContext).apiKey.isNotBlank()

    /**
     * @throws ApiNotConfiguredException wenn kein API-Key gesetzt ist.
     * @throws com.chris.whisperbar.api.ApiNetworkException bei Netzproblemen.
     * @throws com.chris.whisperbar.api.ApiHttpException bei Fehlerstatus der API.
     */
    fun transcribe(context: Context, samples: FloatArray): String {
        val prefs = Prefs(context.applicationContext)
        if (prefs.apiKey.isBlank()) throw ApiNotConfiguredException()

        val language = prefs.language
        val trimmed = AudioUtils.trimSilence(samples)

        val raw = ApiTranscriber(
            baseUrl = prefs.apiBaseUrl,
            apiKey = prefs.apiKey,
            model = prefs.apiModel,
            prompt = prefs.apiPrompt,
        ).transcribe(trimmed, language)

        if (raw.isBlank()) return ""

        val refined = if (prefs.llmPolish) {
            TextRefiner(prefs.apiBaseUrl, prefs.apiKey, prefs.llmModel)
                .refine(raw, language, prefs.smartFillers)
        } else {
            raw
        }

        val options = PolishPlan.options(
            removeFillers = prefs.removeFillers,
            autoCapitalize = prefs.autoCapitalize,
            language = language,
            llmPolish = prefs.llmPolish,
            smartFillers = prefs.smartFillers,
        )
        return TextPolisher.polish(refined, options)
    }
}
