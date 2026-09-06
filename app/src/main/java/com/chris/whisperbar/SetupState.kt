package com.chris.whisperbar

/**
 * Wann die App "eingerichtet" ist. Rein (ohne Android), damit JVM-unit-testbar —
 * die Start-Logik (Setup-Screen oder Home) haengt daran.
 *
 * Overlay + Bedienungshilfe sind fuer den schwebenden Knopf noetig, aber kein Blocker.
 */
object SetupState {

    /** Transkriptions-Zugang vollstaendig: Base-URL da und Key da (sofern der Anbieter einen will). */
    fun sttComplete(baseUrl: String, apiKey: String, needsKey: Boolean): Boolean =
        baseUrl.isNotBlank() && (!needsKey || apiKey.isNotBlank())

    /** [engine] null = noch nicht gewaehlt -> nicht bereit. */
    fun isReady(
        engine: Engine?,
        sttComplete: Boolean,
        offlineModelPresent: Boolean,
        micGranted: Boolean,
    ): Boolean {
        if (!micGranted) return false
        return when (engine) {
            Engine.ONLINE -> sttComplete
            Engine.OFFLINE -> offlineModelPresent
            null -> false
        }
    }
}
