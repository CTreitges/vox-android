package com.chris.vox.ime

import com.chris.vox.overlay.BubbleVisual

/** Reine (Android-freie) Masse und Zuordnungen der Diktier-Tastatur (UX-Spec §5.3). */
object ImeMetrics {

    const val KEY_HEIGHT_DP = 48
    const val KEY_HEIGHT_LARGE_DP = 56

    /** Ab fontScale 1,3 wachsen die Tasten von 48 auf 56 dp. */
    const val LARGE_FONT_SCALE = 1.3f

    fun keyHeightDp(fontScale: Float): Int =
        if (fontScale >= LARGE_FONT_SCALE) KEY_HEIGHT_LARGE_DP else KEY_HEIGHT_DP

    /** Level in `mic_button_bg.xml` (Level-List der vier Fuellungen) fuer eine Darstellung. */
    fun micFillLevel(visual: BubbleVisual): Int = visual.fill.ordinal
}
