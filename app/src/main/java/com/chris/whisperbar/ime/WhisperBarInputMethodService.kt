package com.chris.whisperbar.ime

import android.Manifest
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.TextView
import com.chris.whisperbar.AppNav
import com.chris.whisperbar.AudioRecorder
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.TranscriptionEngine
import com.chris.whisperbar.api.ApiNotConfiguredException
import com.chris.whisperbar.api.isRetryable
import com.chris.whisperbar.overlay.BubbleAnimators
import com.chris.whisperbar.overlay.BubbleMotion
import com.chris.whisperbar.overlay.BubbleState
import com.chris.whisperbar.overlay.BubbleVisuals
import com.chris.whisperbar.overlay.MicIcon
import com.chris.whisperbar.overlay.MicRings
import java.util.concurrent.Executors

/**
 * Diktier-Tastatur (UX-Spec §5.3): grosser Push-to-talk-Mikro-Knopf mit denselben vier
 * Zustaenden wie der schwebende Knopf ([BubbleState]), Pegelband und ein paar Basis-Tasten.
 * Halten = aufnehmen, loslassen = transkribieren und Text ins aktive Feld schreiben.
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

    @Volatile private var state = BubbleState.IDLE

    /** Audio des letzten fehlgeschlagenen Versuchs. */
    private var pendingSamples: FloatArray? = null

    private var statusView: TextView? = null
    private var levelBand: LevelBandView? = null
    private var micZone: View? = null
    private var micButton: ImageButton? = null
    private var rings: MicRings? = null
    private var retryKey: View? = null

    /** Statuszeile: Farbe und Tipp-Ziel je Art (UX-Spec §5.3). */
    private enum class Status { HINT, LISTENING, TRANSCRIBING, ERROR, NEED_PERMISSION, NOT_CONFIGURED }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onCreateInputView(): View {
        val root = layoutInflater.inflate(R.layout.keyboard_view, null)
        statusView = root.findViewById(R.id.status)
        levelBand = root.findViewById(R.id.level)
        micZone = root.findViewById(R.id.mic_zone)
        micButton = root.findViewById(R.id.mic)
        retryKey = root.findViewById(R.id.key_retry)
        rings = MicRings(
            pulse = root.findViewById(R.id.mic_pulse),
            ring = root.findViewById(R.id.mic_ring),
            arc = root.findViewById(R.id.mic_progress),
            reduceMotion = ::reduceMotion,
        )
        applyKeyHeight(root)

        recorder.onAmplitude = { amp ->
            levelBand?.setLevel(amp)
            rings?.level = amp
        }

        micButton?.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startDictation(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { stopDictation(); true }
                else -> false
            }
        }

        root.findViewById<View>(R.id.key_globe).setOnClickListener { showImePicker() }
        root.findViewById<View>(R.id.key_comma).setOnClickListener { commitRaw(", ") }
        root.findViewById<View>(R.id.key_period).setOnClickListener { commitRaw(". ") }
        root.findViewById<View>(R.id.key_space).setOnClickListener { commitRaw(" ") }
        root.findViewById<View>(R.id.key_backspace).setOnClickListener { backspace() }
        root.findViewById<View>(R.id.key_enter).setOnClickListener { performEnter() }
        root.findViewById<View>(R.id.key_settings).setOnClickListener { startActivity(AppNav.settings(this)) }
        retryKey?.setOnClickListener { retry() }

        applyState(BubbleState.IDLE, animate = false)
        return root
    }

    /** Ab fontScale 1,3 werden die Tasten 56 statt 48 dp hoch (UX-Spec §5.3). */
    private fun applyKeyHeight(root: View) {
        val dp = ImeMetrics.keyHeightDp(resources.configuration.fontScale)
        if (dp == ImeMetrics.KEY_HEIGHT_DP) return
        val density = resources.displayMetrics.density
        val row = root.findViewById<ViewGroup>(R.id.key_row)
        row.layoutParams = row.layoutParams.apply { height = ((dp + KEY_ROW_EXTRA_DP) * density).toInt() }
        for (i in 0 until row.childCount) {
            val key = row.getChildAt(i)
            key.layoutParams = key.layoutParams.apply { height = (dp * density).toInt() }
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Ein alter Fehlerzustand gilt fuer das neue Feld nicht mehr; eine laufende
        // Uebertragung bleibt sichtbar.
        if (state == BubbleState.ERROR) {
            pendingSamples = null
            applyState(BubbleState.IDLE)
        }
        if (state != BubbleState.SENDING) showIdleStatus()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        if (recorder.isRecording) recorder.cancel()
        if (state == BubbleState.RECORDING) applyState(BubbleState.IDLE)
        levelBand?.stop()
        super.onFinishInputView(finishingInput)
    }

    // --- Diktat -------------------------------------------------------------

    private fun startDictation() {
        if (state == BubbleState.SENDING) return // vorheriges Diktat wird noch uebertragen
        if (!hasMicPermission()) {
            showStatus(Status.NEED_PERMISSION)
            startActivity(AppNav.setup(this, SETUP_STEP_MIC))
            return
        }
        if (!TranscriptionEngine.isConfigured(this)) {
            showStatus(Status.NOT_CONFIGURED)
            startActivity(AppNav.setup(this))
            return
        }
        if (recorder.isRecording) return
        if (recorder.start()) {
            pendingSamples = null
            applyState(BubbleState.RECORDING)
            showStatus(Status.LISTENING)
            haptic(BubbleMotion.Haptic.CONFIRM)
        } else {
            showStatus(Status.ERROR)
        }
    }

    private fun stopDictation() {
        if (!recorder.isRecording) return
        // Sofortiges UI-Feedback auf dem Main-Thread ...
        applyState(BubbleState.SENDING)
        showStatus(Status.TRANSCRIBING)
        haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        io.submit {
            // ... aber stop() (join + PCM->Float) und die Anfrage bewusst auf dem
            // io-Thread, NIE auf dem UI-Thread (sonst Freeze/ANR beim Loslassen).
            val samples = recorder.stop()
            // Sehr kurze Aufnahmen (< 0,3 s) verwerfen — meist versehentliche Taps.
            if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) {
                main.post {
                    applyState(BubbleState.IDLE)
                    showIdleStatus()
                }
                return@submit
            }
            send(samples)
        }
    }

    private fun retry() {
        val samples = pendingSamples ?: return
        if (state == BubbleState.SENDING) return
        applyState(BubbleState.SENDING)
        showStatus(Status.TRANSCRIBING)
        haptic(BubbleMotion.Haptic.CONTEXT_CLICK)
        io.submit { send(samples) }
    }

    /** Laeuft auf dem io-Thread. */
    private fun send(samples: FloatArray) {
        try {
            val text = TranscriptionEngine.transcribe(applicationContext, samples)
            pendingSamples = null
            main.post {
                commitDictation(text)
                applyState(BubbleState.IDLE)
                rings?.flashSuccess()
                showIdleStatus()
            }
        } catch (e: ApiNotConfiguredException) {
            pendingSamples = null
            main.post {
                applyState(BubbleState.IDLE)
                showStatus(Status.NOT_CONFIGURED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transkription fehlgeschlagen", e)
            val retryable = e.isRetryable()
            pendingSamples = if (retryable) samples else null
            main.post {
                applyState(if (retryable) BubbleState.ERROR else BubbleState.IDLE)
                showStatus(Status.ERROR, e.message ?: getString(R.string.kb_error))
                if (!reduceMotion()) micZone?.let { BubbleAnimators.shake(it).start() }
                haptic(BubbleMotion.Haptic.REJECT)
            }
        }
    }

    // --- Anzeige ------------------------------------------------------------

    /** Mikro-Taste (Fuellung, Icon, Ringe), Wiederholen-Taste und Pegelband auf [next] setzen. */
    private fun applyState(next: BubbleState, animate: Boolean = true) {
        state = next
        val visual = BubbleVisuals.visualFor(next, reduceMotion = reduceMotion())
        micButton?.let {
            it.background.level = ImeMetrics.micFillLevel(visual)
            MicIcon.apply(it, visual, animate = animate && !reduceMotion())
        }
        rings?.show(visual.ring)
        retryKey?.visibility = if (next == BubbleState.ERROR) View.VISIBLE else View.GONE
        if (next == BubbleState.RECORDING) levelBand?.start() else levelBand?.stop()
    }

    /** Ruhe-Statuszeile: Hinweis oder Warnung (fehlende Berechtigung / kein Zugang). */
    private fun showIdleStatus() = showStatus(
        when {
            !hasMicPermission() -> Status.NEED_PERMISSION
            !TranscriptionEngine.isConfigured(this) -> Status.NOT_CONFIGURED
            else -> Status.HINT
        },
    )

    private fun showStatus(kind: Status, text: CharSequence? = null) {
        val v = statusView ?: return
        v.text = text ?: getString(
            when (kind) {
                Status.HINT -> R.string.kb_hint_hold
                Status.LISTENING -> R.string.kb_listening
                Status.TRANSCRIBING -> R.string.kb_transcribing
                Status.ERROR -> R.string.kb_error
                Status.NEED_PERMISSION -> R.string.kb_need_permission
                Status.NOT_CONFIGURED -> R.string.kb_not_configured
            },
        )
        v.setTextColor(
            getColor(
                when (kind) {
                    Status.HINT, Status.TRANSCRIBING -> R.color.wb_onSurfaceVariant
                    Status.LISTENING -> R.color.wb_recordingText
                    Status.ERROR -> R.color.wb_error
                    Status.NEED_PERMISSION, Status.NOT_CONFIGURED -> R.color.wb_warning
                },
            ),
        )
        // Warnzeilen fuehren per Tipp in den Assistenten (Mikrofon = Schritt 3).
        when (kind) {
            Status.NEED_PERMISSION -> v.setOnClickListener { startActivity(AppNav.setup(this, SETUP_STEP_MIC)) }
            Status.NOT_CONFIGURED -> v.setOnClickListener { startActivity(AppNav.setup(this)) }
            else -> v.setOnClickListener(null)
        }
        v.isClickable = kind == Status.NEED_PERMISSION || kind == Status.NOT_CONFIGURED
    }

    private fun reduceMotion() = BubbleAnimators.reduceMotion(this)

    private fun haptic(kind: BubbleMotion.Haptic) {
        micButton?.performHapticFeedback(BubbleMotion.hapticConstant(kind, Build.VERSION.SDK_INT))
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

    override fun onDestroy() {
        if (recorder.isRecording) recorder.cancel()
        rings?.release()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WhisperBarIME"

        /** Assistenten-Schritt "Mikrofon erlauben" (UX-Spec §2.2). */
        private const val SETUP_STEP_MIC = 3

        /** Tastenreihe ist 4 dp hoeher als die Tasten (52/48 bzw. 60/56). */
        private const val KEY_ROW_EXTRA_DP = 4
    }
}
