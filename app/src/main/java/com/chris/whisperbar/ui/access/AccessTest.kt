package com.chris.whisperbar.ui.access

import com.chris.whisperbar.AudioUtils
import com.chris.whisperbar.OnlineBackend
import com.chris.whisperbar.RefineMode
import com.chris.whisperbar.api.ApiAccess
import com.chris.whisperbar.api.ApiHttpException
import com.chris.whisperbar.api.ApiNetworkException
import com.chris.whisperbar.api.TextRefiner
import com.chris.whisperbar.api.WavUpload
import java.net.SocketTimeoutException
import java.util.Locale

/**
 * "Zugang pruefen" (Spec §2.2 Schritt 2a / §2.5): eine Sekunde Stille an /audio/transcriptions
 * bzw. ein Mini-Prompt an /chat/completions. Blockierend — aus einem Hintergrund-Thread.
 * Die Fehlerklassifikation ist rein und JVM-testbar.
 */
object AccessTest {

    enum class Kind { UNAUTHORIZED, RATE_LIMIT, SERVER, TIMEOUT, NETWORK, UNKNOWN }

    sealed class Outcome {
        data class Ok(val millis: Long) : Outcome()
        data class Failed(val kind: Kind, val code: Int = 0, val message: String = "") : Outcome()
    }

    const val PROBE_TEXT = "Antworte mit OK"

    fun classify(e: Throwable): Outcome.Failed = when (e) {
        is ApiHttpException -> when {
            e.code == 401 || e.code == 403 -> Outcome.Failed(Kind.UNAUTHORIZED, e.code)
            e.code == 429 -> Outcome.Failed(Kind.RATE_LIMIT, e.code)
            e.code >= 500 -> Outcome.Failed(Kind.SERVER, e.code)
            else -> Outcome.Failed(Kind.UNKNOWN, e.code, e.message.orEmpty())
        }
        is ApiNetworkException ->
            if (e.cause is SocketTimeoutException) Outcome.Failed(Kind.TIMEOUT)
            else Outcome.Failed(Kind.NETWORK, message = e.message.orEmpty())
        else -> Outcome.Failed(Kind.UNKNOWN, message = e.message ?: e.javaClass.simpleName)
    }

    /** Transkriptions-Zugang: 1 s Stille-WAV (16 kHz, mono) an den Anbieter. */
    fun stt(access: ApiAccess, prompt: String, language: String): Outcome =
        timed { OnlineBackend(access, prompt).transcribe(silence(), language) }

    /** LLM-Zugang: kurzer Testsatz per Chat-Completion. */
    fun llm(access: ApiAccess, language: String): Outcome =
        timed { TextRefiner(access).refine(PROBE_TEXT, language, RefineMode.POLISH, smartFillers = false) }

    fun silence(): WavUpload = WavUpload.fromSamples(FloatArray(AudioUtils.SAMPLE_RATE))

    /** "0,8" — Sekunden mit einer Nachkommastelle fuer rec_test_ok. */
    fun seconds(millis: Long): String = String.format(Locale.GERMANY, "%.1f", millis / 1000.0)

    private fun timed(block: () -> Unit): Outcome {
        val t0 = System.nanoTime()
        return try {
            block()
            Outcome.Ok((System.nanoTime() - t0) / 1_000_000)
        } catch (e: Exception) {
            classify(e)
        }
    }
}
