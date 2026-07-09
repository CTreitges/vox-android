package com.chris.whisperbar.a11y

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Bedienungshilfe-Dienst, der erkannten Text an der Cursor-Position in das gerade
 * fokussierte, editierbare Feld einfuegt — OHNE selbst Tastatur zu sein. Dadurch kann
 * der schwebende Mikro-Button diktieren, waehrend Gboard aktiv bleibt.
 *
 * Bewusst clipboard-frei (ACTION_SET_TEXT), da Zwischenablage-Schreibzugriff auf
 * Android 10+ fuer Hintergrund-Prozesse eingeschraenkt ist.
 */
class TextInserterAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* nicht benoetigt */ }

    override fun onInterrupt() { /* nicht benoetigt */ }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Fuegt [text] an der Cursor-Position ein. Gibt true bei Erfolg. */
    fun insert(text: String): Boolean {
        if (text.isEmpty()) return true
        val root = rootInActiveWindow ?: return false
        val node = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
            ?: return false

        val old = node.text?.toString() ?: ""
        var selStart = node.textSelectionStart
        var selEnd = node.textSelectionEnd
        if (selStart !in 0..old.length || selEnd !in 0..old.length) {
            selStart = old.length
            selEnd = old.length
        }
        val lo = minOf(selStart, selEnd)
        val hi = maxOf(selStart, selEnd)

        // Fuehrendes Leerzeichen, wenn direkt an ein Wort angefuegt wird.
        val needsSpace = lo > 0 && !old[lo - 1].isWhitespace() &&
            text.isNotEmpty() && !text[0].isWhitespace()
        val ins = if (needsSpace) " $text" else text

        val combined = old.substring(0, lo) + ins + old.substring(hi)
        val setArgs = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, combined)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) return false

        val cursor = lo + ins.length
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        return true
    }

    companion object {
        @Volatile
        private var instance: TextInserterAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        /** Versucht den Text einzufuegen; false, wenn Dienst aus oder kein Fokusfeld. */
        fun tryInsert(text: String): Boolean = instance?.insert(text) ?: false
    }
}
