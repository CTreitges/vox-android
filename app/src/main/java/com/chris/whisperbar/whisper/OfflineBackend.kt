package com.chris.whisperbar.whisper

import android.content.Context
import com.chris.whisperbar.TranscriptResult
import com.chris.whisperbar.TranscriptionBackend
import com.chris.whisperbar.api.WavUpload

/** Offline gewaehlt, aber kein Modell auf dem Geraet (oder die Engine fehlt noch). */
class OfflineNotAvailableException :
    RuntimeException("Offline-Erkennung nicht verfügbar — Modell in den Einstellungen herunterladen")

/**
 * Platzhalter — WP3 ersetzt: prueft dann, ob das gewaehlte Modell (Prefs.offlineModel)
 * vollstaendig in filesDir/models liegt.
 */
object OfflineStatus {
    @Suppress("UNUSED_PARAMETER")
    fun isModelAvailable(context: Context): Boolean = false
}

/**
 * Platzhalter — WP3 ersetzt ihn durch die whisper.cpp-Anbindung (WhisperEngine/JNI).
 * Parameter sind schon die spaeteren: Modell-ID, Beam-Search vs. Greedy, initial_prompt.
 */
class OfflineBackend(
    @Suppress("unused") private val modelId: String,
    @Suppress("unused") private val accurate: Boolean,
    @Suppress("unused") private val initialPrompt: String,
) : TranscriptionBackend {

    override val label: String = "Offline"

    override fun transcribe(upload: WavUpload, language: String): TranscriptResult =
        throw OfflineNotAvailableException()
}
