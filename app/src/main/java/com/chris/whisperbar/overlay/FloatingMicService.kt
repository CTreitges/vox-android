package com.chris.whisperbar.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.chris.whisperbar.AudioRecorder
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.SetupActivity
import com.chris.whisperbar.TranscriptionEngine
import com.chris.whisperbar.a11y.TextInserterAccessibilityService
import com.chris.whisperbar.api.ApiNotConfiguredException
import com.chris.whisperbar.api.isRetryable
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Schwebender Mikro-Knopf (Overlay ueber allen Apps). Tippen startet und beendet das
 * Diktat, der erkannte Text wird per Bedienungshilfe ins fokussierte Feld eingefuegt —
 * Gboard bleibt dabei die aktive Tastatur.
 *
 * Der Knopf zeigt vier Zustaende ([BubbleState]), weil die Transkription ueber das Netz
 * laeuft und spuerbar dauert: bereit, nimmt auf (mit Timer), sendet, fehlgeschlagen.
 * Ein fehlgeschlagenes Diktat bleibt gepuffert und kann per Tippen erneut gesendet
 * werden; Ziehen auf das ✕ am unteren Rand verwirft es.
 *
 * Foreground-Service (Typ microphone), damit der Knopf dauerhaft sichtbar bleibt.
 */
