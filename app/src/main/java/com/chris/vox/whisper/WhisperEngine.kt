package com.chris.vox.whisper

import android.content.Context
import android.util.Log
import com.chris.vox.TranscriptResult
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Prozessweiter Zugriff auf das geladene whisper-Modell. IME, schwebender Knopf und Share-Ansicht
 * laufen im selben Prozess und teilen sich DIESE Instanz — sonst laege das Modell doppelt im RAM
 * (small ~430 MB, large-v3-turbo ~1 GB). Laedt das gewuenschte Modell bei Bedarf, tauscht es bei
 * Modellwechsel (erst freigeben, dann laden) und gibt es bei Speicherdruck frei ([release]).
 */
object WhisperEngine {

    private const val TAG = "WhisperEngine"

    /** Beam-Search-Breite fuer "Genau" (whisper-cli-Default). */
    const val BEAM_SIZE = 5

    private val lock = ReentrantLock()
    private val busy = AtomicInteger()

    @Volatile private var store: ModelStore? = null
    @Volatile private var ctx: WhisperContext? = null
    private var loadedModelId: String? = null

    /**
     * Beim App-Start ([com.chris.vox.VoxApplication]) und als Sicherheitsnetz aus [OfflineStatus].
     * Setzt immer neu (nur ein File-Wrapper): unter Robolectric hat jeder Test ein eigenes filesDir.
     */
    fun init(context: Context) {
        store = ModelStore(context.applicationContext)
    }

    /** Liegt gerade ein Modell im Speicher? (Statusanzeige/Debug) */
    val isLoaded: Boolean get() = ctx != null

    /**
     * Blockierend — aus einem Hintergrund-Thread. Laedt [modelId], falls noetig.
     * @throws OfflineNotAvailableException wenn Modell, Geraete-Unterstuetzung oder Bibliothek fehlen
     * @throws OfflineTranscriptionException wenn whisper_full scheitert
     */
    fun transcribe(modelId: String, samples: FloatArray, language: String, initialPrompt: String, accurate: Boolean): TranscriptResult {
        busy.incrementAndGet()
        try {
            val context = ensureLoaded(modelId)
            return context.transcribe(samples, language, initialPrompt, if (accurate) BEAM_SIZE else 1, OfflineSupport.threadCount())
        } finally {
            busy.decrementAndGet()
        }
    }

    /** Laufende Erkennung abbrechen (Abbrechen-Ziel des schwebenden Knopfs, Share-Ansicht). */
    fun abort() {
        ctx?.abort()
    }

    /**
     * Modell aus dem Speicher werfen (onTrimMemory, Modell geloescht). Kehrt sofort zurueck: eine
     * gerade laufende Erkennung wird nicht unterbrochen — dann bleibt das Modell, der naechste
     * Speicherdruck-Aufruf versucht es erneut.
     */
    fun release() {
        if (!lock.tryLock()) return // gerade wird geladen: Main-Thread nicht blockieren
        try {
            if (busy.get() > 0) return
            ctx?.release(wait = false)
            ctx = null
            loadedModelId = null
        } finally {
            lock.unlock()
        }
    }

    private fun ensureLoaded(modelId: String): WhisperContext = lock.withLock {
        val store = store ?: throw OfflineNotAvailableException()
        val model = ModelCatalog.find(modelId) ?: throw OfflineNotAvailableException()
        if (!store.isInstalled(model)) throw OfflineNotAvailableException()
        if (!WhisperLib.available) throw OfflineNotAvailableException(OfflineNotAvailableException.MSG_UNSUPPORTED)
        ctx?.let { if (loadedModelId == modelId) return it }
        ctx?.release(wait = true) // erst freigeben, dann laden: sonst liegen kurz beide Modelle im RAM
        ctx = null
        loadedModelId = null
        Log.i(TAG, "Lade Modell ${model.id} (${model.fileName})")
        val loaded = WhisperContext.load(store.file(model))
        ctx = loaded
        loadedModelId = modelId
        loaded
    }
}
