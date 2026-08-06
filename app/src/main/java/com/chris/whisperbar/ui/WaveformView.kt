package com.chris.whisperbar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.chris.whisperbar.R
import kotlin.math.max

/**
 * Scrollende Balken-Wellenform als Rueckmeldung "ich hoere dich gerade".
 *
 * Die frueher genutzte Loesung war ein einzelner `View` mit `scaleX` — eine Linie, die
 * nur die aktuelle Lautstaerke zeigte. Hier laeuft stattdessen ein Ringpuffer der
 * letzten Messwerte durch, damit man sieht, dass wirklich etwas ankommt.
 *
 * Kosten: ein `drawRoundRect` je Balken, nur waehrend der Aufnahme.
 */
class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val activeColor = context.getColor(R.color.accent)
    private val idleColor = context.getColor(R.color.kb_key_pressed)

    /** Ringpuffer der letzten Pegel; [head] zeigt auf den aeltesten Eintrag. */
    private val levels = FloatArray(BAR_COUNT)
    private var head = 0
    private var active = false

    /** Neuen Messwert anhaengen und genau einmal neu zeichnen. */
    fun push(level: Float) {
        levels[head] = level.coerceIn(0f, 1f)
        head = (head + 1) % BAR_COUNT
        active = true
        invalidate()
    }

    /** Auf Ruhezustand zuruecksetzen (flache, gedaempfte Linie). */
    fun reset() {
        java.util.Arrays.fill(levels, 0f)
        head = 0
        active = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val slot = w / BAR_COUNT
        val barWidth = max(2f, slot * 0.55f)
        val radius = barWidth / 2f
        val minHeight = barWidth
        val cy = h / 2f

        paint.color = if (active) activeColor else idleColor

        for (i in 0 until BAR_COUNT) {
            // Vom aeltesten zum neuesten, damit die Welle nach rechts laeuft.
            val level = levels[(head + i) % BAR_COUNT]
            val barHeight = max(minHeight, level * h)
            val cx = slot * i + slot / 2f
            // Neuere Balken kraeftiger — erzeugt den Eindruck von Bewegung.
            paint.alpha = if (active) (90 + 165 * i / BAR_COUNT) else 90
            canvas.drawRoundRect(
                cx - barWidth / 2f, cy - barHeight / 2f,
                cx + barWidth / 2f, cy + barHeight / 2f,
                radius, radius, paint,
            )
        }
    }

    private companion object {
        const val BAR_COUNT = 28
    }
}
