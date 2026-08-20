package com.chris.whisperbar

import android.content.Context
import android.provider.OpenableColumns
import com.chris.whisperbar.api.ApiNotConfiguredException
import com.chris.whisperbar.api.ApiTranscriber
import com.chris.whisperbar.api.TextRefiner
import com.chris.whisperbar.api.WavUpload
import java.io.File
import java.io.RandomAccessFile

/**
 * Einziger Weg vom Audio zum fertigen Text. Reihenfolge:
 *
 *  1. Stille am Anfang/Ende wegschneiden (kleinerer Upload)
 *  2. Transkription ueber die konfigurierte OpenAI-kompatible API
 *  3. optional: Sprachmodell glaettet Zeichensetzung/Grammatik
 *  4. Nachbearbeitung (Fuellwoerter, Gross-Schreibung, Whitespace)
 *
 * Blockierend — immer aus einem Hintergrund-Thread aufrufen.
 */
object TranscriptionEngine {

    /** Ob ueberhaupt diktiert werden kann (API-Key hinterlegt). */
    fun isConfigured(context: Context): Boolean =
        Prefs(context.applicationContext).apiKey.isNotBlank()

    /**
     * @throws ApiNotConfiguredException wenn kein API-Key gesetzt ist.
     * @throws com.chris.whisperbar.api.ApiNetworkException bei Netzproblemen.
     * @throws com.chris.whisperbar.api.ApiHttpException bei Fehlerstatus der API.
     */
    fun transcribe(context: Context, samples: FloatArray): String {
        val prefs = Prefs(context.applicationContext)
        if (prefs.apiKey.isBlank()) throw ApiNotConfiguredException()

        val language = prefs.language
        val trimmed = AudioUtils.trimSilence(samples)

        val raw = ApiTranscriber(
            baseUrl = prefs.apiBaseUrl,
            apiKey = prefs.apiKey,
            model = prefs.apiModel,
            prompt = prefs.apiPrompt,
        ).transcribe(trimmed, language)

        if (raw.isBlank()) return ""

        val refined = if (prefs.llmPolish) {
            TextRefiner(prefs.apiBaseUrl, prefs.apiKey, prefs.llmModel)
                .refine(raw, language, prefs.smartFillers)
        } else {
            raw
        }

        val options = PolishPlan.options(
            removeFillers = prefs.removeFillers,
            autoCapitalize = prefs.autoCapitalize,
            language = language,
            llmPolish = prefs.llmPolish,
            smartFillers = prefs.smartFillers,
        )
        return TextPolisher.polish(refined, options)
    }
}

/**
 * Ergebnis einer geteilten Audiodatei.
 */
data class SharedTranscript(
    /** Anzeigename der Quelle (Dateiname), fuer die Ueberschrift in der Ergebnis-Ansicht. */
    val source: String,
    val text: String,
    val durationMs: Long,
)

/**
 * Transkribiert Audiodateien, die aus einer anderen App geteilt wurden
 * (WhatsApp-Sprachnachricht, Aufnahme-App, Dateimanager …).
 *
 * Bewusst getrennt von [TranscriptionEngine]: geteiltes Audio wird WORTGETREU
 * ausgegeben — keine Fuellwort-Entfernung, keine KI-Glaettung. Bei einer fremden
 * Sprachnachricht will man wissen, was gesagt wurde, nicht eine geglaettete Fassung.
 */
object SharedAudioTranscriber {

    /** Hoechstlaenge eines Stuecks. 5 Min = 9,6 MB WAV — deutlich unter dem 25-MB-Limit. */
    private const val MAX_CHUNK_FRAMES = AudioConvert.TARGET_RATE * 300

    /** So weit vor der Grenze wird nach einer Sprechpause zum Schneiden gesucht. */
    private const val CUT_SEARCH_FRAMES = AudioConvert.TARGET_RATE * 20

    /**
     * @param onProgress (Schritt, Gesamtschritte, Beschriftung) — Gesamtschritte ist erst
     *   nach dem Entpacken bekannt und kann sich einmal erhoehen.
     * @throws ApiNotConfiguredException wenn kein API-Key gesetzt ist.
     * @throws UnsupportedAudioException wenn die Datei nicht decodiert werden kann.
     */
    fun transcribe(
        context: Context,
        uri: android.net.Uri,
        onProgress: (Int, Int, String) -> Unit = { _, _, _ -> },
        isCancelled: () -> Boolean = { false },
    ): SharedTranscript {
        val app = context.applicationContext
        val prefs = Prefs(app)
        if (prefs.apiKey.isBlank()) throw ApiNotConfiguredException()

        val name = displayName(app, uri)
        val temp = File.createTempFile("shared-", ".pcm", app.cacheDir)
        try {
            onProgress(0, 1, app.getString(R.string.share_decoding))
            val decoded = AudioDecoder.decodeToPcm(app, uri, temp, isCancelled = isCancelled)
            if (decoded.frameCount == 0) {
                throw UnsupportedAudioException(app.getString(R.string.share_empty))
            }

            val chunks = AudioChunks.plan(
                totalFrames = decoded.frameCount,
                maxFrames = MAX_CHUNK_FRAMES,
                profile = decoded.profile,
                framesPerEntry = decoded.framesPerProfileEntry,
                searchFrames = CUT_SEARCH_FRAMES,
            )

            val transcriber = ApiTranscriber(
                baseUrl = prefs.apiBaseUrl,
                apiKey = prefs.apiKey,
                model = prefs.apiModel,
                prompt = prefs.apiPrompt,
            )

            val parts = mutableListOf<String>()
            for ((i, chunk) in chunks.withIndex()) {
                if (isCancelled()) throw UnsupportedAudioException("Abgebrochen")
                onProgress(i, chunks.size, app.getString(R.string.share_sending))
                val part = transcriber.transcribe(
                    upload(decoded.pcmFile, chunk),
                    prefs.language,
                )
                if (part.isNotBlank()) parts.add(part)
            }
            onProgress(chunks.size, chunks.size, app.getString(R.string.share_sending))

            val joined = parts.joinToString(" ")
            return SharedTranscript(
                source = name,
                text = TextPolisher.polish(joined, PolishPlan.verbatim(prefs.language)),
                durationMs = decoded.durationMs,
            )
        } finally {
            temp.delete()
        }
    }

    /** Streamt genau ein Stueck aus der entpackten PCM-Datei in die Verbindung. */
    private fun upload(pcmFile: File, chunk: AudioChunks.Chunk) = WavUpload(
        pcmByteCount = chunk.frameCount * 2,
    ) { os ->
        RandomAccessFile(pcmFile, "r").use { raf ->
            raf.seek(chunk.startFrame * 2L)
            val buf = ByteArray(64 * 1024)
            var left = chunk.frameCount * 2
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size, left))
                if (n <= 0) break
                os.write(buf, 0, n)
                left -= n
            }
        }
    }

    /** Dateiname der geteilten Quelle, sonst ein neutraler Ersatz. */
    private fun displayName(context: Context, uri: android.net.Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) {
                        val n = c.getString(idx)
                        if (!n.isNullOrBlank()) return n
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.share_unknown_source)
    }
}
