package com.chris.whisperbar

import android.content.Context
import java.util.concurrent.Executors

/**
 * Thread-sichere Huelle um einen nativen whisper_context.
 *
 * whisper.cpp erlaubt keinen gleichzeitigen Zugriff aus mehreren Threads. Deshalb
 * laufen Laden, Transkribieren und Freigeben alle ueber EINEN dedizierten Single-Thread-
 * Executor. Nach aussen ist [transcribe] synchron (blockiert den Aufrufer-Thread).
 */
class WhisperContext private constructor() : Transcriber {

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "whisper-worker") }

    @Volatile private var ptr: Long = 0L

    private fun initFromAsset(context: Context, assetPath: String) {
        ptr = worker.submit<Long> {
            WhisperLib.initContextFromAsset(context.assets, assetPath)
        }.get()
        if (ptr == 0L) {
            throw RuntimeException("Whisper-Modell konnte nicht geladen werden: $assetPath")
        }
    }

    private fun initFromFile(path: String) {
        ptr = worker.submit<Long> { WhisperLib.initContext(path) }.get()
        if (ptr == 0L) {
            throw RuntimeException("Whisper-Modell konnte nicht geladen werden: $path")
        }
    }

    override fun transcribe(samples: FloatArray, language: String): String {
        if (samples.isEmpty()) return ""
        return worker.submit<String> {
            val ctx = ptr
            check(ctx != 0L) { "WhisperContext bereits freigegeben" }
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            WhisperLib.fullTranscribe(ctx, threads, language, samples)
            val count = WhisperLib.getTextSegmentCount(ctx)
            buildString {
                for (i in 0 until count) append(WhisperLib.getTextSegment(ctx, i))
            }
        }.get()
    }

    override fun release() {
        try {
            worker.submit {
                if (ptr != 0L) {
                    WhisperLib.freeContext(ptr)
                    ptr = 0L
                }
            }.get()
        } finally {
            worker.shutdown()
        }
    }

    companion object {
        /** Asset-Pfad des gebuendelten Standard-Modells (in app/src/main/assets/). */
        const val MODEL_ASSET = "models/ggml-small-q5_1.bin"

        /**
         * Laedt das Modell aus den Assets des uebergebenen Context (streamt direkt aus
         * dem APK). Wichtig: NICHT applicationContext erzwingen — im Instrumented-Test
         * muss der Test-Context seine eigenen (androidTest-)Assets liefern koennen.
         */
        fun createFromAsset(context: Context, assetPath: String = MODEL_ASSET): WhisperContext {
            return WhisperContext().apply { initFromAsset(context, assetPath) }
        }

        /** Laedt das Modell aus einer Datei (heruntergeladene Modelle in filesDir). */
        fun createFromFile(path: String): WhisperContext {
            return WhisperContext().apply { initFromFile(path) }
        }

        /** Laedt aus der angegebenen Quelle (gebuendeltes Asset oder Datei). */
        fun createFrom(context: Context, source: ModelSource): WhisperContext = when (source) {
            is ModelSource.Asset -> createFromAsset(context, source.path)
            is ModelSource.FileP -> createFromFile(source.path)
        }

        fun systemInfo(): String = WhisperLib.getSystemInfo()
    }
}
