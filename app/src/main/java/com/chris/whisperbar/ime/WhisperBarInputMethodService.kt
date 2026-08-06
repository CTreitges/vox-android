package com.chris.whisperbar.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.chris.whisperbar.HomeActivity
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.SettingsActivity
import com.chris.whisperbar.WhisperEngine
import com.chris.whisperbar.dictation.DictationController
import com.chris.whisperbar.dictation.DictationPhase
import com.chris.whisperbar.ui.MicOrbView
import com.chris.whisperbar.ui.WaveformView

/**
 * Diktat-Tastatur.
 *
 * Bedienung: den Mikro-Orb **halten und sprechen** (klassisch) oder **kurz tippen** —
 * dann laeuft die Aufnahme freihaendig weiter und endet nach einer Sprechpause von
 * selbst. Beides ueber dieselbe Geste, ohne Moduswechsel; die Unterscheidung macht
 * [DictationController].
 *
 * Dazu eine Reihe Bearbeitungs-Tasten, damit man fuer eine kleine Korrektur nicht die
 * Tastatur wechseln muss — inklusive **"Diktat zurueck"**, das genau den zuletzt
 * eingefuegten Text wieder entfernt.
 */
class WhisperBarInputMethodService : InputMethodService(), DictationController.Listener {

    private lateinit var controller: DictationController
    private val main = Handler(Looper.getMainLooper())

    private var statusView: TextView? = null
    private var waveform: WaveformView? = null
    private var orb: MicOrbView? = null
    private var micIcon: ImageView? = null
    private var undoKey: ImageButton? = null

    /** Zuletzt per Diktat eingefuegter Text — Grundlage fuer "Diktat zurueck". */
    private var lastDictation: String? = null

