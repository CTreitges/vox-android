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
        val prefs = Prefs.get(app)
        // Gewaehltes Modell aufloesen; fehlt es (noch nicht geladen), auf das gebuendelte
        // SMALL zurueckfallen, damit Diktat nie bricht.
        val (model, source) = resolveSource(app, prefs.model)

        val existing = ctx
        if (existing != null && loadedModel == model) {
            existing.fastMode = prefs.fastMode
            return existing
        }

        existing?.release()
        ctx = null
        loadedModel = null

        Log.i(TAG, "Lade Modell ${model.id} aus $source (${CpuInfo.inferenceThreads} Threads)")
        val newCtx = WhisperContext.createFrom(app, source)
        newCtx.fastMode = prefs.fastMode
        ctx = newCtx
        loadedModel = model
        newCtx
    }

    private fun resolveSource(app: Context, requested: WhisperModel): Pair<WhisperModel, ModelSource> {
        ModelManager.localSource(app, requested)?.let { return requested to it }
        ModelManager.localSource(app, WhisperModel.SMALL)?.let { return WhisperModel.SMALL to it }
        throw ModelNotAvailableException(requested)
    }

    /**
     * Vorwaermen im Hintergrund — der uebliche Aufruf aus der UI. Ein einzelner,
     * prozessweiter Thread genuegt: das Laden ist ohnehin durch [lock] serialisiert.
     */
    fun preloadAsync(context: Context) {
        val app = context.applicationContext
        if (ctx != null) return // schon warm
        preloader.execute {
            runCatching { preload(app) }
        }
    }

    private val preloader by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "wb-preload").apply { isDaemon = true }
        }
    }

    /** Optionales Vorwaermen des lokalen Modells (im API-Modus nicht noetig). */
    fun preload(context: Context) {
        val app = context.applicationContext
        if (Prefs.get(app).useApi) return
        if (ctx != null) return // schon warm — kein Lock noetig
        try {
            ensureLoaded(app)
        } catch (e: Exception) {
            Log.w(TAG, "Preload uebersprungen: ${e.message}")
        }
    }

    /**
     * Transkribiert die Aufnahme. Trimmt vorher Stille — rein ueber Indizes, das Audio
     * wird dabei nicht kopiert. Nutzt je nach Einstellung die Cloud-API (bessere
     * Qualität, sendet Audio) oder das lokale Modell.
     * Wirft [ModelNotAvailableException], wenn das lokale Modell nicht vorliegt.
     */
    fun transcribe(context: Context, audio: AudioSlice, language: String): String {
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        val trimmed = AudioUtils.trimSilence(audio)
        if (prefs.useApi) {
            return ApiTranscriber(prefs.apiBaseUrl, prefs.apiKey, prefs.apiModel)
                .transcribe(trimmed, language)
        }
        return ensureLoaded(app).transcribe(trimmed, language)
    }

    fun isReady(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = Prefs.get(app)
        return if (prefs.useApi) prefs.apiKey.isNotBlank()
        else ModelManager.isAvailable(app, prefs.model)
    }

    fun release() = synchronized(lock) {
        ctx?.release()
        ctx = null
        loadedModel = null
    }
}
