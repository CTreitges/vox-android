package com.chris.whisperbar

import com.chris.whisperbar.api.ApiAccess
import com.chris.whisperbar.api.ApiTranscriber
import com.chris.whisperbar.api.WavUpload

/** Rohtext einer Erkennung plus — wenn der Erkenner sie meldet — die erkannte Sprache. */
data class TranscriptResult(
    val text: String,
    val detectedLanguage: String? = null,
)

/**
 * Abstraktion ueber die Spracherkennung, damit Engine/IME/Share nicht wissen muessen,
 * ob ueber einen Anbieter oder auf dem Geraet erkannt wird.
 */
interface TranscriptionBackend {
    /** Kurzname fuer die Anzeige ("OpenAI", "Offline"). */
    val label: String

    /**
     * @param upload 16 kHz Mono PCM16 (Diktat oder ein Stueck einer geteilten Datei).
     * @param language ISO-Code ("de", "en", ...) oder "auto".
     */
    fun transcribe(upload: WavUpload, language: String): TranscriptResult
}

/** Erkennung ueber die OpenAI-kompatible API des konfigurierten Anbieters. */
class OnlineBackend(access: ApiAccess, prompt: String = "") : TranscriptionBackend {

    private val api = ApiTranscriber(access, prompt)

    override val label: String = access.provider.name

    override fun transcribe(upload: WavUpload, language: String): TranscriptResult =
        api.transcribe(upload, language)
}
