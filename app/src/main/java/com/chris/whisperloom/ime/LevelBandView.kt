package com.chris.whisperloom.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import com.chris.whisperloom.R

/**
 * Pegelband der Diktier-Tastatur (UX-Spec §5.3): 21 Balken (3 dp breit, 4 dp Luecke,
 * Radius 1,5), Hoehe 4–24 dp. Die Rechnung steckt in [LevelBand]; hier nur Zeichnen und
 * der Frame-Takt. Ohne Aufnahme liegen die Balken flach in loom_surfaceContainerHighest,
 * waehrend der Aufnahme leuchten sie in loom_recording.
 */
class LevelBandView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val barWidth = 3f * density
    private val gap = 4f * density
    private val radius = 1.5f * density
    private val minHeight = 4f * density
    private val maxHeight = 24f * density

    private val activeColor = context.getColor(R.color.loom_recording)
    private val idleColor = context.getColor(R.color.loom_surfaceContainerHighest)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private var heights = FloatArray(LevelBand.BARS)

    @Volatile private var level = 0f
    private var lastFrameMs = 0L

    /** Ob gerade eine Aufnahme laeuft (Balken reagieren nur dann). */
    var isActive = false
        private set

    private val frame = object : Runnable {
        override fun run() {
            if (!isActive) return
            val now = SystemClock.uptimeMillis()
            heights = LevelBand.heights(level, heights, now - lastFrameMs)
            lastFrameMs = now
            invalidate()
            postOnAnimation(this)
        }
    }

    /** Spitzenwert 0..1 vom Recorder — darf von jedem Thread kommen. */
    fun setLevel(amplitude: Float) {
        level = LevelBand.fromAmplitude(amplitude)
    }

    fun start() {
        if (isActive) return
        isActive = true
        lastFrameMs = SystemClock.uptimeMillis()
        postOnAnimation(frame)
    }

    fun stop() {
        isActive = false
        removeCallbacks(frame)
        level = 0f
        heights = FloatArray(LevelBand.BARS)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = LevelBand.BARS * barWidth + (LevelBand.BARS - 1) * gap
        var x = (width - total) / 2f
        val cy = height / 2f
        paint.color = if (isActive) activeColor else idleColor
        for (i in 0 until LevelBand.BARS) {
            val h = minHeight + (maxHeight - minHeight) * heights[i]
            canvas.drawRoundRect(x, cy - h / 2f, x + barWidth, cy + h / 2f, radius, radius, paint)
            x += barWidth + gap
        }
    }
}
