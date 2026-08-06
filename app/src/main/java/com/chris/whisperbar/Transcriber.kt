package com.chris.whisperbar

/**
 * Abstraktion ueber die Spracherkennung, damit UI/IME nicht direkt an die
 * JNI-Schicht koppeln. Implementiert von [WhisperContext] (whisper.cpp) und
 * [ApiTranscriber] (OpenAI-kompatible Cloud).
 */
interface Transcriber {
    /**
     * @param audio 16 kHz Mono, Float im Bereich [-1, 1], als Sicht auf einen
     *   groesseren Puffer — so wandert die Aufnahme ohne Kopie bis in die Erkennung.
     * @param language ISO-Code ("de", "en", ...) oder "auto".
     * @return erkannter Rohtext (noch nicht poliert).
     */
    fun transcribe(audio: AudioSlice, language: String): String

    /** Bequemlichkeits-Ueberladung fuer Aufrufer mit einem vollstaendigen Array. */
    fun transcribe(samples: FloatArray, language: String): String =
        transcribe(AudioSlice.of(samples), language)

    /** Native Ressourcen freigeben. */
    fun release()
}
