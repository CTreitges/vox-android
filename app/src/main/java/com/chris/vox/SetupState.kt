package com.chris.vox

/**
 * Vollstaendigkeit des Transkriptions-Zugangs — rein (ohne Android). Ob die App insgesamt
 * "eingerichtet" ist (Engine, URL gueltig, Modell, Mikrofon, Overlay), entscheidet allein
 * `ui.nav.SetupRouter.isSetUp`; hier liegt nur der Baustein, den Router und
 * [TranscriptionEngine.isConfigured] gemeinsam nutzen.
 */
object SetupState {

    /** Transkriptions-Zugang vollstaendig: Base-URL da und Key da (sofern der Anbieter einen will). */
    fun sttComplete(baseUrl: String, apiKey: String, needsKey: Boolean): Boolean =
        baseUrl.isNotBlank() && (!needsKey || apiKey.isNotBlank())
}
