package com.chris.whisperbar

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperbar.api.ApiNotConfiguredException
import com.chris.whisperbar.whisper.OfflineBackend
import com.chris.whisperbar.whisper.OfflineNotAvailableException
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress

/**
 * Robolectric-Tests fuer Backend-Wahl und den kompletten Diktat-Pfad gegen einen lokalen
 * "eigenen Server" (JDK-HttpServer): Multipart-Felder, kein Authorization-Header ohne Key,
 * Chat-Body fuer Ollama & Co., Politur danach.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TranscriptionEngineTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var prefs: Prefs
    private lateinit var server: HttpServer

    private var sttBody = ""
    private var sttAuth: String? = "unset"
    private var chatBody: String? = null
    private var sttResponse = """{"text":"also ähm hallo welt"}"""
    private var chatResponse = """{"choices":[{"message":{"content":"<think>ueberlegen</think>Hallo Welt."}}]}"""

    @Before fun setUp() {
        ctx.getSharedPreferences("whisperbar", Context.MODE_PRIVATE).edit().clear().commit()
        prefs = Prefs(ctx)
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/audio/transcriptions") { ex ->
            sttAuth = ex.requestHeaders.getFirst("Authorization")
            sttBody = ex.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            val out = sttResponse.toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.createContext("/v1/chat/completions") { ex ->
            chatBody = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val out = chatResponse.toByteArray()
            ex.sendResponseHeaders(200, out.size.toLong())
            ex.responseBody.use { it.write(out) }
        }
        server.start()
    }

    @After fun tearDown() {
        server.stop(0)
    }

    private fun useLocalServer() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        prefs.apiBaseUrl = "http://127.0.0.1:${server.address.port}/v1"
        prefs.apiModel = "whisper-1"
        prefs.language = "de"
        prefs.llmModel = "qwen3:8b"
    }

    private val speech = FloatArray(AudioUtils.SAMPLE_RATE) { 0.3f }

    @Test fun ohneEngineNichtKonfiguriert() {
        prefs.apiKey = "sk"
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        try {
            TranscriptionEngine.transcribe(ctx, speech)
            fail("ApiNotConfiguredException erwartet")
        } catch (e: ApiNotConfiguredException) {
            // erwartet
        }
    }

    @Test fun onlineOhneKeyBeiCloudAnbieterNichtKonfiguriert() {
        prefs.engine = Engine.ONLINE
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        prefs.apiKey = "sk"
        assertTrue(TranscriptionEngine.isConfigured(ctx))
    }

    @Test fun eigenerServerBrauchtNurEineUrl() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "custom"
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        prefs.apiBaseUrl = "http://192.168.1.50:8000/v1"
        assertTrue(TranscriptionEngine.isConfigured(ctx))
    }

    @Test fun offlineIstNochEinPlatzhalter() {
        prefs.engine = Engine.OFFLINE
        assertFalse(TranscriptionEngine.isConfigured(ctx))
        assertTrue(TranscriptionEngine.backend(prefs) is OfflineBackend)
        assertEquals("Offline", TranscriptionEngine.backend(prefs).label)
        try {
            TranscriptionEngine.transcribe(ctx, speech)
            fail("OfflineNotAvailableException erwartet")
        } catch (e: OfflineNotAvailableException) {
            // erwartet
        }
    }

    @Test fun onlineBackendTraegtDenAnbieternamen() {
        prefs.engine = Engine.ONLINE
        prefs.sttProviderId = "groq"
        assertEquals("Groq", TranscriptionEngine.backend(prefs).label)
    }

    @Test fun diktatGegenEigenenServerOhneKeyUndOhneKi() {
        useLocalServer()
        val text = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Also hallo welt", text) // Fuellwort weg, Satzanfang gross
        assertNull(sttAuth)
        assertTrue(sttBody.contains("name=\"model\"\r\n\r\nwhisper-1\r\n"))
        assertTrue(sttBody.contains("name=\"language\"\r\n\r\nde\r\n"))
        assertTrue(sttBody.contains("name=\"response_format\"\r\n\r\njson\r\n"))
        assertTrue(sttBody.contains("filename=\"audio.wav\""))
        assertTrue(sttBody.contains("RIFF"))
        assertFalse(sttBody.contains("name=\"prompt\""))
        assertNull(chatBody)
    }

    @Test fun diktatMitKiGlaettungGegenOllama() {
        useLocalServer()
        prefs.refineMode = RefineMode.POLISH
        prefs.apiPrompt = "Chris"
        val text = TranscriptionEngine.transcribe(ctx, speech)

        assertEquals("Hallo Welt.", text) // <think>-Block entfernt
        assertTrue(sttBody.contains("name=\"prompt\"\r\n\r\nChris\r\n"))
        val chat = JSONObject(chatBody!!)
        assertEquals("qwen3:8b", chat.getString("model"))
        assertEquals(0, chat.getInt("temperature"))
        assertEquals("none", chat.getString("reasoning_effort"))
        assertFalse(chat.has("max_completion_tokens"))
        val messages = chat.getJSONArray("messages")
        assertTrue(messages.getJSONObject(0).getString("content").contains("Zeichensetzung"))
        assertEquals("also ähm hallo welt", messages.getJSONObject(1).getString("content"))
    }

    @Test fun keyWirdAlsBearerGesendet() {
        useLocalServer()
        prefs.apiKey = "geheim"
        TranscriptionEngine.transcribe(ctx, speech)
        assertEquals("Bearer geheim", sttAuth)
    }

    @Test fun autoSpracheNimmtDieErkannteFuerDiePolitur() {
        useLocalServer()
        prefs.language = "auto"
        sttResponse = """{"text":"i um think so","language":"en"}"""
        assertEquals("I think so", TranscriptionEngine.transcribe(ctx, speech))
        assertFalse(sttBody.contains("name=\"language\""))
    }

    @Test fun leereAntwortBleibtLeer() {
        useLocalServer()
        sttResponse = """{"text":"   "}"""
        assertEquals("", TranscriptionEngine.transcribe(ctx, speech))
    }

    @Test fun effektiveSprache() {
        assertEquals("de", TranscriptionEngine.effectiveLanguage("de", "en"))
        assertEquals("en", TranscriptionEngine.effectiveLanguage("auto", "en"))
        assertEquals("auto", TranscriptionEngine.effectiveLanguage("auto", null))
        assertEquals("auto", TranscriptionEngine.effectiveLanguage("auto", " "))
    }
}
