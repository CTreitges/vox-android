package com.chris.whisperbar

import java.util.regex.Pattern

/**
 * Optionen fuer die Nachbearbeitung ("polish") eines rohen Whisper-Transkripts.
 * Rein datengetrieben, damit [TextPolisher] frei von Android-Abhaengigkeiten und
 * auf der JVM unit-testbar bleibt.
 */
data class PolishOptions(
    val removeFillers: Boolean = true,
    val autoCapitalize: Boolean = true,
    /** "auto" | "de" | "en" | ... — steuert die Fuellwort-Liste. */
    val language: String = "auto",
)

/**
 * Entscheidet, was die Regex-Nachbearbeitung noch tun soll. Rein (ohne Android),
 * damit JVM-unit-testbar.
 */
object PolishPlan {

    /**
     * Wenn ein Sprachmodell selbst ueber Fuellwoerter entscheidet, darf die feste
     * Wortliste nicht nochmal daruebergehen — sonst wuerde zweimal gefiltert und die
     * Entscheidung der KI ("im Zweifel behalten") wieder ausgehebelt. Die restliche
     * Normalisierung (Whitespace, Satzzeichen, Gross-Schreibung) laeuft weiter.
     */
    fun options(
        removeFillers: Boolean,
        autoCapitalize: Boolean,
        language: String,
        llmPolish: Boolean,
        smartFillers: Boolean,
    ): PolishOptions {
        val aiDecidesFillers = llmPolish && smartFillers
        return PolishOptions(
            removeFillers = removeFillers && !aiDecidesFillers,
            autoCapitalize = autoCapitalize,
            language = language,
        )
    }
}

/**
 * Wandelt rohe Whisper-Ausgabe in sauberen Text um: Whitespace normalisieren,
 * Fuellwoerter entfernen, Leerzeichen vor Satzzeichen fixen, Saetze gross schreiben.
 *
 * Bewusst konservativ: nur eindeutige Disfluenzen ("aehm", "um") werden entfernt,
 * keine echten Woerter — sonst zerstoert man legitime Eingaben.
 */
object TextPolisher {

    // Nur eindeutige Fuellsilben. Echte Woerter wie "like"/"halt" bleiben drin,
    // weil sie im Fliesstext meist gewollt sind (false-positive-Vermeidung).
    private val FILLERS: Map<String, List<String>> = mapOf(
        "de" to listOf("ähm", "äh", "öhm", "ähem", "hmm", "öh"),
        "en" to listOf("um", "uh", "uhm", "erm", "hmm"),
        "es" to listOf("eh", "este", "mmm"),
        "fr" to listOf("euh", "hmm"),
        "it" to listOf("ehm", "mmm"),
    )

    private val MULTI_WS = Pattern.compile("\\s+")
    private val SPACE_BEFORE_PUNCT = Pattern.compile("\\s+([,.;:!?…])")

    fun polish(raw: String, options: PolishOptions = PolishOptions()): String {
        var text = raw.trim()
        if (text.isEmpty()) return ""

        text = MULTI_WS.matcher(text).replaceAll(" ")

        if (options.removeFillers) {
            for (filler in fillersFor(options.language)) {
                // (?<!\p{L}) ... (?!\p{L}) = ganze-Wort-Grenze, unicode-tauglich (ae, oe...).
                // Optionales folgendes Komma mitnehmen, damit keine ", ," Reste bleiben.
                val p = Pattern.compile(
                    "(?<!\\p{L})" + Pattern.quote(filler) + "(?!\\p{L}),?",
                    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE,
                )
                text = p.matcher(text).replaceAll(" ")
            }
            text = MULTI_WS.matcher(text).replaceAll(" ").trim()
        }

        text = SPACE_BEFORE_PUNCT.matcher(text).replaceAll("$1")
        text = MULTI_WS.matcher(text).replaceAll(" ").trim()

        if (options.autoCapitalize) {
            text = capitalizeSentences(text)
        }
        return text
    }

    // Im auto-Modus NUR sprachuebergreifend eindeutige Disfluenzen — niemals Woerter,
    // die in irgendeiner Sprache echt sind (z.B. dt. "um", span. "este"). Sonst wuerde
    // der auto-Modus legitime Eingaben loeschen.
    private val AUTO_FILLERS = listOf(
        "ähm", "äh", "öhm", "ähem", "öh", "uh", "uhm", "erm", "euh", "ehm", "hmm", "mmm",
    )

    private fun fillersFor(language: String): List<String> {
        if (language == "auto") return AUTO_FILLERS
        return FILLERS[language] ?: emptyList()
    }

    /** Erster Buchstabe + jeder Buchstabe nach . ! ? gross. */
    private fun capitalizeSentences(text: String): String {
        val sb = StringBuilder(text.length)
        var capitalizeNext = true
        for (ch in text) {
            if (capitalizeNext && ch.isLetter()) {
                sb.append(ch.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(ch)
            }
            when (ch) {
                '.', '!', '?' -> capitalizeNext = true
            }
        }
        return sb.toString()
    }
}