class FloatingMicService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val recorder = AudioRecorder()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wb-float-io") }
    private val main = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var micView: ImageView? = null
    private var progressView: ProgressBar? = null
    private var labelView: TextView? = null
    private var cancelView: View? = null
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var cancelLp: WindowManager.LayoutParams

    @Volatile private var state = BubbleState.IDLE

    /** Audio des letzten fehlgeschlagenen Versuchs — Grundlage fuer den Wiederholen-Tipp. */
    private var pendingSamples: FloatArray? = null

    private var recordingStartedAt = 0L
    private val tick = object : Runnable {
        override fun run() {
            if (state != BubbleState.RECORDING) return
            labelView?.text = BubbleUi.formatDuration(SystemClock.elapsedRealtime() - recordingStartedAt)
            main.postDelayed(this, 250)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        try {
            startAsForeground()
        } catch (e: Exception) {
            Log.e(TAG, "Foreground-Start fehlgeschlagen", e)
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.float_no_overlay))
            stopSelf()
            return
        }
        addBubble()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    // --- Foreground-Notification ---------------------------------------------

    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID, getString(R.string.float_channel), NotificationManager.IMPORTANCE_LOW,
        )
        nm.createNotificationChannel(channel)

        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingMicService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.float_running))
            .setContentText(getString(R.string.float_running_text))
            .addAction(R.drawable.ic_mic, getString(R.string.float_stop), stopPi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // --- Bubble --------------------------------------------------------------

    private fun addBubble() {
        val v = LayoutInflater.from(this).inflate(R.layout.floating_mic, null)
        micView = v.findViewById(R.id.bubble_mic)
        progressView = v.findViewById(R.id.bubble_progress)
        labelView = v.findViewById(R.id.bubble_label)
        // Gemerkte Position wiederherstellen; die Bubble-Groesse steht vor dem Layout
        // noch nicht fest, deshalb hier mit 0 clampen (haelt sie im Bildschirm) und
        // beim ersten Ziehen exakt nachziehen.
        val start = clampToScreen(prefs.floatX, prefs.floatY, 0, 0)
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = start.x
            y = start.y
        }
        v.setOnTouchListener(dragTapListener())
        wm.addView(v, lp)
        bubbleView = v
        applyState(BubbleState.IDLE)
    }

    private fun clampToScreen(x: Int, y: Int, width: Int, height: Int): BubblePos {
        val dm = resources.displayMetrics
        return BubblePosition.clamp(x, y, width, height, dm.widthPixels, dm.heightPixels)
    }

    private fun savePosition() {
        prefs.floatX = lp.x
        prefs.floatY = lp.y
    }

    // --- Abbrechen-Ziel ------------------------------------------------------

    /** Nur zeigen, wenn es auch etwas zu verwerfen gibt. */
    private fun canDiscard() = state == BubbleState.RECORDING || state == BubbleState.ERROR

    private fun showCancelTarget() {
        if (cancelView != null) return
        val v = LayoutInflater.from(this).inflate(R.layout.floating_cancel, null)
        val dm = resources.displayMetrics
        val size = (CANCEL_SIZE_DP * dm.density).toInt()
        cancelLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (dm.widthPixels - size) / 2
            y = dm.heightPixels - size - (CANCEL_MARGIN_DP * dm.density).toInt()
        }
        runCatching { wm.addView(v, cancelLp) }
        cancelView = v
    }

    private fun hideCancelTarget() {
        cancelView?.let { runCatching { wm.removeView(it) } }
        cancelView = null
    }

    private fun isOverCancelTarget(): Boolean {
        val cancel = cancelView ?: return false
        val bubble = bubbleView ?: return false
        val radius = (CANCEL_HIT_RADIUS_DP * resources.displayMetrics.density).toInt()
        return BubblePosition.isOverCancel(
            lp.x, lp.y, bubble.width, bubble.height,
            cancelLp.x, cancelLp.y, cancel.width, cancel.height,
            radius,
        )
    }

    private fun dragTapListener() = object : View.OnTouchListener {
        // Systemweite Schwelle statt fester Pixelzahl: 12 px sind auf einem dichten
        // Display nur ~3 dp — dann galt schon ein leichtes Zittern beim Tippen als
        // Ziehen (Diktat startete nicht), waehrend eine bewusste kleine Korrektur der
        // Position umgekehrt als Tippen durchging und die Aufnahme startete.
        private val touchSlop = ViewConfiguration.get(this@FloatingMicService).scaledTouchSlop

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        override fun onTouch(view: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        if (!moved && canDiscard()) showCancelTarget()
                        moved = true
                    }
                    if (!moved) return true // unter der Schwelle: noch nicht verschieben
                    val p = clampToScreen(startX + dx, startY + dy, view.width, view.height)
                    lp.x = p.x
                    lp.y = p.y
                    runCatching { wm.updateViewLayout(bubbleView, lp) }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        onTap()
                    } else if (isOverCancelTarget()) {
                        hideCancelTarget()
                        // Der Knopf soll nach dem Verwerfen nicht ueber dem ✕ liegen
                        // bleiben — zurueck an die gemerkte Position.
                        restorePosition()
                        discard()
                    } else {
                        hideCancelTarget()
                        savePosition()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideCancelTarget()
                    if (moved) savePosition()
                }
            }
            return true
        }
    }

    private fun restorePosition() {
        val p = clampToScreen(prefs.floatX, prefs.floatY, 0, 0)
        lp.x = p.x
        lp.y = p.y
        runCatching { wm.updateViewLayout(bubbleView, lp) }
    }

    private fun onTap() = when (state) {
        BubbleState.IDLE -> startRec()
        BubbleState.RECORDING -> stopRec()
        BubbleState.SENDING -> Unit // laeuft schon
        BubbleState.ERROR -> retry()
    }

    // --- Zustands-Anzeige ----------------------------------------------------

    private fun applyState(next: BubbleState) {
        state = next
        main.removeCallbacks(tick)
        micView?.backgroundTintList = when (next) {
            BubbleState.IDLE -> null
            BubbleState.RECORDING -> ColorStateList.valueOf(getColor(R.color.recording))
            BubbleState.SENDING -> ColorStateList.valueOf(getColor(R.color.accent_pressed))
            BubbleState.ERROR -> ColorStateList.valueOf(getColor(R.color.recording))
        }
        micView?.alpha = if (next == BubbleState.SENDING) 0.35f else 1f
        progressView?.visibility = if (next == BubbleState.SENDING) View.VISIBLE else View.GONE

        when (next) {
            BubbleState.IDLE -> labelView?.visibility = View.GONE
            BubbleState.RECORDING -> {
                labelView?.text = BubbleUi.formatDuration(0)
                labelView?.visibility = View.VISIBLE
                main.post(tick)
            }
            BubbleState.SENDING -> {
                labelView?.setText(R.string.float_sending)
                labelView?.visibility = View.VISIBLE
            }
            BubbleState.ERROR -> {
                labelView?.setText(R.string.float_retry_hint)
                labelView?.visibility = View.VISIBLE
            }
        }
    }

    // --- Aufnahme + Transkription -------------------------------------------

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.kb_need_permission))
            openSetup()
            return
        }
        if (!TranscriptionEngine.isConfigured(this)) {
            toast(getString(R.string.api_not_configured))
            openSetup()
            return
        }
        if (recorder.start()) {
            recordingStartedAt = SystemClock.elapsedRealtime()
            pendingSamples = null
            applyState(BubbleState.RECORDING)
        } else {
            toast(getString(R.string.kb_error))
        }
    }

    private fun stopRec() {
        if (state != BubbleState.RECORDING) return
        applyState(BubbleState.SENDING)
        io.submit {
            val samples = recorder.stop()
            // Sehr kurze Aufnahmen (< 0,3 s) verwerfen — meist versehentliche Taps.
            if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) {
                main.post { applyState(BubbleState.IDLE) }
                return@submit
            }
            send(samples)
        }
    }

    private fun retry() {
        val samples = pendingSamples
        if (samples == null) {
            applyState(BubbleState.IDLE)
            return
        }
        applyState(BubbleState.SENDING)
        io.submit { send(samples) }
    }

    /** Verwirft eine laufende Aufnahme oder das gepufferte Audio. */
    private fun discard() {
        if (state == BubbleState.RECORDING) recorder.cancel()
        pendingSamples = null
        applyState(BubbleState.IDLE)
        toast(getString(R.string.float_discarded))
    }

    /** Laeuft auf dem io-Thread. */
    private fun send(samples: FloatArray) {
        try {
            val text = TranscriptionEngine.transcribe(applicationContext, samples)
            val out = if (prefs.trailingSpace && text.isNotEmpty()) "$text " else text
            pendingSamples = null
            main.post {
                if (out.isNotBlank()) {
                    if (!TextInserterAccessibilityService.tryInsert(out)) fallbackClipboard(out)
                }
                applyState(BubbleState.IDLE)
            }
        } catch (e: ApiNotConfiguredException) {
            pendingSamples = null
            main.post {
                toast(getString(R.string.api_not_configured))
                applyState(BubbleState.IDLE)
                openSetup()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transkription fehlgeschlagen", e)
            // Nur puffern, wenn ein zweiter Versuch ueberhaupt Sinn hat — sonst
            // haengt der Knopf dauerhaft im Fehlerzustand.
            val retryable = e.isRetryable()
            pendingSamples = if (retryable) samples else null
            main.post {
                toast(e.message ?: getString(R.string.kb_error))
                applyState(if (retryable) BubbleState.ERROR else BubbleState.IDLE)
            }
        }
    }

    private fun fallbackClipboard(text: String) {
        runCatching {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("whisperbar", text.trim()))
        }
        toast(getString(R.string.float_clipboard_fallback))
    }

    private fun openSetup() = startActivity(
        Intent(this, SetupActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )

    private fun toast(msg: String) = main.post {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        isRunning = false
        main.removeCallbacks(tick)
        hideCancelTarget()
        runCatching { bubbleView?.let { wm.removeView(it) } }
        bubbleView = null
        if (recorder.isRecording) recorder.cancel()
        io.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingMic"
        private const val NOTIF_ID = 42
        private const val CHANNEL_ID = "whisperbar_float"
        private const val CANCEL_SIZE_DP = 64
        private const val CANCEL_MARGIN_DP = 96
        private const val CANCEL_HIT_RADIUS_DP = 72
        const val ACTION_STOP = "com.chris.whisperbar.STOP_FLOAT"

        /** Ob der schwebende Knopf aktuell laeuft (fuer die Setup-Statusanzeige). */
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val i = Intent(context, FloatingMicService::class.java)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingMicService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
