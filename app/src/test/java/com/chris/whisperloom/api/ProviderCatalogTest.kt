package com.chris.whisperloom.api

import com.chris.whisperloom.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer den Anbieter-Katalog. Er ist reine Daten — aber falsche Daten
 * heissen 400/401 beim Anbieter, und die Defaults muessen im Katalog existieren.
 */
class ProviderCatalogTest {

    @Test fun idsSindEindeutig() {
        val ids = ProviderCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun modellIdsSindJeAnbieterEindeutig() {
        for (p in ProviderCatalog.providers) {
            val stt = p.sttModels.map { it.id }
            val llm = p.llmModels.map { it.id }
            assertEquals("STT ${p.id}", stt.size, stt.toSet().size)
            assertEquals("LLM ${p.id}", llm.size, llm.toSet().size)
        }
    }

    @Test fun cloudAnbieterNutzenHttpsUndVerlangenEinenKey() {
        for (p in ProviderCatalog.providers.filter { !it.isCustom }) {
            assertTrue(p.id, p.baseUrl.startsWith("https://"))
            assertTrue(p.id, p.needsKey)
            assertTrue(p.id, p.keyUrl.startsWith("https://"))
            assertFalse(p.id, p.allowsHttp)
            assertEquals(p.id, 90, p.defaultReadTimeoutSec)
            p.sttPathOverride?.let { assertTrue(p.id, it.startsWith("https://")) }
        }
    }

    @Test fun eigenerServerIstOffenKonfigurierbar() {
        val c = ProviderCatalog.custom
        assertEquals("", c.baseUrl)
        assertFalse(c.needsKey)
        assertTrue(c.allowsHttp)
        assertEquals(600, c.defaultReadTimeoutSec)
        assertTrue(c.hasStt)
        assertTrue(c.hasLlm)
        assertEquals("", c.defaultSttModel)
    }

    @Test fun defaultsExistierenImKatalog() {
        val openai = ProviderCatalog.openai
        assertEquals(Prefs.DEFAULT_API_URL, openai.baseUrl)
        assertEquals(Prefs.DEFAULT_API_MODEL, openai.defaultSttModel)
        assertEquals(Prefs.DEFAULT_LLM_MODEL, openai.defaultLlmModel)
        assertNotNull(openai.sttModel(Prefs.DEFAULT_API_MODEL))
        assertNotNull(openai.llmModel(Prefs.DEFAULT_LLM_MODEL))
    }

    @Test fun groqDefaultsSindDieGratisModelle() {
        val groq = ProviderCatalog.byId("groq")
        assertEquals("whisper-large-v3-turbo", groq.defaultSttModel)
        assertEquals("openai/gpt-oss-20b", groq.defaultLlmModel)
    }

    @Test fun gptTranscribeSendetLanguagesArray() {
        assertEquals("languages[]", ProviderCatalog.openai.sttModel("gpt-transcribe")!!.languageField)
        assertEquals("language", ProviderCatalog.openai.sttModel("gpt-4o-transcribe")!!.languageField)
    }

    @Test fun altesDefaultModellIstAlsAuslaufGekennzeichnet() {
        assertTrue(ProviderCatalog.openai.sttModel("gpt-4o-transcribe")!!.label.contains("Auslauf"))
    }

    @Test fun reasoningModelleOhneTemperatureHabenEinenEffort() {
        for (p in ProviderCatalog.providers) {
            for (m in p.llmModels.filter { !it.temperatureSupported }) {
                assertNotNull("${p.id}/${m.id}", m.reasoningEffort)
            }
        }
    }

    @Test fun anbieterFlagsAusDerRecherche() {
        val mistral = ProviderCatalog.byId("mistral")
        assertFalse(mistral.sttSendsPrompt)
        assertFalse(mistral.sttSendsResponseFormat)
        val openrouter = ProviderCatalog.byId("openrouter")
        assertFalse(openrouter.sttSendsPrompt)
        assertTrue(openrouter.sttSendsResponseFormat)
        assertEquals(
            "https://api.deepinfra.com/v1/audio/transcriptions",
            ProviderCatalog.byId("deepinfra").sttPathOverride,
        )
        assertNull(ProviderCatalog.openai.sttPathOverride)
    }

    @Test fun anzeigeReihenfolgeDerDropdowns() {
        assertEquals(
            listOf("openai", "groq", "mistral", "together", "deepinfra", "openrouter", "custom"),
            ProviderCatalog.sttProviders.map { it.id },
        )
        assertEquals(
            listOf("openai", "groq", "mistral", "openrouter", "anthropic", "gemini", "deepseek", "custom"),
            ProviderCatalog.llmProviders.map { it.id },
        )
    }

    @Test fun unbekannteIdFaelltAufOpenAiZurueck() {
        assertEquals("openai", ProviderCatalog.byId("").id)
        assertEquals("openai", ProviderCatalog.byId("gibtsnicht").id)
        assertNull(ProviderCatalog.find("gibtsnicht"))
        assertEquals("groq", ProviderCatalog.byId("groq").id)
    }
}
