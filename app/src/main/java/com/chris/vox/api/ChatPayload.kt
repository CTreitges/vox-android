package com.chris.vox.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Request-Body fuer POST /chat/completions. Die Sampling-Entscheidung ist rein
 * (JVM-unit-testbar): Reasoning-Modelle (OpenAI gpt-5.x) lehnen `temperature` ab und
 * wollen `reasoning_effort` + `max_completion_tokens`; Ollama/Qwen3 denken ohne
 * `reasoning_effort: "none"` erst minutenlang nach.
 */
object ChatPayload {

    /** Reicht fuer jedes Diktat; verhindert Endlos-Ausgaben bei Reasoning-Modellen. */
    const val MAX_COMPLETION_TOKENS = 4096

    data class Sampling(
        val temperature: Int? = null,
        val reasoningEffort: String? = null,
        val maxCompletionTokens: Int? = null,
    )

    fun sampling(access: ApiAccess): Sampling {
        val option = access.modelOption
        // Unbekanntes Modell (frei eingetippt): klassisches Verhalten mit temperature 0.
        val temperatureOk = option?.temperatureSupported ?: true
        return Sampling(
            temperature = if (temperatureOk) 0 else null,
            reasoningEffort = if (access.provider.isCustom) "none" else option?.reasoningEffort,
            maxCompletionTokens = if (temperatureOk) null else MAX_COMPLETION_TOKENS,
        )
    }

    fun build(access: ApiAccess, systemPrompt: String, userText: String): String {
        val s = sampling(access)
        return JSONObject().apply {
            put("model", access.model)
            s.temperature?.let { put("temperature", it) }
            s.reasoningEffort?.let { put("reasoning_effort", it) }
            s.maxCompletionTokens?.let { put("max_completion_tokens", it) }
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userText)),
            )
        }.toString()
    }
}
