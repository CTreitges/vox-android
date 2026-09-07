package com.chris.vox.api

/**
 * Aufgeloester Zugang zu einem OpenAI-kompatiblen Endpunkt — alles, was ein Aufruf
 * braucht. [modelOption] ist null bei frei eingetippten Modell-IDs (eigener Server,
 * "Eigenes Modell…"); dann gelten die konservativen Defaults.
 */
data class ApiAccess(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val readTimeoutMs: Int,
    val provider: Provider,
    val modelOption: ModelOption?,
)

/**
 * Leitet aus den rohen Einstellungswerten den Zugang ab. Rein (ohne Android), damit
 * die Fallback-Regeln JVM-unit-testbar sind — hier entscheidet sich, gegen welchen
 * Server mit welchem Key gesprochen wird.
 */
object AccessResolver {

    /** Wert von `llm_provider`, der den Transkriptions-Zugang wiederverwendet. */
    const val LLM_SAME = "same"

    /**
     * @param readTimeoutSec 0 = Anbieter-Default (90 s, eigener Server 600 s).
     */
    fun resolveStt(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        readTimeoutSec: Int = 0,
    ): ApiAccess {
        val provider = ProviderCatalog.byId(providerId)
        val modelId = model.trim().ifBlank { provider.defaultSttModel }
        return ApiAccess(
            baseUrl = baseUrl.trim().ifBlank { provider.baseUrl },
            apiKey = apiKey.trim(),
            model = modelId,
            readTimeoutMs = timeoutMs(readTimeoutSec, provider),
            provider = provider,
            modelOption = provider.sttModel(modelId),
        )
    }

    /**
     * Textverbesserung: `same` (oder leer) uebernimmt den Transkriptions-Zugang komplett.
     * Ist derselbe Anbieter explizit gewaehlt, fuellen leere Felder sich aus dem
     * Transkriptions-Zugang; ein anderer Anbieter bekommt seine eigenen Defaults —
     * der OpenAI-Key darf nie versehentlich an Groq gehen.
     */
    fun resolveLlm(
        stt: ApiAccess,
        providerId: String,
        baseUrl: String,
        apiKey: String,
        model: String,
    ): ApiAccess {
        val same = providerId.isBlank() || providerId == LLM_SAME
        val provider = if (same) stt.provider else ProviderCatalog.byId(providerId)
        val sameProvider = provider.id == stt.provider.id
        val modelId = model.trim().ifBlank { provider.defaultLlmModel }

        val url = when {
            same -> stt.baseUrl
            baseUrl.isNotBlank() -> baseUrl.trim()
            sameProvider -> stt.baseUrl
            else -> provider.baseUrl
        }
        val key = when {
            same -> stt.apiKey
            apiKey.isNotBlank() -> apiKey.trim()
            sameProvider -> stt.apiKey
            else -> ""
        }
        return ApiAccess(
            baseUrl = url,
            apiKey = key,
            model = modelId,
            readTimeoutMs = if (sameProvider) stt.readTimeoutMs else timeoutMs(0, provider),
            provider = provider,
            modelOption = provider.llmModel(modelId),
        )
    }

    private fun timeoutMs(seconds: Int, provider: Provider): Int =
        (if (seconds > 0) seconds else provider.defaultReadTimeoutSec) * 1000
}
