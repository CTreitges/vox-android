package com.chris.whisperbar.overlay

import android.animation.Animator
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import com.chris.whisperbar.R

/**
 * Ring-Ebenen um den Mikro-Kreis — fuer Knopf (84/72/76 dp) und IME-Taste (104/92/96 dp)
 * gleich aufgebaut: [pulse] Puls-Ring (nur RECORDING), [ring] statischer 2-dp-Zustandsring,
 * [arc] rotierender Sende-Bogen (nur SENDING). Genau eine Ebene ist sichtbar.
 */
class MicRings(
    private val pulse: View,
    private val ring: View,
    private val arc: View,
    private val reduceMotion: () -> Boolean,
) {
    /** Pegel 0..1 — hebt den Startradius des Pulses (BubbleMotion.PULSE_LEVEL_BOOST). */
    @Volatile var level = 0f

    private var pulseAnim: Animator? = null
    private var spinAnim: Animator? = null

    /** Zaehlt jeden Wechsel; ein verspaeteter Erfolgs-Ring-Rueckfall gilt nur fuer seine Generation. */
    private var generation = 0

    fun show(kind: BubbleVisual.Ring) {
        generation++
        stopAnimations()
        when (kind) {
            BubbleVisual.Ring.PRIMARY -> static(R.drawable.bubble_ring_idle)
            BubbleVisual.Ring.RECORDING_STATIC -> static(R.drawable.bubble_ring_recording)
            BubbleVisual.Ring.ERROR -> static(R.drawable.bubble_ring_error)
            BubbleVisual.Ring.PULSE -> {
                ring.visibility = View.INVISIBLE
                arc.visibility = View.GONE
                pulse.visibility = View.VISIBLE
                pulseAnim = BubbleAnimators.pulse(pulse) { level }.also { it.start() }
            }
            BubbleVisual.Ring.ARC -> {
                ring.visibility = View.INVISIBLE
                pulse.visibility = View.INVISIBLE
                arc.visibility = View.VISIBLE
                // Reduce-Motion: der Bogen steht still, bleibt aber als Zustandsbild sichtbar.
                if (!reduceMotion()) spinAnim = BubbleAnimators.spin(arc).also { it.start() }
            }
        }
    }

    /** Erfolg: 300 ms success-Ring, danach wieder der IDLE-Ring (sofern kein Wechsel dazwischenkam). */
    fun flashSuccess() {
        generation++
        val mine = generation
        stopAnimations()
        static(R.drawable.bubble_ring_success)
        ring.postDelayed({ if (generation == mine) show(BubbleVisual.Ring.PRIMARY) }, BubbleMotion.SUCCESS_RING_MS)
    }

    fun release() = stopAnimations()

    private fun static(res: Int) {
        pulse.visibility = View.INVISIBLE
        arc.visibility = View.GONE
        ring.setBackgroundResource(res)
        ring.visibility = View.VISIBLE
    }

    private fun stopAnimations() {
        pulseAnim?.cancel()
        pulseAnim = null
        spinAnim?.cancel()
        spinAnim = null
        pulse.alpha = 1f
        pulse.scaleX = 1f
        pulse.scaleY = 1f
        arc.rotation = 0f
    }
}

/** Icon im Mikro-Kreis: Symbol, Farbe, Deckkraft — mit kurzem Fade (150 ms) beim Wechsel. */
object MicIcon {

    fun iconRes(icon: BubbleVisual.Icon): Int = when (icon) {
        BubbleVisual.Icon.MIC -> R.drawable.ic_mic
        BubbleVisual.Icon.STOP -> R.drawable.ic_stop
        BubbleVisual.Icon.REPLAY -> R.drawable.ic_replay
        BubbleVisual.Icon.CONTENT_COPY -> R.drawable.ic_content_copy
    }

    fun tintRes(tint: BubbleVisual.IconTint): Int = when (tint) {
        BubbleVisual.IconTint.PRIMARY -> R.color.wb_primary
        BubbleVisual.IconTint.ON_RECORDING -> R.color.wb_onRecording
        BubbleVisual.IconTint.ON_PRIMARY_CONTAINER -> R.color.wb_onPrimaryContainer
        BubbleVisual.IconTint.ON_ERROR_CONTAINER -> R.color.wb_onErrorContainer
    }

    fun apply(view: ImageView, visual: BubbleVisual, animate: Boolean) {
        view.animate().cancel()
        view.setImageResource(iconRes(visual.icon))
        view.imageTintList = ColorStateList.valueOf(view.context.getColor(tintRes(visual.iconTint)))
        if (animate) {
            view.alpha = 0f
            view.animate().alpha(visual.iconAlpha).setDuration(BubbleMotion.ICON_FADE_MS).start()
        } else {
            view.alpha = visual.iconAlpha
        }
    }
}
