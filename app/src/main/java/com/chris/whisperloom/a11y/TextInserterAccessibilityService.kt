package com.chris.whisperloom.a11y

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

        val old = currentText(node)
        val result = TextInsertion.compute(old, node.textSelectionStart, node.textSelectionEnd, text)

        val setArgs = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, result.text,
            )
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)) return false

        val cursor = result.cursor
        val selArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        return true
    }

    /**
     * Aktueller Feldinhalt — leer, wenn das Feld nur seinen Platzhalter zeigt.
     *
     * WICHTIG: `AccessibilityNodeInfo.getText()` liefert bei einem LEEREN Feld den
     * Hint-Text (TextView.getTextForAccessibility faellt auf den Hint zurueck). Ohne
     * diese Pruefung landet der Platzhalter als "vorhandener Text" im Feld und das
     * Diktat wird daran angehaengt — genau die Symptome "Nachricht ..." (WhatsApp)
     * oder "Google ..." (Suchleiste) vor dem eigentlichen Text.
     */
    private fun currentText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString() ?: return ""
        // isShowingHintText ist der offizielle Weg (API 26+); der Hint-Vergleich faengt
        // Felder ab, die das Flag nicht setzen, aber trotzdem den Hint als Text melden.
        if (node.isShowingHintText || text == node.hintText?.toString()) return ""
        return text
    }

    companion object {
        @Volatile
        private var instance: TextInserterAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        /** Versucht den Text einzufuegen; false, wenn Dienst aus oder kein Fokusfeld. */
        fun tryInsert(text: String): Boolean = instance?.insert(text) ?: false
    }
}
