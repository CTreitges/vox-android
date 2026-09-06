package com.chris.whisperbar

/**
 * Teilt einen langen Transkript-Text heuristisch in Absaetze — fuer geteilte
 * Sprachnachrichten, die als ein Block kaum lesbar sind. Rein (ohne Android).
 *
 * Grundsaetze: nie mitten im Satz trennen, Woerter nie anfassen (die Absaetze mit
 * Leerzeichen zusammengefuegt ergeben wieder den Eingabetext), Absatzwechsel bevorzugt
 * vor Diskursmarkern ("Also", "Ausserdem", "Dann", "Okay" …). Konservativ: lieber
 * ein Absatz zu wenig als ein Schnitt an der falschen Stelle.
 */
object Paragrapher {

    /** Ab hier darf ein Absatz enden. */
    private const val MIN_CHARS = 280

    /** Ab hier soll er enden, sobald der naechste Satz nicht mehr passt. */
    private const val MAX_CHARS = 420

    private const val MIN_SENTENCES = 2
    private const val MAX_SENTENCES = 4

    /** Ein so kurzer letzter Einzelsatz ("Tschuess.") haengt sich an den vorigen Absatz. */
    private const val SHORT_TAIL_CHARS = 60

    /** Satzgrenze: Satzzeichen (optional Anfuehrungszeichen/Klammer) und dann Whitespace. */
    private val SENTENCE_END = Regex("(?<=[.!?…][\"»“”')\\]]?)\\s+")

    /**
     * Woerter, die vor einem Punkt KEIN Satzende bedeuten. Einzelbuchstaben ("z. B.") und
     * reine Zahlen ("am 3. Oktober") werden zusaetzlich programmatisch erkannt.
     */
    private val ABBREVIATIONS = setOf(
        "bzw", "usw", "ca", "evtl", "ggf", "inkl", "exkl", "zzgl", "vgl", "dr", "nr", "str",
        "prof", "vs", "etc", "mr", "mrs", "ms", "st", "tel", "max", "min",
    )

    /** Satzanfaenge, vor denen ein Absatzwechsel natuerlich wirkt (klein geschrieben). */
    private val MARKERS = listOf(
        "also", "außerdem", "ausserdem", "dann", "und dann", "okay", "ok", "zweitens", "drittens",
        "noch was", "noch etwas", "übrigens", "jedenfalls", "ach so", "ach ja", "genau", "so",
        "anyway", "then", "secondly", "another thing", "by the way", "also,",
    )

    fun split(text: String): List<String> {
        val sentences = sentences(text)
        if (sentences.isEmpty()) return emptyList()

        val paragraphs = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        var currentChars = 0

        for (s in sentences) {
            if (current.size >= MIN_SENTENCES && currentChars >= MIN_CHARS &&
                (startsWithMarker(s) || current.size >= MAX_SENTENCES || currentChars + 1 + s.length > MAX_CHARS)
            ) {
                paragraphs.add(current)
                current = mutableListOf()
                currentChars = 0
            }
            current.add(s)
            currentChars += s.length + if (current.size > 1) 1 else 0
        }
        paragraphs.add(current)

        // Ein kurzer Einzelsatz am Ende ("Tschuess.") wirkt als eigener Absatz verloren.
        if (paragraphs.size > 1) {
            val tail = paragraphs.last()
            if (tail.size == 1 && tail[0].length < SHORT_TAIL_CHARS) {
                paragraphs.removeAt(paragraphs.size - 1)
                paragraphs.last().addAll(tail)
            }
        }
        return paragraphs.map { it.joinToString(" ") }
    }

    /** Zerlegt in Saetze; Abkuerzungen, Einzelbuchstaben und Ordnungszahlen bleiben verbunden. */
    fun sentences(text: String): List<String> {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return emptyList()
        val pieces = SENTENCE_END.split(normalized).filter { it.isNotEmpty() }

        val out = mutableListOf<String>()
        val pending = StringBuilder()
        for (piece in pieces) {
            if (pending.isNotEmpty()) pending.append(' ')
            pending.append(piece)
            if (!endsWithAbbreviation(piece)) {
                out.add(pending.toString())
                pending.setLength(0)
            }
        }
        if (pending.isNotEmpty()) out.add(pending.toString())
        return out
    }

    private fun endsWithAbbreviation(piece: String): Boolean {
        if (!piece.endsWith(".")) return false
        val lastWord = piece.dropLast(1).substringAfterLast(' ').trimStart('(', '"', '„', '«')
        if (lastWord.isEmpty()) return false
        if (lastWord.length == 1 && lastWord[0].isLetter()) return true
        if (lastWord.all { it.isDigit() }) return true
        return lastWord.lowercase() in ABBREVIATIONS
    }

    private fun startsWithMarker(sentence: String): Boolean {
        val s = sentence.lowercase()
        return MARKERS.any { m ->
            s.startsWith(m) && (s.length == m.length || !s[m.length].isLetter())
        }
    }
}
