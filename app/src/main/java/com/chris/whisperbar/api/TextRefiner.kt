package com.chris.whisperbar.api

import com.chris.whisperbar.RefineMode
import org.json.JSONObject

/**
 * Optionale zweite Runde: laesst ein Sprachmodell den Rohtext bearbeiten (glaetten,
 * verschoenern, zusammenfassen, in Absaetze gliedern). Kostet eine zusaetzliche
 * Anfrage und etwas Latenz — deshalb in den Einstellungen abschaltbar.
 *
 * Spricht POST /chat/completions des [ApiAccess] — das kann ein anderer Anbieter
 * als bei der Transkription sein (z. B. Groq-STT + Ollama-LLM).
 */
class TextRefiner(private val access: ApiAccess) {

    /**
     * Liefert den bearbeiteten Text. Bei leerer Eingabe, [RefineMode.OFF] oder leerer
     * Antwort wird der Originaltext zurueckgegeben — die Veredelung darf ein Diktat
     * niemals verschlucken.
     */
    fun refine(raw: String, language: String, mode: RefineMode, smartFillers: Boolean): String {
        if (raw.isBlank() || mode == RefineMode.OFF) return raw

        val payload = ChatPayload.build(
            access = access,
            systemPrompt = RefinePrompt.build(mode, german = language == "de", smartFillers = smartFillers),
            userText = raw,
        )

        val body = Http.post(
            url = Http.endpoint(access.baseUrl, "/chat/completions"),
            apiKey = access.apiKey,
            contentType = "application/json",
            readTimeoutMs = access.readTimeoutMs,
        ) { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }

        val text = JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.let { stripThinking(it) }
            ?.trim()

        return if (text.isNullOrBlank()) raw else text
    }

    companion object {
        // Qwen3 & Co. schreiben ihr Nachdenken als <think>…</think> in den Text, wenn
        // der Server reasoning_effort ignoriert. Das gehoert nie ins Diktat.
        private val THINK_BLOCK = Regex("(?s)^\\s*<think>.*?</think>\\s*")

        fun stripThinking(content: String): String = THINK_BLOCK.replace(content, "")
    }
}
