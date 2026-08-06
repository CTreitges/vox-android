package com.chris.whisperbar.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.chris.whisperbar.R
import com.chris.whisperbar.dictation.DictationPhase
import kotlin.math.min

/**
 * Der Mikro-Knopf als eigene View: gefuellter Kreis, ein mitatmender Pegel-Ring waehrend
 * der Aufnahme und ein rotierender Bogen waehrend der Erkennung.
 *
 * Wird von der Tastatur **und** vom schwebenden Knopf genutzt, damit beide denselben
 * Zustand gleich darstellen.
 *
 * Zeichnet nur, wenn sich etwas bewegt: im Ruhezustand laeuft kein einziger Frame.
 * Der Pegel wird ausserdem geglaettet, statt jeden Messwert hart zu uebernehmen —
 * so wirkt der Ring ruhig, obwohl die Messung 30-mal pro Sekunde kommt.
 */
class MicOrbView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val colorAccent = context.getColor(R.color.accent)
    private val colorAccentPressed = context.getColor(R.color.accent_pressed)
    private val colorRecording = context.getColor(R.color.recording)

    private val arcBounds = RectF()

    private var targetLevel = 0f
    private var shownLevel = 0f
    private var sweepStart = 0f

    init {
        // Bedienbar per Beruehrung UND per Bedienungshilfe (TalkBack loest den
        // OnClickListener direkt aus, ohne dass Touch-Events fliessen).
        isClickable = true
        isFocusable = true
    }

    /** Aktueller Zustand — bestimmt Farbe und Animation. */
    var phase: DictationPhase = DictationPhase.IDLE
        set(value) {
            if (field == value) return
            field = value
            if (value != DictationPhase.RECORDING) {
                targetLevel = 0f
                shownLevel = 0f
            }
            if (value == DictationPhase.WORKING) sweepStart = 0f
            invalidate()
            scheduleFrameIfNeeded()
        }

    /** Neuer Pegel 0..1; wird weich angefahren. */
    fun setLevel(level: Float) {
        targetLevel = level.coerceIn(0f, 1f)
        scheduleFrameIfNeeded()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val outer = min(width, height) / 2f
        // Platz fuer Ring und Bogen ausserhalb des Kreises reservieren.
        val base = outer * 0.72f
        val stroke = outer * 0.09f

        val recording = phase == DictationPhase.RECORDING
        val working = phase == DictationPhase.WORKING

        // Pegel weich nachziehen: schnell hoch (reagiert sofort), langsam runter (kein Flackern).
        val diff = targetLevel - shownLevel
        shownLevel += diff * if (diff > 0) RISE else FALL

        if (recording && shownLevel > 0.01f) {
            ring.color = colorRecording
            ring.alpha = (70 * shownLevel).toInt().coerceIn(0, 70)
            canvas.drawCircle(cx, cy, base + (outer - base) * shownLevel, ring)
        }

        fill.color = when {
            recording -> colorRecording
            isPressed -> colorAccentPressed
            else -> colorAccent
        }
        canvas.drawCircle(cx, cy, base, fill)

        if (working) {
            arc.color = colorAccent
            arc.strokeWidth = stroke
            val inset = stroke / 2f + 1f
            arcBounds.set(cx - outer + inset, cy - outer + inset, cx + outer - inset, cy + outer - inset)
            canvas.drawArc(arcBounds, sweepStart, 90f, false, arc)
            sweepStart = (sweepStart + SPIN_DEGREES_PER_FRAME) % 360f
        }

        scheduleFrameIfNeeded()
    }

    /** Naechsten Frame nur anfordern, wenn tatsaechlich Bewegung ansteht. */
    private fun scheduleFrameIfNeeded() {
        val moving = phase == DictationPhase.WORKING ||
            (phase == DictationPhase.RECORDING && kotlin.math.abs(targetLevel - shownLevel) > 0.005f)
        if (moving && isAttachedToWindow) postInvalidateOnAnimation()
    }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        invalidate()
    }

    private companion object {
        const val RISE = 0.45f
        const val FALL = 0.12f
        const val SPIN_DEGREES_PER_FRAME = 6f
    }
}
