package com.chris.whisperbar

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Verfuegbare Whisper-Modelle (alle q5_1-quantisiert). SMALL ist im APK gebuendelt
 * (out-of-the-box offline), BASE/TINY werden bei Bedarf einmalig geladen.
 */
enum class WhisperModel(
    val id: String,
    val display: String,
    val fileName: String,
    val approxMb: Int,
    /** Erwartete Dateigroesse in Bytes (fuer die Download-Integritaetspruefung). */
    val bytes: Long,
    val bundled: Boolean,
) {
    SMALL("small", "Small — beste Qualität (~181 MB)", "ggml-small-q5_1.bin", 181, 190_085_487L, true),
    BASE("base", "Base — ausgewogen (~56 MB)", "ggml-base-q5_1.bin", 56, 59_707_625L, false),
    TINY("tiny", "Tiny — am schnellsten (~30 MB)", "ggml-tiny-q5_1.bin", 30, 32_152_673L, false);

    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    companion object {
        fun fromId(id: String?): WhisperModel = entries.firstOrNull { it.id == id } ?: SMALL
    }
}

/** Woher ein Modell geladen wird: gebuendeltes Asset oder Datei in filesDir. */
sealed interface ModelSource {
    data class Asset(val path: String) : ModelSource
    data class FileP(val path: String) : ModelSource
}

/**
 * Verwaltet die Modell-Dateien: gebuendeltes SMALL aus Assets, BASE/TINY per Download
 * nach filesDir (idempotent, atomar via .tmp-rename).
 */
object ModelManager {

    fun assetPath(model: WhisperModel) = "models/${model.fileName}"

    fun localFile(context: Context, model: WhisperModel) = File(context.filesDir, model.fileName)

    /** Lokal verfuegbare Quelle oder null (dann muss [download] laufen). */
    fun localSource(context: Context, model: WhisperModel): ModelSource? {
        if (model.bundled) return ModelSource.Asset(assetPath(model))
        val f = localFile(context, model)
        return if (f.exists() && f.length() > 1_000_000L) ModelSource.FileP(f.absolutePath) else null
    }

    fun isAvailable(context: Context, model: WhisperModel) = localSource(context, model) != null

    /**
     * Laedt ein (nicht gebuendeltes) Modell nach filesDir. Blockierend — vom Hintergrund
     * aufrufen. [progress] liefert 0..100. Gibt true bei Erfolg.
     */
    fun download(context: Context, model: WhisperModel, progress: (Int) -> Unit): Boolean {
        if (model.bundled) return true
        val target = localFile(context, model)
        if (target.exists() && target.length() > 1_000_000L) return true
        val tmp = File(context.filesDir, "${model.fileName}.tmp")
        return try {
            val conn = (URL(model.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) return false
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var n: Int
                    var lastPct = -1
                    while (input.read(buf).also { n = it } >= 0) {
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) {
                            val pct = ((read * 100) / total).toInt()
                            if (pct != lastPct) { lastPct = pct; progress(pct) }
                        }
                    }
                }
            }
            // Integritaet: bei bekannter Laenge exakt verlangen; zusaetzlich gegen die
            // erwartete Modellgroesse pruefen (fangt Teildownload / HTML-Fehlerseite ab).
            if (total > 0 && read != total) { tmp.delete(); return false }
            if (tmp.length() < model.bytes * 95 / 100) { tmp.delete(); return false }
            if (!tmp.renameTo(target)) { tmp.delete(); return false }
            true
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }
}
