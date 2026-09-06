package com.chris.whisperbar

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.chris.whisperbar.api.ApiNotConfiguredException
import java.util.concurrent.Executors

/**
 * Nimmt aus anderen Apps geteilte Audiodateien entgegen (Teilen-Menue) und transkribiert
 * sie — der Hauptfall sind WhatsApp-Sprachnachrichten.
 *
 * Der Text wird WORTGETREU ausgegeben: keine Fuellwort-Entfernung, keine KI-Glaettung.
 * Bei einer fremden Nachricht will man wissen, was gesagt wurde.
 *
 * Die Zwischenablage wird bewusst NICHT automatisch ueberschrieben — nur auf Knopfdruck.
 */
class ShareTranscribeActivity : Activity() {

    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wb-share-io") }
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var cancelled = false

    private lateinit var progressView: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var resultView: TextView
    private lateinit var actions: View

    private var uris: List<Uri> = emptyList()
    private var results: List<SharedTranscript> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share)

        progressView = findViewById(R.id.share_progress)
        statusView = findViewById(R.id.share_status)
        resultView = findViewById(R.id.share_result)
        actions = findViewById(R.id.share_actions)

        findViewById<Button>(R.id.share_copy).setOnClickListener { copyToClipboard() }
        findViewById<Button>(R.id.share_forward).setOnClickListener { forward() }
        findViewById<Button>(R.id.share_retry).setOnClickListener { start() }

        uris = incomingUris(intent)
        if (uris.isEmpty()) {
            fail(getString(R.string.share_no_audio))
            return
        }
        start()
    }

    /** ACTION_SEND liefert eine Datei, ACTION_SEND_MULTIPLE mehrere. */
    private fun incomingUris(intent: Intent?): List<Uri> {
        if (intent == null) return emptyList()
        return when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty().filterNotNull()
            else -> emptyList()
        }
    }

    // --- Ablauf ---------------------------------------------------------------

    private fun start() {
        cancelled = false
        results = emptyList()
        actions.visibility = View.GONE
        resultView.text = ""
        progressView.visibility = View.VISIBLE
        statusView.visibility = View.VISIBLE
        statusView.setText(R.string.share_starting)

        io.submit {
            val done = mutableListOf<SharedTranscript>()
            val failures = mutableListOf<String>()
            for ((index, uri) in uris.withIndex()) {
                if (cancelled) return@submit
                try {
                    val t = SharedAudioTranscriber.transcribe(
                        context = this,
                        uri = uri,
                        onProgress = { step, total, label ->
                            main.post { showProgress(index, step, total, label) }
                        },
                        isCancelled = { cancelled },
                    )
                    done.add(t)
                    // Zwischenergebnis sofort zeigen — bei mehreren Dateien wartet man
                    // sonst bis zum Schluss auf den ersten Text.
                    main.post { showResults(done, failures, partial = true) }
                } catch (e: ApiNotConfiguredException) {
                    main.post { fail(getString(R.string.api_not_configured)) }
                    return@submit
                } catch (e: Exception) {
                    Log.e(TAG, "Geteiltes Audio fehlgeschlagen", e)
                    failures.add(e.message ?: getString(R.string.kb_error))
                }
            }
            if (cancelled) return@submit
            main.post {
                if (done.isEmpty()) {
                    fail(failures.firstOrNull() ?: getString(R.string.kb_error))
                } else {
                    showResults(done, failures, partial = false)
                }
            }
        }
    }

    private fun showProgress(fileIndex: Int, step: Int, total: Int, label: String) {
        val prefix = if (uris.size > 1) {
            getString(R.string.share_file_of, fileIndex + 1, uris.size) + " · "
        } else {
            ""
        }
        val chunkPart = if (total > 1) " ${step + 1}/$total" else ""
        statusView.text = "$prefix$label$chunkPart"
    }

    private fun showResults(
        done: List<SharedTranscript>,
        failures: List<String>,
        partial: Boolean,
    ) {
        results = done.toList()
        progressView.visibility = if (partial) View.VISIBLE else View.GONE
        statusView.visibility = if (partial) View.VISIBLE else View.GONE
        actions.visibility = if (partial) View.GONE else View.VISIBLE
        resultView.text = render(done, failures)
    }

    /** Bei mehreren Dateien je Abschnitt eine Ueberschrift mit Quelle und Dauer. */
    private fun render(done: List<SharedTranscript>, failures: List<String>): String {
        val sb = StringBuilder()
        for (t in done) {
            if (done.size > 1 || failures.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("\n\n")
                sb.append("— ${t.source} · ${Formats.duration(t.durationMs)} —\n")
            }
            sb.append(textOf(t).ifBlank { getString(R.string.share_nothing_recognised) })
        }
        for (f in failures) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(getString(R.string.share_one_failed, f))
        }
        return sb.toString()
    }

    private fun fail(message: String) {
        progressView.visibility = View.GONE
        statusView.visibility = View.GONE
        actions.visibility = View.VISIBLE
        resultView.text = message
        results = emptyList()
    }

    // --- Aktionen -------------------------------------------------------------

    /** Vorerst die Fassung ohne Fuellwoerter, absatzweise — der Umschalter kommt mit dem Compose-UI. */
    private fun textOf(t: SharedTranscript): String = t.paragraphsCleaned.joinToString("\n\n")

    /** Nur der reine Text, ohne die Ueberschriften der Ergebnis-Ansicht. */
    private fun plainText(): String =
        results.joinToString("\n\n") { textOf(it) }.ifBlank { resultView.text.toString() }

    private fun copyToClipboard() {
        val text = plainText()
        if (text.isBlank()) return
        runCatching {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("whisperbar", text))
        }
        Toast.makeText(this, R.string.share_copied, Toast.LENGTH_SHORT).show()
    }

    private fun forward() {
        val text = plainText()
        if (text.isBlank()) return
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                getString(R.string.share_forward),
            ),
        )
    }

    override fun onDestroy() {
        cancelled = true
        io.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhisperBarShare"
    }
}
