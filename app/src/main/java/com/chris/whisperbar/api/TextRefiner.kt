package com.chris.whisperbar.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Baut die Anweisung fuer die Textveredelung. Rein (ohne Android/Netz), damit
 * JVM-unit-testbar — die Anweisung entscheidet ueber die Textqualitaet und darf
 * nicht unbemerkt verrutschen.
 */
object RefinePrompt {

    /**
     * @param german Anweisung auf Deutsch (bei deutscher Diktatsprache) statt Englisch.
     * @param smartFillers Wenn true, entscheidet das Modell selbst, welche Fuellwoerter,
     *   Versprecher und Wiederholungen weg koennen — statt einer festen Wortliste.
     */
    fun build(german: Boolean, smartFillers: Boolean): String {
        val sb = StringBuilder()
        if (german) {
            sb.append(
                "Du korrigierst diktierten Text. Setze Zeichensetzung, Gross- und " +
                    "Kleinschreibung sowie Absaetze richtig. Aendere den Inhalt nicht, " +
                    "uebersetze nicht, ergaenze nichts und kommentiere nicht. " +
                    "Antworte ausschliesslich mit dem korrigierten Text.",
            )
            if (smartFillers) {
                sb.append(
                    " Entferne ausserdem Fuellwoerter, Versprecher, Stotterer und " +
                        "unbeabsichtigte Wiederholungen, wenn sie erkennbar nicht gemeint " +
                        "waren. Im Zweifel behalte das Wort.",
                )
            }
        } else {
            sb.append(
                "You clean up dictated text. Fix punctuation, capitalisation and " +
                    "paragraphs. Do not change the meaning, do not translate, do not add " +
                    "anything and do not comment. Reply with the corrected text only.",
            )
            if (smartFillers) {
                sb.append(
                    " Also remove filler words, false starts, stutters and unintended " +
                        "repetitions where they were clearly not meant. When in doubt, keep it.",
                )
            }
        }
        return sb.toString()
    }
}

/**
 * Optionale zweite Runde: laesst ein Sprachmodell den Rohtext zu sauberen Saetzen
 * glaetten (Zeichensetzung, Grammatik, Absaetze). Kostet eine zusaetzliche Anfrage
 * und etwas Latenz — deshalb in den Einstellungen abschaltbar.
 *
 * Nutzt denselben OpenAI-kompatiblen Endpunkt wie die Transkription
 * (POST /chat/completions).
 */
class TextRefiner(
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) {

    /**
     * Liefert den geglaetteten Text. Bei leerer Eingabe oder leerer Antwort wird der
     * Originaltext zurueckgegeben — die Veredelung darf ein Diktat niemals verschlucken.
     */
    fun refine(raw: String, language: String, smartFillers: Boolean): String {
        if (raw.isBlank()) return raw
        if (apiKey.isBlank()) throw ApiNotConfiguredException()

        val payload = JSONObject().apply {
            put("model", model)
            put("temperature", 0)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", RefinePrompt.build(language == "de", smartFillers)),
                    )
                    put(JSONObject().put("role", "user").put("content", raw))
                },
            )
        }.toString()

        val body = Http.post(
            url = Http.endpoint(baseUrl, "/chat/completions"),
            apiKey = apiKey,
            contentType = "application/json",
        ) { os -> os.write(payload.toByteArray(Charsets.UTF_8)) }

        val text = JSONObject(body)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.trim()

        return if (text.isNullOrBlank()) raw else text
    }
}
