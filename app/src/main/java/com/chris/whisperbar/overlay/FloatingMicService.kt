package com.chris.whisperbar.overlay

import android.Manifest
import android.animation.ValueAnimator
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
import android.graphics.PixelFormat
import android.graphics.Point
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
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.chris.whisperbar.HomeActivity
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.WhisperEngine
import com.chris.whisperbar.a11y.TextInserterAccessibilityService
import com.chris.whisperbar.dictation.DictationController
import com.chris.whisperbar.dictation.DictationPhase
import com.chris.whisperbar.ui.MicOrbView
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Schwebender Mikro-Knopf ueber allen Apps. Diktieren, ohne die Tastatur zu wechseln —
 * der Text landet per Bedienungshilfe im gerade fokussierten Feld, Gboard bleibt aktiv.
 *
 * ### Gesten
 * | Geste | Wirkung |
 * |---|---|
 * | Tippen | Diktat starten / beenden (endet auch nach einer Sprechpause von selbst) |
 * | Ziehen | Verschieben — rastet am naechsten Bildschirmrand ein, Position bleibt gespeichert |
 * | Auf ✕ ziehen | Laufende Aufnahme verwerfen bzw. den Knopf ausblenden |
 * | Lang druecken | Letztes Diktat zuruecknehmen |
 *
 * Im Ruhezustand ist der Knopf halbtransparent, damit er nicht stoert, und wird beim
 * Beruehren wieder voll sichtbar.
 */
class FloatingMicService : Service(), DictationController.Listener {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private lateinit var controller: DictationController
    private val main = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var orb: MicOrbView? = null
    private var icon: ImageView? = null
    private lateinit var lp: WindowManager.LayoutParams

    private var cancelView: View? = null
    private var cancelTarget: View? = null

