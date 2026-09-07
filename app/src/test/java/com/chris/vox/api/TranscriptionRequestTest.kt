package com.chris.vox.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Multipart-Felder je Anbieter/Modell. Ein falsches Feld heisst
 * 400/422 beim Anbieter — und das faellt sonst erst live auf.
 */
class TranscriptionRequestTest {

    private fun stt(provider: String, model: String = "", url: String = "") =
        AccessResolver.resolveStt(provider, url, "k", model, 0)

    private fun names(fields: List<Pair<String, String>>) = fields.map { it.first }

    @Test fun gptTranscribeSendetLanguagesArray() {
        val f = TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "de", "Namen: Chris")
        assertEquals(listOf("model", "response_format", "languages[]", "prompt"), names(f))
        assertTrue(f.contains("languages[]" to "de"))
        assertTrue(f.contains("prompt" to "Namen: Chris"))
        assertFalse(names(f).contains("language"))
    }

    @Test fun altesOpenAiModellSendetLanguage() {
        val f = TranscriptionRequest.fields(stt("openai", "gpt-4o-transcribe"), "de", "")
        assertEquals(listOf("model" to "gpt-4o-transcribe", "response_format" to "json", "language" to "de"), f)
    }

    @Test fun groqBekommtAlleFelder() {
        val f = TranscriptionRequest.fields(stt("groq"), "en", "ctx")
        assertEquals(listOf("model", "response_format", "language", "prompt"), names(f))
        assertEquals("whisper-large-v3-turbo", f[0].second)
    }

    @Test fun mistralBekommtWederPromptNochResponseFormat() {
        val f = TranscriptionRequest.fields(stt("mistral"), "de", "ctx")
        assertEquals(listOf("model" to "voxtral-mini-latest", "language" to "de"), f)
    }

    @Test fun openRouterBekommtKeinenPrompt() {
        val f = TranscriptionRequest.fields(stt("openrouter"), "de", "ctx")
        assertEquals(listOf("model", "response_format", "language"), names(f))
    }

    @Test fun autoLaesstDieSpracheWeg() {
        assertFalse(names(TranscriptionRequest.fields(stt("openai", "gpt-transcribe"), "auto", "")).any { it.startsWith("language") })
        assertFalse(names(TranscriptionRequest.fields(stt("groq"), "", "")).contains("language"))
    }

    @Test fun leererPromptWirdNichtGesendet() {
        assertFalse(names(TranscriptionRequest.fields(stt("groq"), "de", "   ")).contains("prompt"))
    }

    @Test fun eigenerServerMitFreiemModell() {
        val f = TranscriptionRequest.fields(stt("custom", "Systran/faster-whisper-medium", "http://s:8000/v1"), "de", "ctx")
        assertEquals(
            listOf(
                "model" to "Systran/faster-whisper-medium", "response_format" to "json",
                "language" to "de", "prompt" to "ctx",
            ),
            f,
        )
    }

    @Test fun eigenerServerOhneModellSendetKeinLeeresModelFeld() {
        // Review API-7: custom hat keinen Modell-Default; model="" quittieren speaches/LocalAI mit 422.
        val f = TranscriptionRequest.fields(stt("custom", "", "http://s:8000/v1"), "de", "")
        assertEquals(listOf("response_format" to "json", "language" to "de"), f)
        assertFalse(names(TranscriptionRequest.fields(stt("custom", "   ", "http://s:8000/v1"), "auto", "")).contains("model"))
    }

    @Test fun endpunktAusBaseUrlOderOverride() {
        assertEquals(
            "https://api.openai.com/v1/audio/transcriptions",
            TranscriptionRequest.url(stt("openai")),
        )
        assertEquals(
            "http://s:8000/v1/audio/transcriptions",
            TranscriptionRequest.url(stt("custom", url = "http://s:8000/v1/")),
        )
        assertEquals(
            "https://api.deepinfra.com/v1/audio/transcriptions",
            TranscriptionRequest.url(stt("deepinfra")),
        )
    }
}
