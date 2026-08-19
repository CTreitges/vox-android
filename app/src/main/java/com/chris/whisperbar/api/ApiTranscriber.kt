package com.chris.whisperbar.api

import com.chris.whisperbar.AudioUtils
import com.chris.whisperbar.Transcriber
import com.chris.whisperbar.WavEncoder
import org.json.JSONObject

/**
 * Transkribiert per OpenAI-kompatibler HTTP-API (POST /audio/transcriptions,
 * multipart/form-data). Funktioniert mit OpenAI, Groq und selbst gehosteten
 * Whisper-Servern — Base-URL, Modell und Key sind konfigurierbar.
 *
 * Das aufgenommene Audio wird dabei an den Anbieter gesendet.
 */
class ApiTranscriber(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    /**
     * Optionaler Kontext fuer die Erkennung (Eigennamen, Fachbegriffe, Stil der
     * Zeichensetzung). Die API nimmt ihn als `prompt` entgegen; er kostet nichts
     * extra und verbessert vor allem Namen und Schreibweisen spuerbar.
     */
    private val prompt: String = "",
) : Transcriber {

    override fun transcribe(samples: FloatArray, language: String): String {
        if (apiKey.isBlank()) throw ApiNotConfiguredException()
        val wav = WavEncoder.encode(samples, AudioUtils.SAMPLE_RATE)
        val boundary = "----whisperbar${System.nanoTime()}"

        val body = Http.post(
            url = Http.endpoint(baseUrl, "/audio/transcriptions"),
            apiKey = apiKey,
            contentType = "multipart/form-data; boundary=$boundary",
        ) { os ->
            fun field(name: String, value: String) {
                os.write("--$boundary\r\n".toByteArray())
                os.write(
                    "Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(),
                )
            }
            field("model", model)
            field("response_format", "json")
            if (language.isNotBlank() && language != "auto") field("language", language)
            if (prompt.isNotBlank()) field("prompt", prompt)

            os.write("--$boundary\r\n".toByteArray())
            os.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".toByteArray(),
            )
            os.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
            os.write(wav)
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }

        return JSONObject(body).optString("text", "").trim()
    }

    override fun release() { /* zustandslos */ }
}
