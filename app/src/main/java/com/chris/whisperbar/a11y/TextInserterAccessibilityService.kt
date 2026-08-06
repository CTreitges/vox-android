package com.chris.whisperbar.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Bedienungshilfe-Dienst, der erkannten Text an der Cursor-Position in das gerade
 * fokussierte, editierbare Feld einfuegt — OHNE selbst Tastatur zu sein. Dadurch kann
 * der schwebende Mikro-Button diktieren, waehrend Gboard aktiv bleibt.
 *
 * Bewusst clipboard-frei (ACTION_SET_TEXT), da Zwischenablage-Schreibzugriff auf
 * Android 10+ fuer Hintergrund-Prozesse eingeschraenkt ist.
 *
 * Merkt sich das zuletzt Eingefuegte, damit ein misslungenes Diktat mit einer Geste
 * wieder verschwindet ([undoLast]) — sonst muesste man Wort fuer Wort loeschen.
 */
class TextInserterAccessibilityService : AccessibilityService() {

    /** Zuletzt eingefuegter Text; null, sobald er nicht mehr zurueckgenommen werden kann. */
    private var lastInserted: String? = null

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* nicht benoetigt */ }

    override fun onInterrupt() { /* nicht benoetigt */ }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Das aktuell fokussierte, beschreibbare Feld — oder null. */
    private fun focusedEditable(): AccessibilityNodeInfo? =
        rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    /** Fuegt [text] an der Cursor-Position ein. Gibt true bei Erfolg. */
    fun insert(text: String): Boolean {
        if (text.isEmpty()) return true
        val node = focusedEditable() ?: return false

        val old = node.text?.toString() ?: ""
        val (lo, hi) = selectionOf(node, old)

        // Fuehrendes Leerzeichen, wenn direkt an ein Wort angefuegt wird.
        val needsSpace = lo > 0 && !old[lo - 1].isWhitespace() &&
            text.isNotEmpty() && !text[0].isWhitespace()
        val ins = if (needsSpace) " $text" else text

        val combined = old.substring(0, lo) + ins + old.substring(hi)
        if (!setText(node, combined)) return false

        val cursor = lo + ins.length
        setSelection(node, cursor, cursor)
        lastInserted = ins
        return true
    }

    /**
     * Nimmt das zuletzt Eingefuegte zurueck — aber nur, wenn es unveraendert direkt vor
     * dem Cursor steht. Sonst haette man in der Zwischenzeit weitergetippt und wuerde
     * fremden Text loeschen.
     */
    fun undoLast(): Boolean {
        val text = lastInserted ?: return false
        val node = focusedEditable() ?: return false
        val old = node.text?.toString() ?: return false
        val (lo, hi) = selectionOf(node, old)
        if (lo != hi || lo < text.length) return false
        if (old.substring(lo - text.length, lo) != text) return false

        val combined = old.substring(0, lo - text.length) + old.substring(lo)
        if (!setText(node, combined)) return false
        val cursor = lo - text.length
        setSelection(node, cursor, cursor)
        lastInserted = null
        return true
    }

    private fun selectionOf(node: AccessibilityNodeInfo, text: String): Pair<Int, Int> {
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start !in 0..text.length || end !in 0..text.length) {
            start = text.length
            end = text.length
        }
        return minOf(start, end) to maxOf(start, end)
    }

    private fun setText(node: AccessibilityNodeInfo, value: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun setSelection(node: AccessibilityNodeInfo, start: Int, end: Int) {
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args)
    }

    companion object {
        @Volatile
        private var instance: TextInserterAccessibilityService? = null

        /** Ob der Dienst laeuft UND gebunden ist — Voraussetzung fuers Einfuegen. */
        fun isRunning(): Boolean = instance != null

        /**
         * Ob der Nutzer den Dienst eingeschaltet hat — gelesen aus der Systemeinstellung
         * statt aus [instance]. Zwischen "eingeschaltet" und "gebunden" liegen ein paar
         * hundert Millisekunden; fuer die Statusanzeige zaehlt, was der Nutzer umgelegt
         * hat, sonst steht dort noch "offen", obwohl der Schalter schon an ist.
         */
        fun isEnabled(context: Context): Boolean {
            val am = context.getSystemService(AccessibilityManager::class.java) ?: return false
            return runCatching {
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo?.serviceInfo?.packageName == context.packageName }
            }.getOrDefault(false)
        }

        /** Versucht den Text einzufuegen; false, wenn Dienst aus oder kein Fokusfeld. */
        fun tryInsert(text: String): Boolean = instance?.insert(text) ?: false

        /** Versucht das letzte Diktat zurueckzunehmen; false, wenn nicht moeglich. */
        fun tryUndo(): Boolean = instance?.undoLast() ?: false
    }
}
