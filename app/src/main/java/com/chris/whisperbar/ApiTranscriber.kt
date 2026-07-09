package com.chris.whisperbar

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Geworfen, wenn der API-Modus aktiv ist, aber kein API-Key gesetzt wurde. */
class ApiNotConfiguredException : RuntimeException("Cloud-API aktiv, aber kein API-Key gesetzt")

/**
 * Transkribiert per OpenAI-kompatibler HTTP-API (POST /audio/transcriptions,
 * multipart/form-data). Funktioniert mit OpenAI, Groq und selbst gehosteten
 * Whisper-Servern — Base-URL, Modell und Key sind konfigurierbar.
 *
 * ACHTUNG: sendet das aufgenommene Audio an den Anbieter (nicht mehr offline).
 */
class ApiTranscriber(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : Transcriber {

    override fun transcribe(samples: FloatArray, language: String): String {
        if (apiKey.isBlank()) throw ApiNotConfiguredException()
        val wav = WavEncoder.encode(samples, AudioUtils.SAMPLE_RATE)
        val boundary = "----whisperbar${System.nanoTime()}"
        val endpoint = baseUrl.trimEnd('/') + "/audio/transcriptions"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        conn.outputStream.use { os ->
            fun field(name: String, value: String) {
                os.write("--$boundary\r\n".toByteArray())
                os.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            }
            field("model", model)
            field("response_format", "json")
            if (language.isNotBlank() && language != "auto") field("language", language)

            os.write("--$boundary\r\n".toByteArray())
            os.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".toByteArray(),
            )
            os.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
            os.write(wav)
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            throw RuntimeException("API-Fehler $code: ${body.take(200)}")
        }
        return JSONObject(body).optString("text", "").trim()
    }

    override fun release() { /* zustandslos */ }
}
