package com.chris.whisperloom

/**
 * Zerlegt lange Aufnahmen in Stuecke, die einzeln zur Erkennung passen.
 *
 * Die API nimmt hoechstens 25 MB pro Datei; 16 kHz Mono PCM16 sind 32 kB/s, also gut
 * 13 Minuten. Wir schneiden deutlich frueher und vor allem an einer LEISEN Stelle —
 * ein Schnitt mitten im Wort kostet sonst an jeder Naht ein paar Silben.
 */
object AudioChunks {

    /** Halbolffene Grenzen eines Stuecks in Frames (16 kHz Mono). */
    data class Chunk(val startFrame: Int, val endFrame: Int) {
        val frameCount: Int get() = endFrame - startFrame
    }

    /**
     * @param totalFrames Gesamtlaenge der Aufnahme.
     * @param maxFrames Hoechstlaenge eines Stuecks.
     * @param profile Lautstaerke-Profil (RMS je [framesPerEntry] Frames), waehrend des
     *   Dekodierens nebenbei gefuellt. Leer = es wird stur bei [maxFrames] geschnitten.
     * @param framesPerEntry wie viele Frames ein Profil-Eintrag abdeckt.
     * @param searchFrames wie weit vor der Hoechstgrenze nach einer leisen Stelle gesucht wird.
     */
    fun plan(
        totalFrames: Int,
        maxFrames: Int,
        profile: FloatArray = FloatArray(0),
        framesPerEntry: Int = 1,
        searchFrames: Int = 0,
    ): List<Chunk> {
        require(maxFrames > 0) { "maxFrames muss positiv sein" }
        if (totalFrames <= 0) return emptyList()
        if (totalFrames <= maxFrames) return listOf(Chunk(0, totalFrames))

        val chunks = mutableListOf<Chunk>()
        var start = 0
        while (start < totalFrames) {
            val hardEnd = start + maxFrames
            if (hardEnd >= totalFrames) {
                chunks.add(Chunk(start, totalFrames))
                break
            }
            val cut = quietestCut(hardEnd, start, maxFrames, profile, framesPerEntry, searchFrames)
            chunks.add(Chunk(start, cut))
            start = cut
        }
        return chunks
    }

    /**
     * Sucht kurz vor der Hoechstgrenze die leiseste Stelle.
     *
     * Zwei Sicherungen, ohne die es aus dem Ruder laeuft:
     * (1) Es wird nie vor der halben Hoechstlaenge geschnitten — sonst erzeugt eine
     *     Pause direkt am Stueckanfang eine Kette von Winz-Stuecken, von denen jedes
     *     eine eigene API-Anfrage kostet.
     * (2) Nur wenn die Stelle deutlich leiser ist als das Fenster im Schnitt, wird sie
     *     genommen. Bei durchgehendem Reden gibt es keine gute Naht — dann lieber die
     *     volle Laenge ausnutzen.
     */
    private fun quietestCut(
        hardEnd: Int,
        start: Int,
        maxFrames: Int,
        profile: FloatArray,
        framesPerEntry: Int,
        searchFrames: Int,
    ): Int {
        if (profile.isEmpty() || searchFrames <= 0 || framesPerEntry <= 0) return hardEnd
        val earliest = maxOf(hardEnd - searchFrames, start + maxFrames / 2)
        if (earliest >= hardEnd) return hardEnd

        // Aufgerundet, damit der Eintrag vollstaendig im Fenster liegt.
        val firstEntry = (earliest + framesPerEntry - 1) / framesPerEntry
        val lastEntry = minOf(hardEnd / framesPerEntry - 1, profile.size - 1)
        if (firstEntry > lastEntry) return hardEnd

        var bestEntry = firstEntry
        var bestRms = Float.MAX_VALUE
        var sum = 0.0
        for (e in firstEntry..lastEntry) {
            sum += profile[e]
            if (profile[e] < bestRms) {
                bestRms = profile[e]
                bestEntry = e
            }
        }
        val average = sum / (lastEntry - firstEntry + 1)
        if (bestRms >= average * QUIET_FACTOR) return hardEnd

        // Mitte des leisesten Eintrags — dort ist der Abstand zu beiden Woertern am groessten.
        val cut = bestEntry * framesPerEntry + framesPerEntry / 2
        return cut.coerceIn(earliest, hardEnd)
    }

    /** So viel leiser als der Fenster-Durchschnitt muss eine Stelle sein, um als Naht zu taugen. */
    private const val QUIET_FACTOR = 0.5
}
