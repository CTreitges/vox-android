package com.chris.whisperbar

import android.content.res.AssetManager

/**
 * Direkte JNI-Bindings zu libwhisperbar.so. Die Methodennamen MUESSEN exakt zu den
 * `Java_com_chris_whisperbar_WhisperLib_*`-Symbolen in whisper_jni.cpp passen.
 *
 * Nicht direkt benutzen — [WhisperContext] kapselt Thread-Sicherheit und Lebenszyklus.
 */
internal object WhisperLib {
    init {
        System.loadLibrary("whisperbar")
    }

    external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    /**
     * @param audioOffset / [audioLength] Teilbereich von [audioData] — nur dieser wird
     *   nach nativ kopiert, der Rest des Aufnahme-Puffers wird gar nicht angefasst.
     * @param audioCtx gedeckelte Encoder-Positionen (siehe [WhisperTuning.audioCtxFor]).
     */
    external fun fullTranscribe(
        contextPtr: Long,
        numThreads: Int,
        language: String,
        audioCtx: Int,
        audioData: FloatArray,
        audioOffset: Int,
        audioLength: Int,
    )
    external fun getTextSegmentCount(contextPtr: Long): Int
    external fun getTextSegment(contextPtr: Long, index: Int): String
    external fun getSystemInfo(): String
}
