package com.chris.whisperbar.api

/**
 * Was an POST /audio/transcriptions geht — Endpunkt und Multipart-Felder je
 * Anbieter/Modell. Rein (ohne Netz), damit JVM-unit-testbar: ein falsches Feld
 * heisst 400/422 beim Anbieter, und das faellt sonst erst live auf.
 */
object TranscriptionRequest {

    const val PATH = "/audio/transcriptions"

    /** DeepInfra legt Audio nicht unter der Chat-Base-URL ab -> Override aus dem Katalog. */
    fun url(access: ApiAccess): String =
        access.provider.sttPathOverride ?: Http.endpoint(access.baseUrl, PATH)

    /**
     * Textfelder in Sendereihenfolge (die Datei kommt zuletzt, siehe [ApiTranscriber]).
     *
     * - `languages[]` statt `language` bei OpenAI gpt-transcribe (Array-Feld).
     * - keine Sprache bei "auto" (der Anbieter erkennt sie selbst).
     * - `prompt`/`response_format` nur, wenn der Anbieter sie kennt (Mistral, OpenRouter nicht).
     */
    fun fields(access: ApiAccess, language: String, prompt: String): List<Pair<String, String>> {
        val out = mutableListOf("model" to access.model)
        if (access.provider.sttSendsResponseFormat) out += "response_format" to "json"
        val lang = language.trim()
        if (lang.isNotEmpty() && lang != "auto") {
            out += (access.modelOption?.languageField ?: "language") to lang
        }
        if (access.provider.sttSendsPrompt && prompt.isNotBlank()) out += "prompt" to prompt.trim()
        return out
    }
}
