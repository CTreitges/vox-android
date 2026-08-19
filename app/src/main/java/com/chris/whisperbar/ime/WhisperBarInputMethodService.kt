package com.chris.whisperbar.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.inputmethodservice.InputMethodService
import com.chris.whisperbar.AudioRecorder
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.TranscriptionEngine
import com.chris.whisperbar.SetupActivity
import com.chris.whisperbar.SettingsActivity
import com.chris.whisperbar.api.ApiNotConfiguredException
import com.chris.whisperbar.api.isRetryable
import java.util.concurrent.Executors

/**
 * Diktier-Tastatur: grosser Push-to-talk-Mikro-Knopf plus ein paar Basis-Tasten.
 * Halten = aufnehmen, loslassen = ueber die API transkribieren und Text ins aktive
 * Feld schreiben.
 *
 * Scheitert die Anfrage (kein Netz, Server-Aussetzer), bleibt das Audio gepuffert und
 * die Wiederholen-Taste erscheint — sonst waere ein langes Diktat verloren.
 */
class WhisperBarInputMethodService : InputMethodService() {

    private lateinit var prefs: Prefs
    private val recorder = AudioRecorder()

    // Alle Netz-/IO-Arbeiten seriell auf einem Hintergrund-Thread; UI ueber main.
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wb-ime-io") }
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var busy = false // Anfrage laeuft -> keine neue Aufnahme

    /** Audio des letzten fehlgeschlagenen Versuchs. */
    private var pendingSamples: FloatArray? = null

    private var statusView: TextView? = null
    private var levelView: View? = null
    private var micButton: ImageButton? = null
    private var retryButton: Button? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)
        statusView = root.findViewById(R.id.status)
        levelView = root.findViewById(R.id.level)
        micButton = root.findViewById(R.id.mic)
        retryButton = root.findViewById(R.id.key_retry)

        recorder.onAmplitude = { amp ->
            main.post { levelView?.scaleX = amp.coerceIn(0f, 1f) }
        }

        micButton?.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startDictation(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopDictation(); true }
                else -> false
            }
        }

        root.findViewById<Button>(R.id.key_globe).setOnClickListener { showImePicker() }
        root.findViewById<Button>(R.id.key_comma).setOnClickListener { commitRaw(", ") }
        root.findViewById<Button>(R.id.key_period).setOnClickListener { commitRaw(". ") }
        root.findViewById<Button>(R.id.key_space).setOnClickListener { commitRaw(" ") }
        root.findViewById<Button>(R.id.key_backspace).setOnClickListener { backspace() }
        root.findViewById<Button>(R.id.key_enter).setOnClickListener { performEnter() }
        root.findViewById<Button>(R.id.key_settings).setOnClickListener { openSettings() }
        retryButton?.setOnClickListener { retry() }

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetLevel()
        showRetry(false)
        setStatus(
            if (TranscriptionEngine.isConfigured(this)) R.string.kb_hint_hold
            else R.string.api_not_configured,
        )
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (recorder.isRecording) recorder.cancel()
        resetLevel()
        super.onFinishInputView(finishingInput)
    }

    // --- Diktat -------------------------------------------------------------

    private fun startDictation() {
        if (busy) return // vorheriges Diktat wird noch uebertragen
        if (!hasMicPermission()) {
            setStatus(R.string.kb_need_permission)
            openSetup()
            return
        }
        if (!TranscriptionEngine.isConfigured(this)) {
            setStatus(R.string.api_not_configured)
            openSettings()
            return
        }
        if (recorder.isRecording) return
        if (recorder.start()) {
            showRetry(false)
            pendingSamples = null
            setStatus(R.string.kb_listening)
            micButton?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.recording))
        } else {
            setStatus(R.string.kb_error)
        }
    }

    private fun stopDictation() {
        if (!recorder.isRecording) return
        // Sofortiges UI-Feedback auf dem Main-Thread ...
        micButton?.backgroundTintList = null
        resetLevel()
        setStatus(R.string.kb_transcribing)
        busy = true
        io.submit {
            // ... aber stop() (join + PCM->Float) und die Anfrage bewusst auf dem
            // io-Thread, NIE auf dem UI-Thread (sonst Freeze/ANR beim Loslassen).
            val samples = recorder.stop()
            // Sehr kurze Aufnahmen (< 0,3 s) verwerfen — meist versehentliche Taps.
            if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) {
                busy = false
                main.post { setStatus(R.string.kb_hint_hold) }
                return@submit
            }
            send(samples)
        }
    }

    private fun retry() {
        val samples = pendingSamples ?: return
        if (busy) return
        busy = true
        showRetry(false)
        setStatus(R.string.kb_transcribing)
        io.submit { send(samples) }
    }

    /** Laeuft auf dem io-Thread. */
    private fun send(samples: FloatArray) {
        try {
            val text = TranscriptionEngine.transcribe(applicationContext, samples)
            pendingSamples = null
            main.post {
                commitDictation(text)
                setStatus(R.string.kb_hint_hold)
            }
        } catch (e: ApiNotConfiguredException) {
            pendingSamples = null
            main.post { setStatus(R.string.api_not_configured) }
        } catch (e: Exception) {
            Log.e(TAG, "Transkription fehlgeschlagen", e)
            val retryable = e.isRetryable()
            pendingSamples = if (retryable) samples else null
            main.post {
                statusView?.text = e.message ?: getString(R.string.kb_error)
                showRetry(retryable)
            }
        } finally {
            busy = false
        }
    }

    // --- Text einfuegen -----------------------------------------------------

    private fun commitDictation(text: String) {
        val ic = currentInputConnection ?: return
        if (text.isEmpty()) return
        var out = text
        // Fuehrendes Leerzeichen, wenn direkt an ein Wort angefuegt wird.
        val before = ic.getTextBeforeCursor(1, 0)
        if (!before.isNullOrEmpty()) {
            val prev = before[0]
            if (!prev.isWhitespace() && out.isNotEmpty() && out[0].isLetterOrDigit()) {
                out = " $out"
            }
        }
        if (prefs.trailingSpace && !out.endsWith(" ")) out += " "
        ic.commitText(out, 1)
    }

    private fun commitRaw(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun backspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun performEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction =
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (action != EditorInfo.IME_ACTION_NONE && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
    }

    // --- Helfer -------------------------------------------------------------

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun showImePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    private fun openSetup() = startActivity(
        Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    private fun openSettings() = startActivity(
        Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    private fun setStatus(resId: Int) {
        statusView?.setText(resId)
    }

    private fun showRetry(visible: Boolean) {
        retryButton?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun resetLevel() {
        levelView?.scaleX = 0f
    }

    override fun onDestroy() {
        if (recorder.isRecording) recorder.cancel()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhisperBarIME"
    }
}
