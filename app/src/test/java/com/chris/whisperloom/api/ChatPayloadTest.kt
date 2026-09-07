package com.chris.whisperloom.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Sampling-Entscheidung des Chat-Bodys. Reasoning-Modelle lehnen
 * `temperature` mit 400 ab; Ollama/Qwen3 denken ohne `reasoning_effort: none` minutenlang.
 */
class ChatPayloadTest {

    private fun llm(sttProvider: String, llmProvider: String, model: String, url: String = ""): ApiAccess {
        val stt = AccessResolver.resolveStt(sttProvider, url, "k", "", 0)
        return AccessResolver.resolveLlm(stt, llmProvider, "", "", model)
    }

    @Test fun klassischesModellBekommtNurTemperature() {
        val s = ChatPayload.sampling(llm("openai", "same", "gpt-4o-mini"))
        assertEquals(ChatPayload.Sampling(temperature = 0), s)
    }

    @Test fun openAiReasoningModellOhneTemperature() {
        val s = ChatPayload.sampling(llm("openai", "same", "gpt-5.6-luna"))
        assertNull(s.temperature)
        assertEquals("none", s.reasoningEffort)
        assertEquals(ChatPayload.MAX_COMPLETION_TOKENS, s.maxCompletionTokens)
    }

    @Test fun altesGpt5MiniNimmtMinimal() {
        val s = ChatPayload.sampling(llm("openai", "same", "gpt-5-mini"))
        assertEquals(ChatPayload.Sampling(temperature = null, reasoningEffort = "minimal", maxCompletionTokens = 4096), s)
    }

    @Test fun groqGptOssBekommtTemperatureUndLow() {
        val s = ChatPayload.sampling(llm("groq", "same", "openai/gpt-oss-20b"))
        assertEquals(ChatPayload.Sampling(temperature = 0, reasoningEffort = "low"), s)
    }

    @Test fun geminiFlashLiteSchaltetDenkenAb() {
        val s = ChatPayload.sampling(llm("openai", "gemini", "gemini-2.5-flash-lite"))
        assertEquals(ChatPayload.Sampling(temperature = 0, reasoningEffort = "none"), s)
    }

    @Test fun eigenerServerImmerReasoningNone() {
        // Ollama + Qwen3: ohne "none" landet <think>…</think> im Text.
        val s = ChatPayload.sampling(llm("custom", "same", "qwen3:8b", "http://s:11434/v1"))
        assertEquals(ChatPayload.Sampling(temperature = 0, reasoningEffort = "none"), s)
    }

    @Test fun unbekanntesCloudModellBleibtKlassisch() {
        val s = ChatPayload.sampling(llm("openai", "same", "irgendwas-neues"))
        assertEquals(ChatPayload.Sampling(temperature = 0), s)
    }
}
