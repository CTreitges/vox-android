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
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import com.chris.whisperbar.AudioRecorder
import com.chris.whisperbar.ModelNotAvailableException
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.SetupActivity
import com.chris.whisperbar.TextPolisher
import com.chris.whisperbar.WhisperEngine
import com.chris.whisperbar.a11y.TextInserterAccessibilityService
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Schwebender Mikro-Button (Overlay ueber allen Apps). Tippen = Aufnahme starten,
 * nochmal tippen = stoppen, transkribieren und den Text via Bedienungshilfe ins gerade
 * fokussierte Feld einfuegen. So bleibt Gboard die aktive Tastatur.
 *
 * Foreground-Service (Typ microphone), damit der Button dauerhaft sichtbar bleibt.
 */
class FloatingMicService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val recorder = AudioRecorder()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "wb-float-io") }
    private val main = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var micView: ImageView? = null
    private lateinit var lp: WindowManager.LayoutParams

    @Volatile private var recording = false
    @Volatile private var busy = false // Transkription laeuft -> Taps ignorieren

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
        io.submit { WhisperEngine.preload(applicationContext) }
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
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 320
        }
        v.setOnTouchListener(dragTapListener())
        wm.addView(v, lp)
        bubbleView = v
    }

    private fun dragTapListener() = object : View.OnTouchListener {
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
                    if (abs(dx) > 12 || abs(dy) > 12) moved = true
                    lp.x = startX + dx
                    lp.y = startY + dy
                    runCatching { wm.updateViewLayout(bubbleView, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            return true
        }
    }

    private fun onTap() {
        if (busy) return // waehrend laufender Transkription keine neue Aufnahme starten
        if (!recording) startRec() else stopRec()
    }

    // --- Aufnahme + Transkription -------------------------------------------

    private fun startRec() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.kb_need_permission))
            openSetup()
            return
        }
        if (recorder.start()) {
            recording = true
            micView?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.recording))
        } else {
            toast(getString(R.string.kb_error))
        }
    }

    private fun stopRec() {
        if (!recording) return
        recording = false
        busy = true
        micView?.backgroundTintList = ColorStateList.valueOf(getColor(R.color.accent_pressed))
        val language = prefs.language
        val options = prefs.polishOptions()
        val trailing = prefs.trailingSpace
        io.submit {
            try {
                val samples = recorder.stop()
                if (samples.size < AudioRecorder.SAMPLE_RATE * 3 / 10) {
                    main.post { resetBubble() }
                    return@submit
                }
                val raw = WhisperEngine.transcribe(applicationContext, samples, language)
                val text = TextPolisher.polish(raw, options).let { if (trailing && it.isNotEmpty()) "$it " else it }
                main.post {
                    if (text.isNotBlank()) {
                        if (!TextInserterAccessibilityService.tryInsert(text)) fallbackClipboard(text)
                    }
                    resetBubble()
                }
            } catch (e: ModelNotAvailableException) {
                main.post { toast(getString(R.string.model_not_loaded)); resetBubble() }
            } catch (e: Exception) {
                Log.e(TAG, "Transkription fehlgeschlagen", e)
                main.post { toast(getString(R.string.kb_error)); resetBubble() }
            }
        }
    }

    private fun resetBubble() {
        busy = false
        micView?.backgroundTintList = null
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
        const val ACTION_STOP = "com.chris.whisperbar.STOP_FLOAT"

        /** Ob der schwebende Button aktuell laeuft (fuer die Setup-Statusanzeige). */
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
