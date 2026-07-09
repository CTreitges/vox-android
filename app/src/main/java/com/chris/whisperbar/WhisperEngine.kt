package com.chris.whisperbar

import android.content.Context
import android.util.Log

/** Wird geworfen, wenn das gewaehlte Modell (noch) nicht lokal vorliegt. */
class ModelNotAvailableException(val model: WhisperModel) :
    RuntimeException("Modell ${model.display} ist nicht geladen")

/**
 * Prozessweiter, geteilter Zugriff auf das geladene Whisper-Modell. IME und
 * schwebender Mikro-Button nutzen DIESELBE Instanz — sonst laege das Modell doppelt
 * im RAM. Laedt das in den Einstellungen gewaehlte Modell und laedt neu, wenn es wechselt.
 */
object WhisperEngine {

    private const val TAG = "WhisperEngine"
    private val lock = Any()

    @Volatile private var ctx: WhisperContext? = null
    @Volatile private var loadedModel: WhisperModel? = null

    /** Laedt das aktuell gewaehlte Modell (falls noetig) und liefert den Kontext. */
    private fun ensureLoaded(context: Context): WhisperContext = synchronized(lock) {
        val app = context.applicationContext
        // Gewaehltes Modell aufloesen; fehlt es (noch nicht geladen), auf das gebuendelte
        // SMALL zurueckfallen, damit Diktat nie bricht.
        val (model, source) = resolveSource(app, Prefs(app).model)

        val existing = ctx
        if (existing != null && loadedModel == model) return existing

        existing?.release()
        ctx = null
        loadedModel = null

        Log.i(TAG, "Lade Modell ${model.id} aus $source")
        val newCtx = WhisperContext.createFrom(app, source)
        ctx = newCtx
        loadedModel = model
        newCtx
    }

    private fun resolveSource(app: Context, requested: WhisperModel): Pair<WhisperModel, ModelSource> {
        ModelManager.localSource(app, requested)?.let { return requested to it }
        ModelManager.localSource(app, WhisperModel.SMALL)?.let { return WhisperModel.SMALL to it }
        throw ModelNotAvailableException(requested)
    }

    /** Optionales Vorwaermen des lokalen Modells (im API-Modus nicht noetig). */
    fun preload(context: Context) {
        if (Prefs(context.applicationContext).useApi) return
        try {
            ensureLoaded(context)
        } catch (e: Exception) {
            Log.w(TAG, "Preload uebersprungen: ${e.message}")
        }
    }

    /**
     * Transkribiert Samples. Trimmt vorher Stille (schneller). Nutzt je nach Einstellung
     * die Cloud-API (bessere Qualität, sendet Audio) oder das lokale Modell.
     * Wirft [ModelNotAvailableException], wenn das lokale Modell nicht vorliegt.
     */
    fun transcribe(context: Context, samples: FloatArray, language: String): String {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val trimmed = AudioUtils.trimSilence(samples)
        if (prefs.useApi) {
            return ApiTranscriber(prefs.apiBaseUrl, prefs.apiKey, prefs.apiModel)
                .transcribe(trimmed, language)
        }
        return ensureLoaded(app).transcribe(trimmed, language)
    }

    fun isReady(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = Prefs(app)
        return if (prefs.useApi) prefs.apiKey.isNotBlank()
        else ModelManager.isAvailable(app, prefs.model)
    }

    fun release() = synchronized(lock) {
        ctx?.release()
        ctx = null
        loadedModel = null
    }
}
