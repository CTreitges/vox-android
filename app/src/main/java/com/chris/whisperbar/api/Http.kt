package com.chris.whisperbar.api

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimaler HTTP-Helfer fuer die beiden OpenAI-kompatiblen Aufrufe der App
 * (Transkription + optionale Textveredelung). Bewusst auf HttpURLConnection statt
 * einer HTTP-Bibliothek — die App haelt sich frei von Dritt-Abhaengigkeiten.
 */
internal object Http {

    const val CONNECT_TIMEOUT_MS = 15_000
    const val READ_TIMEOUT_MS = 90_000

    fun endpoint(baseUrl: String, path: String): String =
        baseUrl.trim().trimEnd('/') + path

    /**
     * Fuehrt den Request aus und liefert den Antwort-Body.
     *
     * @param write schreibt den Request-Body.
     * @throws ApiNetworkException wenn die Verbindung scheitert.
     * @throws ApiHttpException bei Status != 2xx.
     */
    fun post(
        url: String,
        apiKey: String,
        contentType: String,
        write: (java.io.OutputStream) -> Unit,
    ): String {
        val conn = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", contentType)
            }
        } catch (e: IOException) {
            throw ApiNetworkException(e)
        }

        try {
            conn.outputStream.use(write)
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw ApiHttpException(code, errorDetail(body))
            return body
        } catch (e: IOException) {
            throw ApiNetworkException(e)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Zieht die lesbare Meldung aus einer Fehlerantwort ({"error":{"message":…}}),
     * damit im UI nicht roher JSON landet.
     */
    private fun errorDetail(body: String): String {
        val fallback = body.take(200)
        if (body.isBlank()) return "keine Antwort"
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message")
                ?.takeIf { it.isNotBlank() } ?: fallback
        } catch (e: org.json.JSONException) {
            fallback
        }
    }
}
