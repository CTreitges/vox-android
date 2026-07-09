package com.chris.whisperbar

/**
 * Abstraktion ueber die Spracherkennung, damit UI/IME nicht direkt an die
 * JNI-Schicht koppeln. Implementiert von [WhisperContext] (whisper.cpp).
 */
interface Transcriber {
    /**
     * @param samples 16 kHz Mono, Float im Bereich [-1, 1].
     * @param language ISO-Code ("de", "en", ...) oder "auto".
     * @return erkannter Rohtext (noch nicht poliert).
     */
    fun transcribe(samples: FloatArray, language: String): String

    /** Native Ressourcen freigeben. */
    fun release()
}
