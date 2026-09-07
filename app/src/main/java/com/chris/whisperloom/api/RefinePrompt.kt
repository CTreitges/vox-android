package com.chris.whisperloom.api

import com.chris.whisperloom.RefineMode

/**
 * Baut die Anweisung fuer die Textverbesserung. Rein (ohne Android/Netz), damit
 * JVM-unit-testbar — die Anweisung entscheidet ueber die Textqualitaet und darf
 * nicht unbemerkt verrutschen.
 */
object RefinePrompt {

    /**
     * @param mode Was das Modell tun soll (nicht [RefineMode.OFF]).
     * @param german Anweisung auf Deutsch (bei deutscher Diktatsprache) statt Englisch.
     * @param smartFillers Wenn true, entscheidet das Modell selbst, welche Fuellwoerter,
     *   Versprecher und Wiederholungen weg koennen — statt einer festen Wortliste.
     *   Bei einer Zusammenfassung gegenstandslos.
     */
    fun build(mode: RefineMode, german: Boolean, smartFillers: Boolean): String {
        require(mode != RefineMode.OFF) { "RefineMode.OFF hat keine Anweisung" }
        val sb = StringBuilder()
        sb.append(if (german) taskDe(mode) else taskEn(mode))
        if (smartFillers && mode != RefineMode.SUMMARIZE) {
            sb.append(if (german) FILLERS_DE else FILLERS_EN)
        }
        sb.append(if (german) replyDe(mode) else replyEn(mode))
        return sb.toString()
    }

    private fun taskDe(mode: RefineMode): String = when (mode) {
        RefineMode.POLISH ->
            "Du korrigierst diktierten Text. Setze Zeichensetzung, Gross- und " +
                "Kleinschreibung sowie Absaetze richtig. Aendere den Inhalt nicht, " +
                "uebersetze nicht, ergaenze nichts und kommentiere nicht."
        RefineMode.BEAUTIFY ->
            "Du ueberarbeitest diktierten Text, damit er verstaendlicher wird: formuliere " +
                "holprige Stellen klarer, ziehe zerstueckelte Saetze zusammen und setze " +
                "Zeichensetzung und Absaetze richtig. Bewahre Inhalt und Absicht, behalte die " +
                "Ich-Perspektive bei, erfinde keine neuen Fakten, uebersetze nicht und " +
                "kommentiere nicht."
        RefineMode.SUMMARIZE ->
            "Du fasst diktierten Text kurz zusammen: die Kernaussagen in wenigen Absaetzen " +
                "oder Stichpunkten, in der Sprache des Textes. Erfinde keine neuen Fakten, " +
                "uebersetze nicht und kommentiere nicht."
        RefineMode.PARAGRAPHS ->
            "Du gliederst diktierten Text in Absaetze. Lass den Wortlaut sonst unveraendert, " +
                "fasse nichts zusammen, aendere den Inhalt nicht, uebersetze nicht, ergaenze " +
                "nichts und kommentiere nicht."
        RefineMode.OFF -> ""
    }

    private fun taskEn(mode: RefineMode): String = when (mode) {
        RefineMode.POLISH ->
            "You clean up dictated text. Fix punctuation, capitalisation and " +
                "paragraphs. Do not change the meaning, do not translate, do not add " +
                "anything and do not comment."
        RefineMode.BEAUTIFY ->
            "You rewrite dictated text so it reads more clearly: smooth out clumsy " +
                "phrasing, merge fragmented sentences and fix punctuation and paragraphs. " +
                "Preserve the content and intent, keep the first-person perspective, do not " +
                "invent new facts, do not translate and do not comment."
        RefineMode.SUMMARIZE ->
            "You summarise dictated text briefly: the key points in a few paragraphs or " +
                "bullet points, in the language of the text. Do not invent new facts, do not " +
                "translate and do not comment."
        RefineMode.PARAGRAPHS ->
            "You split dictated text into paragraphs. Leave the wording unchanged " +
                "otherwise, do not summarise, do not change the meaning, do not translate, " +
                "do not add anything and do not comment."
        RefineMode.OFF -> ""
    }

    private fun replyDe(mode: RefineMode): String = when (mode) {
        RefineMode.SUMMARIZE -> " Antworte ausschliesslich mit der Zusammenfassung."
        else -> " Antworte ausschliesslich mit dem bearbeiteten Text."
    }

    private fun replyEn(mode: RefineMode): String = when (mode) {
        RefineMode.SUMMARIZE -> " Reply only with the summary."
        else -> " Reply only with the edited text."
    }

    private const val FILLERS_DE =
        " Entferne ausserdem Fuellwoerter, Versprecher, Stotterer und unbeabsichtigte " +
            "Wiederholungen, wenn sie erkennbar nicht gemeint waren. Im Zweifel behalte das Wort."

    private const val FILLERS_EN =
        " Also remove filler words, false starts, stutters and unintended repetitions " +
            "where they were clearly not meant. When in doubt, keep it."
}