    private var bubbleSize = 0
    private var overCancel = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs.get(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        // Ohne Mikrofon-Recht darf auf Android 14+ gar kein Vordergrunddienst vom Typ
        // "microphone" starten — das waere ein harter Absturz statt einer Fehlermeldung.
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.kb_need_permission))
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            toast(getString(R.string.float_no_overlay))
            stopSelf()
            return
        }
        try {
            startAsForeground()
        } catch (e: Exception) {
            Log.e(TAG, "Foreground-Start fehlgeschlagen", e)
            stopSelf()
            return
        }

        controller = DictationController(this, this)
        addBubble()
        isRunning = true
        WhisperEngine.preloadAsync(this)
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
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID, getString(R.string.float_channel), NotificationManager.IMPORTANCE_LOW,
            ),
        )

        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingMicService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openPi = PendingIntent.getActivity(
            this, 2,
            Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.float_running))
            .setContentText(getString(R.string.float_running_text))
            .setContentIntent(openPi)
            .addAction(R.drawable.ic_mic, getString(R.string.float_stop), stopPi)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    // --- Blase ---------------------------------------------------------------

    private fun addBubble() {
        val v = LayoutInflater.from(this).inflate(R.layout.floating_mic, null)
        orb = v.findViewById(R.id.bubble_orb)
        icon = v.findViewById(R.id.bubble_icon)
        bubbleSize = dp(BUBBLE_DP)

        val screen = screenSize()
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Gespeicherte Position wiederherstellen; beim ersten Start rechts mittig.
            x = prefs.bubbleX.takeIf { it != Int.MIN_VALUE } ?: (screen.x - bubbleSize)
            y = prefs.bubbleY.takeIf { it != Int.MIN_VALUE } ?: (screen.y / 2)
        }
        clampToScreen(screen)

        v.setOnTouchListener(BubbleTouchListener())
        orb?.setOnClickListener { controller.toggle() } // Bedienungshilfen
        wm.addView(v, lp)
        bubbleView = v
        applyPhase(DictationPhase.IDLE)
    }

    /**
     * Zieh- und Tipp-Erkennung in einem. Ein Tipp ist eine kurze Beruehrung ohne
     * nennenswerte Bewegung — alles andere ist ein Ziehen.
     */
    private inner class BubbleTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var downAt = 0L
        private var dragging = false
        private var longPressFired = false

        private val longPress = Runnable {
            longPressFired = true
            undoLastDictation()
        }

        override fun onTouch(view: View, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = lp.x; startY = lp.y
                    downAt = SystemClock.uptimeMillis()
                    dragging = false
                    longPressFired = false
                    bubbleView?.animate()?.alpha(1f)?.setDuration(80)?.start()
                    main.postDelayed(longPress, LONG_PRESS_MS)
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (!dragging && (abs(dx) > touchSlop() || abs(dy) > touchSlop())) {
                        dragging = true
                        main.removeCallbacks(longPress)
                        showCancelTarget()
                    }
                    if (dragging) {
                        lp.x = startX + dx
                        lp.y = startY + dy
                        clampToScreen(screenSize())
                        runCatching { wm.updateViewLayout(bubbleView, lp) }
                        updateCancelHighlight()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    if (dragging) {
                        val onTarget = overCancel
                        hideCancelTarget()
                        if (onTarget) {
                            onDroppedOnCancel()
                        } else {
                            snapToEdge()
                        }
                    } else if (!longPressFired &&
                        SystemClock.uptimeMillis() - downAt < LONG_PRESS_MS &&
                        e.actionMasked == MotionEvent.ACTION_UP
                    ) {
                        controller.toggle()
                    }
                    fadeIdleBubble()
                }
            }
            return true
        }
    }

    /** Auf ✕ abgelegt: laufende Aufnahme verwerfen, sonst den Knopf ausblenden. */
    private fun onDroppedOnCancel() {
        if (controller.isRecording) {
            controller.cancel()
            toast(getString(R.string.float_cancelled))
            snapToEdge()
        } else {
            stopSelf()
        }
    }

    private fun undoLastDictation() {
        val message = if (TextInserterAccessibilityService.tryUndo()) R.string.float_undone
        else R.string.float_undo_failed
        toast(getString(message))
    }

    // --- Positionierung ------------------------------------------------------

    private fun screenSize(): Point {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            return Point(b.width(), b.height())
        }
        @Suppress("DEPRECATION")
        return Point().also { wm.defaultDisplay.getSize(it) }
    }

    /** Haelt die Blase komplett im sichtbaren Bereich — auch nach einer Drehung. */
    private fun clampToScreen(screen: Point) {
        lp.x = lp.x.coerceIn(0, (screen.x - bubbleSize).coerceAtLeast(0))
        lp.y = lp.y.coerceIn(dp(TOP_INSET_DP), (screen.y - bubbleSize - dp(BOTTOM_INSET_DP)).coerceAtLeast(0))
    }

    /** Weich an den naechstgelegenen linken/rechten Rand ziehen und Position merken. */
    private fun snapToEdge() {
        val screen = screenSize()
        val maxX = (screen.x - bubbleSize).coerceAtLeast(0)
        val targetX = if (lp.x + bubbleSize / 2 < screen.x / 2) 0 else maxX
        val fromX = lp.x
        if (fromX == targetX) {
            savePosition()
            return
        }
        ValueAnimator.ofInt(fromX, targetX).apply {
            duration = SNAP_MS
            addUpdateListener {
                lp.x = it.animatedValue as Int
                runCatching { wm.updateViewLayout(bubbleView, lp) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) = savePosition()
            })
            start()
        }
    }

    private fun savePosition() {
        prefs.bubbleX = lp.x
        prefs.bubbleY = lp.y
    }

    // --- Ablegeziel ----------------------------------------------------------

    private fun showCancelTarget() {
        if (cancelView != null) return
        val v = LayoutInflater.from(this).inflate(R.layout.floating_cancel, null)
        cancelTarget = v.findViewById(R.id.cancel_target)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(CANCEL_BOTTOM_DP)
        }
        runCatching { wm.addView(v, params) }
        v.alpha = 0f
        v.animate().alpha(1f).setDuration(120).start()
        cancelView = v
    }

    private fun hideCancelTarget() {
        val v = cancelView ?: return
        cancelView = null
        cancelTarget = null
        overCancel = false
        runCatching { wm.removeView(v) }
    }

    /** Markiert das Ziel, sobald die Blase nah genug ist. */
    private fun updateCancelHighlight() {
        val target = cancelTarget ?: return
        val screen = screenSize()
        val targetCx = screen.x / 2f
        val targetCy = screen.y - dp(CANCEL_BOTTOM_DP) - target.height / 2f
        val bubbleCx = lp.x + bubbleSize / 2f
        val bubbleCy = lp.y + bubbleSize / 2f
        val near = hypot(bubbleCx - targetCx, bubbleCy - targetCy) < dp(CANCEL_RADIUS_DP)
        if (near != overCancel) {
            overCancel = near
            target.isActivated = near
            target.animate().scaleX(if (near) 1.2f else 1f).scaleY(if (near) 1.2f else 1f)
                .setDuration(90).start()
        }
    }

    // --- DictationController.Listener ---------------------------------------

    override fun onPhase(phase: DictationPhase) = applyPhase(phase)

    override fun onLevel(level: Float) {
        orb?.setLevel(level)
    }

    override fun onResult(text: String) {
        if (!TextInserterAccessibilityService.tryInsert(text)) fallbackClipboard(text)
    }

    override fun onError(messageRes: Int) {
        toast(getString(messageRes))
    }

    private fun applyPhase(phase: DictationPhase) {
        orb?.phase = phase
        icon?.setImageResource(
            if (phase == DictationPhase.RECORDING) R.drawable.ic_stop else R.drawable.ic_mic,
        )
        if (phase == DictationPhase.IDLE) fadeIdleBubble()
        else bubbleView?.animate()?.alpha(1f)?.setDuration(120)?.start()
    }

    /** Im Ruhezustand zuruecknehmen, damit der Knopf nicht dauerhaft im Weg ist. */
    private fun fadeIdleBubble() {
        if (controller.phase != DictationPhase.IDLE) return
        bubbleView?.animate()?.alpha(IDLE_ALPHA)?.setStartDelay(IDLE_FADE_DELAY_MS)
            ?.setDuration(300)?.start()
    }

    private fun fallbackClipboard(text: String) {
        runCatching {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("whisperbar", text.trim()))
        }
        toast(getString(R.string.float_clipboard_fallback))
    }

    // --- Helfer --------------------------------------------------------------

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun touchSlop() =
        android.view.ViewConfiguration.get(this).scaledTouchSlop

    private fun toast(msg: String) = main.post {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        isRunning = false
        hideCancelTarget()
        runCatching { bubbleView?.let { wm.removeView(it) } }
        bubbleView = null
        if (this::controller.isInitialized) controller.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingMic"
        private const val NOTIF_ID = 42
        private const val CHANNEL_ID = "whisperbar_float"
        const val ACTION_STOP = "com.chris.whisperbar.STOP_FLOAT"

        private const val BUBBLE_DP = 64
        private const val TOP_INSET_DP = 48
        private const val BOTTOM_INSET_DP = 24
        private const val CANCEL_BOTTOM_DP = 96
        private const val CANCEL_RADIUS_DP = 96
        private const val SNAP_MS = 180L
        private const val LONG_PRESS_MS = 550L
        private const val IDLE_ALPHA = 0.55f
        private const val IDLE_FADE_DELAY_MS = 1_500L

        /** Ob der schwebende Knopf aktuell laeuft (fuer die Statusanzeige im Hauptbildschirm). */
        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FloatingMicService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, FloatingMicService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
