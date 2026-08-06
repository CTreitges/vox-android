package com.chris.whisperbar

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.chris.whisperbar.a11y.TextInserterAccessibilityService
import com.chris.whisperbar.overlay.FloatingMicService

/**
 * Startbildschirm.
 *
 * Der alte Einrichtungsbildschirm war eine Liste aus neun gleichrangigen Knoepfen, bei
 * der man selbst herausfinden musste, welcher als naechstes dran ist — und erledigte
 * Schritte blieben fuer immer stehen. Hier gibt es stattdessen:
 *
 *  - **einen** Hauptknopf, der immer den naechsten fehlenden Schritt erledigt und,
 *    sobald alles da ist, das Diktat startet bzw. beendet;
 *  - eine Einrichtungsliste, aus der erledigte Schritte verschwinden;
 *  - die zwei Einstellungen, die man wirklich oft aendert (Sprache, Tempo), direkt hier.
 *
 * Bewusst reines Framework (kein AppCompat).
 */
class HomeActivity : Activity() {

    /** Ein Einrichtungsschritt: Titel, Erklaerung, Pruefung und die zugehoerige Aktion. */
    private data class SetupStep(
        val title: Int,
        val hint: Int,
        val done: () -> Boolean,
        val action: () -> Unit,
    )

    private lateinit var prefs: Prefs
    private lateinit var steps: List<SetupStep>

    /** Der Nutzer ist gerade in den Bedienungshilfe-Einstellungen — Ergebnis pruefen. */
    private var awaitingA11y = false

    /** Der Hinweis auf die eingeschraenkten Einstellungen kam schon; nicht wiederholen. */
    private var restrictedHintShown = false

