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
import com.chris.whisperbar.ModelNotAvailableException
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.TextPolisher
import com.chris.whisperbar.WhisperEngine
import com.chris.whisperbar.SetupActivity
import com.chris.whisperbar.SettingsActivity
import java.util.concurrent.Executors

/**
 * Diktier-Tastatur: grosser Push-to-talk-Mikro-Knopf plus ein paar Basis-Tasten.
 * Halten = aufnehmen, loslassen = per whisper.cpp transkribieren und Text ins
 * aktive Feld schreiben. Modell wird lokal aus den App-Assets geladen.
 */
class WhisperBarInputMethodService : InputMethodService() {

    private lateinit var prefs: Prefs
    private val recorder = AudioRecorder()

    // Alle Whisper-/IO-Arbeiten seriell auf einem Hintergrund-Thread; UI ueber main.
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wb-ime-io") }
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var busy = false // Transkription laeuft -> keine neue Aufnahme

    private var statusView: TextView? = null
    private var levelView: View? = null
    private var micButton: ImageButton? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)
        statusView = root.findViewById(R.id.status)
        levelView = root.findViewById(R.id.level)
        micButton = root.findViewById(R.id.mic)

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

        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        resetLevel()
        setStatus(R.string.kb_hint_hold)
        // Modell im Hintergrund vorladen, damit das erste Diktat schneller ist.
        preload()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (recorder.isRecording) recorder.cancel()
        resetLevel()
        super.onFinishInputView(finishingInput)
    }

    // --- Diktat -------------------------------------------------------------

    private fun startDictation() {
        if (busy) return // vorheriges Diktat wird noch transkribiert
        if (!hasMicPermission()) {
            setStatus(R.string.kb_need_permission)
            openSetup()
            return
        }
        if (recorder.isRecording) return
        if (recorder.start()) {
            setStatus(R.string.kb_listening)
            micButton?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.recording))
            preload()
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
        val language = prefs.language
        val options = prefs.polishOptions()
        io.submit {
            try {
                // ... aber stop() (join + PCM->Float) und Transkription bewusst auf dem
                // io-Thread, NIE auf dem UI-Thread (sonst Freeze/ANR beim Loslassen).
                val samples = recorder.stop()
                // Sehr kurze Aufnahmen (< 0,3 s) verwerfen — meist versehentliche Taps.
                if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) {
                    main.post { setStatus(R.string.kb_hint_hold) }
                    return@submit
                }
                val raw = WhisperEngine.transcribe(applicationContext, samples, language)
                val polished = TextPolisher.polish(raw, options)
                main.post {
                    commitDictation(polished)
                    setStatus(R.string.kb_hint_hold)
                }
            } catch (e: ModelNotAvailableException) {
                main.post { setStatus(R.string.model_not_loaded) }
            } catch (e: Exception) {
                Log.e(TAG, "Transkription fehlgeschlagen", e)
                main.post { setStatus(R.string.kb_error) }
            } finally {
                busy = false
            }
        }
    }

    /** Modell im Hintergrund vorladen (prozessweit geteilt via WhisperEngine). */
    private fun preload() {
        io.submit {
            try {
                WhisperEngine.preload(applicationContext)
            } catch (e: Exception) {
                Log.e(TAG, "Modell-Preload fehlgeschlagen", e)
            }
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

    private fun resetLevel() {
        levelView?.scaleX = 0f
    }

    override fun onDestroy() {
        if (recorder.isRecording) recorder.cancel()
        // Engine NICHT freigeben — sie ist prozessweit geteilt (auch vom Overlay genutzt).
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhisperBarIME"
    }
}
