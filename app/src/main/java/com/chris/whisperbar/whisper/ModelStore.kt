package com.chris.whisperbar.whisper

import android.content.Context
import java.io.File

/**
 * Modell-Dateien auf dem Geraet (Default: filesDir/models). Eine Datei gilt nur als installiert,
 * wenn sie exakt die Katalog-Groesse hat und keine Teildatei (.part) mehr daneben liegt —
 * so laedt die Engine nie einen halben Download. Der Downloader schreibt nach `<datei>.part`
 * und benennt erst nach erfolgreicher Pruefsumme um.
 */
class ModelStore(val dir: File) {

    constructor(context: Context) : this(File(context.filesDir, DIR_NAME))

    fun file(model: WhisperModel): File = File(dir, model.fileName)

    /** Teildatei eines laufenden oder abgebrochenen Downloads (Range-Resume setzt hier an). */
    fun partFile(model: WhisperModel): File = File(dir, model.fileName + PART_SUFFIX)

    fun isInstalled(model: WhisperModel): Boolean {
        val f = file(model)
        return f.isFile && f.length() == model.bytes && !partFile(model).exists()
    }

    /** Fuer Prefs.offlineModel; unbekannte IDs sind nie installiert. */
    fun isInstalled(modelId: String?): Boolean = ModelCatalog.find(modelId)?.let { isInstalled(it) } ?: false

    fun installed(): List<WhisperModel> = ModelCatalog.models.filter { isInstalled(it) }

    /** Loescht Modell und Teildatei. true, wenn danach nichts mehr da ist. */
    fun delete(model: WhisperModel): Boolean {
        file(model).delete()
        partFile(model).delete()
        return !file(model).exists() && !partFile(model).exists()
    }

    /** Belegter Platz (Modelle + Teildateien) in Bytes. */
    fun usedBytes(): Long = dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    /** Freier Platz im Dateisystem des Modellordners (auch wenn der Ordner noch nicht existiert). */
    fun freeBytes(): Long = generateSequence(dir) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace ?: 0L

    fun ensureDir(): Boolean = dir.isDirectory || dir.mkdirs()

    companion object {
        const val DIR_NAME = "models"
        const val PART_SUFFIX = ".part"
    }
}