    override fun onCreate() {
        super.onCreate()
        controller = DictationController(this, this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)
        statusView = root.findViewById(R.id.status)
        waveform = root.findViewById(R.id.waveform)
        orb = root.findViewById(R.id.mic)
        micIcon = root.findViewById(R.id.mic_icon)
        undoKey = root.findViewById(R.id.key_undo)

        // Der Orb selbst ist der Beruehrungsbereich; das Symbol darueber ist nur Deko.
        orb?.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    controller.onPressDown()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    controller.onPressUp()
                    true
                }
                // Alles selbst behandeln: sonst wuerde View.onTouchEvent den
                // Druckzustand bei jedem Wackeln des Fingers zuruecksetzen.
                else -> true
            }
        }
        // Fuer Bedienungshilfen: dort kommen keine Touch-Events an, nur ein Klick.
        orb?.setOnClickListener { controller.toggle() }

        root.findViewById<View>(R.id.key_globe).setOnClickListener { showImePicker() }
        root.findViewById<View>(R.id.key_settings).setOnClickListener { openSettings() }
        root.findViewById<View>(R.id.key_comma).setOnClickListener { commitRaw(", ") }
        root.findViewById<View>(R.id.key_period).setOnClickListener { commitRaw(". ") }
        root.findViewById<View>(R.id.key_space).setOnClickListener { commitRaw(" ") }
        root.findViewById<View>(R.id.key_enter).setOnClickListener { performEnter() }
        undoKey?.setOnClickListener { undoDictation() }

        // Loeschen mit Wiederholung: einmal antippen loescht ein Zeichen, gedrueckt
        // halten loescht fortlaufend — sonst tippt man bei einem falschen Wort ewig.
        root.findViewById<View>(R.id.key_backspace)
            .setOnTouchListener(RepeatingTouchListener { backspace() })

        applyPhase(controller.phase)
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lastDictation = null
        waveform?.reset()
        applyPhase(controller.phase)
        // Modell vorwaermen, solange der Nutzer noch ueberlegt.
        WhisperEngine.preloadAsync(this)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.cancel()
        waveform?.reset()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Sobald der Cursor woanders steht, passt das gemerkte Diktat nicht mehr.
        if (newSelStart != newSelEnd) forgetUndo()
    }

    // --- DictationController.Listener ---------------------------------------

    override fun onPhase(phase: DictationPhase) = applyPhase(phase)

    override fun onLevel(level: Float) {
        orb?.setLevel(level)
        waveform?.push(level)
    }

    override fun onResult(text: String) {
        commitDictation(text)
    }

    override fun onError(messageRes: Int) {
        setStatus(messageRes)
        if (messageRes == R.string.kb_need_permission) openHome()
        // Nach ein paar Sekunden zurueck auf den normalen Hinweis.
        main.removeCallbacks(resetStatus)
        main.postDelayed(resetStatus, ERROR_VISIBLE_MS)
    }

    private val resetStatus = Runnable { applyPhase(controller.phase) }

    private fun applyPhase(phase: DictationPhase) {
        main.removeCallbacks(resetStatus)
        orb?.phase = phase
        micIcon?.setImageResource(
            if (phase == DictationPhase.RECORDING) R.drawable.ic_stop else R.drawable.ic_mic,
        )
        when (phase) {
            DictationPhase.IDLE -> {
                waveform?.reset()
                setStatus(
                    if (Prefs.get(this).handsFree) R.string.kb_hint_idle
                    else R.string.kb_hint_hold,
                )
            }
            DictationPhase.RECORDING -> setStatus(R.string.kb_listening)
            DictationPhase.WORKING -> {
                waveform?.reset()
                setStatus(R.string.kb_transcribing)
            }
        }
        updateUndoState()
    }

    // --- Text einfuegen -----------------------------------------------------

    private fun commitDictation(text: String) {
        val ic = currentInputConnection ?: return
        var out = text
        // Fuehrendes Leerzeichen, wenn direkt an ein Wort angefuegt wird.
        val before = ic.getTextBeforeCursor(1, 0)
        if (!before.isNullOrEmpty()) {
            val prev = before[0]
            if (!prev.isWhitespace() && out.isNotEmpty() && out[0].isLetterOrDigit()) {
                out = " $out"
            }
        }
        ic.commitText(out, 1)
        lastDictation = out
        updateUndoState()
    }

    /**
     * Entfernt genau den zuletzt diktierten Text wieder — aber nur, wenn er unveraendert
     * direkt vor dem Cursor steht. Sonst wuerde man fremden Text loeschen.
     */
    private fun undoDictation() {
        val text = lastDictation ?: return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(text.length, 0)
        if (before?.toString() == text) {
            ic.deleteSurroundingText(text.length, 0)
        }
        forgetUndo()
    }

    private fun forgetUndo() {
        if (lastDictation == null) return
        lastDictation = null
        updateUndoState()
    }

    private fun updateUndoState() {
        val enabled = lastDictation != null && controller.phase == DictationPhase.IDLE
        undoKey?.let {
            it.isEnabled = enabled
            it.alpha = if (enabled) 1f else DISABLED_ALPHA
        }
    }

    private fun commitRaw(s: String) {
        currentInputConnection?.commitText(s, 1)
        forgetUndo()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        // Ausgewaehlten Text loeschen, sonst ein Zeichen davor.
        if (ic.getSelectedText(0).isNullOrEmpty()) ic.deleteSurroundingText(1, 0)
        else ic.commitText("", 1)
        forgetUndo()
    }

    private fun performEnter() {
        val ic: InputConnection = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        val noEnterAction =
            ((info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (action != EditorInfo.IME_ACTION_NONE && !noEnterAction) {
            ic.performEditorAction(action)
        } else {
            ic.commitText("\n", 1)
        }
        forgetUndo()
    }

    // --- Helfer -------------------------------------------------------------

    private fun showImePicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager)?.showInputMethodPicker()
    }

    private fun openHome() = startActivity(
        Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun openSettings() = startActivity(
        Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun setStatus(resId: Int) {
        statusView?.setText(resId)
    }

    override fun onDestroy() {
        main.removeCallbacks(resetStatus)
        controller.shutdown()
        // Engine NICHT freigeben — sie ist prozessweit geteilt (auch vom Overlay genutzt).
        super.onDestroy()
    }

    private companion object {
        const val ERROR_VISIBLE_MS = 2_500L
        const val DISABLED_ALPHA = 0.32f
    }
}

/**
 * Loest [action] beim Druecken aus und wiederholt sie beim Halten — fuer die
 * Loeschtaste. Bewusst hier statt als globale Utility: es ist die einzige Taste,
 * die das braucht.
 */
private class RepeatingTouchListener(
    private val action: () -> Unit,
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    private val repeat = object : Runnable {
        override fun run() {
            val v = view ?: return
            if (!v.isPressed) return
            action()
            handler.postDelayed(this, REPEAT_MS)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                view = v
                v.isPressed = true
                action()
                handler.postDelayed(repeat, INITIAL_DELAY_MS)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.isPressed = false
                handler.removeCallbacks(repeat)
                view = null
                return true
            }
        }
        return false
    }

    private companion object {
        const val INITIAL_DELAY_MS = 400L
        const val REPEAT_MS = 55L
    }
}
