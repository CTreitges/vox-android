package com.chris.whisperloom.api

import com.chris.whisperloom.TranscriptResult
import com.chris.whisperloom.WavEncoder
import org.json.JSONObject

/**
 * Transkribiert per OpenAI-kompatibler HTTP-API (POST /audio/transcriptions,
 * multipart/form-data). Funktioniert mit OpenAI, Groq, Mistral & Co. und selbst
 * gehosteten Whisper-Servern — Endpunkt, Felder und Key kommen aus dem [ApiAccess].
 *
 * Hochgeladen wird immer WAV: die dokumentierten Formate der API sind mp3, mp4, mpeg,
 * mpga, m4a, wav und webm — ogg/opus (WhatsApp-Sprachnachrichten) ist NICHT dabei.
 * Deshalb wird geteiltes Audio vorher lokal dekodiert, statt es durchzureichen.
 *
 * Das aufgenommene Audio wird an den Anbieter gesendet.
 */
class ApiTranscriber(
    private val access: ApiAccess,
    /**
     * Optionaler Kontext fuer die Erkennung (Eigennamen, Fachbegriffe, Stil der
     * Zeichensetzung). Die API nimmt ihn als `prompt` entgegen; er kostet nichts
     * extra und verbessert vor allem Namen und Schreibweisen spuerbar.
     */
    private val prompt: String = "",
) {

    /**
     * @throws ApiNotConfiguredException wenn der Anbieter einen Key braucht und keiner da ist.
     */
    fun transcribe(upload: WavUpload, language: String): TranscriptResult {
        if (access.provider.needsKey && access.apiKey.isBlank()) throw ApiNotConfiguredException()
        val boundary = "----whisperloom${System.nanoTime()}"

        val body = Http.post(
            url = TranscriptionRequest.url(access),
            apiKey = access.apiKey,
            contentType = "multipart/form-data; boundary=$boundary",
            readTimeoutMs = access.readTimeoutMs,
        ) { os ->
            for ((name, value) in TranscriptionRequest.fields(access, language, prompt)) {
                os.write("--$boundary\r\n".toByteArray())
                os.write(
                    "Content-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(),
                )
            }

            os.write("--$boundary\r\n".toByteArray())
            os.write(
                "Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"\r\n".toByteArray(),
            )
            os.write("Content-Type: audio/wav\r\n\r\n".toByteArray())
            os.write(WavEncoder.header(upload.pcmByteCount, upload.sampleRate))
            upload.writePcm(os)
            os.write("\r\n--$boundary--\r\n".toByteArray())
        }

        val json = JSONObject(body)
        return TranscriptResult(
            text = json.optString("text", "").trim(),
            // Nur verbose_json/eigene Server liefern die erkannte Sprache; sonst null.
            detectedLanguage = json.optString("language", "").takeIf { it.isNotBlank() },
        )
    }
}