    private lateinit var stateDot: View
    private lateinit var stateTitle: TextView
    private lateinit var stateDetail: TextView
    private lateinit var primary: Button
    private lateinit var gestures: TextView
    private lateinit var a11yWarning: TextView
    private lateinit var setupRows: LinearLayout
    private lateinit var setupDone: View
    private lateinit var labelSetup: TextView
    private lateinit var setupCard: View
    private lateinit var languageChips: LinearLayout
    private lateinit var speedChips: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_home)

        stateDot = findViewById(R.id.state_dot)
        stateTitle = findViewById(R.id.state_title)
        stateDetail = findViewById(R.id.state_detail)
        primary = findViewById(R.id.btn_primary)
        gestures = findViewById(R.id.gestures)
        a11yWarning = findViewById(R.id.a11y_warning)
        setupRows = findViewById(R.id.setup_rows)
        setupDone = findViewById(R.id.setup_done)
        labelSetup = findViewById(R.id.label_setup)
        setupCard = findViewById(R.id.setup_card)
        languageChips = findViewById(R.id.language_chips)
        speedChips = findViewById(R.id.speed_chips)

        steps = buildSteps()

        primary.setOnClickListener { doPrimaryAction() }
        // Zweiter Weg zur Erklaerung: wer den Hinweis liest, kommt direkt zur Anleitung.
        a11yWarning.setOnClickListener { showRestrictedSettingHelp() }
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btn_kb_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_kb_select).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        buildLanguageChips()
        buildSpeedChips()

        // Modell schon beim Oeffnen der App vorwaermen: wer hier landet, will gleich
        // diktieren — dann ist es beim ersten Versuch bereits im Speicher.
        WhisperEngine.preloadAsync(this)
    }

    override fun onResume() {
        super.onResume()
        // Die Bedienungshilfe laesst sich auch von aussen umschalten, waehrend dieser
        // Bildschirm offen bleibt — etwa mit der Lautstaerke-Tastenkombination. Ohne
        // Beobachter stuende der Schritt dann weiter auf "offen", obwohl er erledigt ist.
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            a11yObserver,
        )
        refresh()
        maybeExplainRestrictedSetting()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(a11yObserver)
    }

    private val a11yObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    // --- Bedienungshilfe: Androids „eingeschränkte Einstellungen" -------------

    private fun openAccessibilitySettings() {
        awaitingA11y = true
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /**
     * Ab Android 13 sperrt das System den Bedienungshilfe-Schalter fuer Apps, die per
     * APK-Datei statt aus einem Store installiert wurden („Aus Sicherheitsgründen ist
     * diese Einstellung derzeit nicht verfügbar"). Der Ausweg — App-Info, Menü ⋮,
     * „Eingeschränkte Einstellungen zulassen" — ist so gut versteckt, dass ohne Hinweis
     * kaum jemand darauf kommt.
     *
     * Deshalb: nur wer gerade in den Bedienungshilfen war und trotzdem ohne aktivierten
     * Dienst zurueckkommt, bekommt die Erklaerung — und das hoechstens einmal pro
     * App-Start, damit es nicht nervt.
     */
    private fun maybeExplainRestrictedSetting() {
        if (!awaitingA11y) return
        awaitingA11y = false
        if (TextInserterAccessibilityService.isEnabled(this)) return
        if (Build.VERSION.SDK_INT < 33 || restrictedHintShown) return
        restrictedHintShown = true
        showRestrictedSettingHelp()
    }

    private fun showRestrictedSettingHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.a11y_blocked_title)
            .setMessage(R.string.a11y_blocked_message)
            .setPositiveButton(R.string.a11y_blocked_open_app_info) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName"),
                    ),
                )
            }
            .setNeutralButton(R.string.a11y_blocked_retry) { _, _ -> openAccessibilitySettings() }
            .setNegativeButton(R.string.a11y_blocked_later, null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refresh()
    }

    // --- Einrichtungsschritte -----------------------------------------------

    private fun buildSteps(): List<SetupStep> {
        val list = mutableListOf(
            SetupStep(
                R.string.setup_row_mic, R.string.setup_row_mic_hint,
                done = ::hasMic,
                action = { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) },
            ),
            SetupStep(
                R.string.setup_row_overlay, R.string.setup_row_overlay_hint,
                done = { Settings.canDrawOverlays(this) },
                action = {
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName"),
                        ),
                    )
                },
            ),
            SetupStep(
                R.string.setup_row_a11y, R.string.setup_row_a11y_hint,
                done = { TextInserterAccessibilityService.isEnabled(this) },
                action = { openAccessibilitySettings() },
            ),
        )
        if (Build.VERSION.SDK_INT >= 33) {
            list += SetupStep(
                R.string.setup_row_notif, R.string.setup_row_notif_hint,
                done = {
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                },
                action = {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                },
            )
        }
        return list
    }

    /** Der Hauptknopf macht immer das, was gerade fehlt — und sonst das Diktat auf/zu. */
    private fun doPrimaryAction() {
        when {
            !hasMic() -> steps.first { it.title == R.string.setup_row_mic }.action()
            !Settings.canDrawOverlays(this) ->
                steps.first { it.title == R.string.setup_row_overlay }.action()
            FloatingMicService.isRunning -> FloatingMicService.stop(this)
            else -> FloatingMicService.start(this)
        }
        // Der Dienststatus braucht einen Moment, bis er sichtbar wird.
        primary.postDelayed(::refresh, SERVICE_SETTLE_MS)
    }

    // --- Darstellung --------------------------------------------------------

    private fun refresh() {
        val open = steps.filter { !it.done() }
        renderSetupRows(open)

        val micOk = hasMic()
        val overlayOk = Settings.canDrawOverlays(this)
        val running = FloatingMicService.isRunning

        primary.setText(
            when {
                !micOk -> R.string.home_grant_mic
                !overlayOk -> R.string.home_grant_overlay
                running -> R.string.home_stop_bubble
                else -> R.string.home_start_bubble
            },
        )

        val ready = micOk && overlayOk
        stateTitle.setText(
            when {
                !ready -> R.string.state_setup
                running -> R.string.state_active
                else -> R.string.state_ready
            },
        )
        stateDot.setBackgroundResource(
            if (ready) R.drawable.bg_dot_ok else R.drawable.bg_dot_todo,
        )
        stateDetail.text = statusLine()

        gestures.visibility = if (running) View.VISIBLE else View.GONE
        a11yWarning.visibility =
            if (ready && !TextInserterAccessibilityService.isEnabled(this)) View.VISIBLE else View.GONE

        updateChipSelection()
    }

    /** „Small · Deutsch · Turbo" — die drei Werte, die das Ergebnis am meisten praegen. */
    private fun statusLine(): String {
        val model = if (prefs.useApi) prefs.apiModel else prefs.model.shortName
        val language = Prefs.LANGUAGES.firstOrNull { it.first == prefs.language }?.second
            ?: prefs.language
        val speed = getString(if (prefs.fastMode) R.string.speed_turbo else R.string.speed_quality)
        return "$model · $language · $speed"
    }

    private fun renderSetupRows(open: List<SetupStep>) {
        setupRows.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (step in open) {
            val row = inflater.inflate(R.layout.item_setup_row, setupRows, false)
            row.findViewById<TextView>(R.id.row_title).setText(step.title)
            row.findViewById<TextView>(R.id.row_hint).setText(step.hint)
            row.setOnClickListener { step.action() }
            setupRows.addView(row)
        }
        val allDone = open.isEmpty()
        setupRows.visibility = if (allDone) View.GONE else View.VISIBLE
        setupDone.visibility = if (allDone) View.VISIBLE else View.GONE
        // Nichts offen und alles erledigt: der Abschnitt darf klein bleiben, aber
        // sichtbar — er ist die Stelle, an der man spaeter etwas wieder abschaltet.
        labelSetup.visibility = View.VISIBLE
        setupCard.visibility = View.VISIBLE
    }

    // --- Chips --------------------------------------------------------------

    private fun buildLanguageChips() {
        for ((code, name) in Prefs.LANGUAGES) {
            languageChips.addView(
                chip(name) {
                    prefs.language = code
                    refresh()
                }.also { it.tag = code },
            )
        }
    }

    private fun buildSpeedChips() {
        speedChips.addView(
            chip(getString(R.string.speed_turbo)) {
                prefs.fastMode = true
                refresh()
            }.also { it.tag = true },
        )
        speedChips.addView(
            chip(getString(R.string.speed_quality)) {
                prefs.fastMode = false
                refresh()
            }.also { it.tag = false },
        )
    }

    private fun chip(label: String, onClick: () -> Unit): Button {
        val b = Button(this, null, 0, R.style.Chip)
        b.text = label
        b.setOnClickListener { onClick() }
        // Die Style-Layout-Attribute greifen bei programmatisch erzeugten Views nicht.
        b.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(38),
        ).apply { marginEnd = dp(8) }
        return b
    }

    private fun updateChipSelection() {
        for (i in 0 until languageChips.childCount) {
            val v = languageChips.getChildAt(i)
            v.isSelected = v.tag == prefs.language
        }
        for (i in 0 until speedChips.childCount) {
            val v = speedChips.getChildAt(i)
            v.isSelected = v.tag == prefs.fastMode
        }
    }

    // --- Helfer -------------------------------------------------------------

    private fun hasMic() =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQ_MIC = 1001
        const val REQ_NOTIF = 1002
        const val SERVICE_SETTLE_MS = 400L
    }
}
