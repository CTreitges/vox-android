package com.chris.whisperbar

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * Einstellungen, gruppiert in Karten: Erkennung, Bedienung, Textveredelung, Cloud-API.
 *
 * Die frueheren Spinner sind durch direkt sichtbare Auswahl-Zeilen und Chips ersetzt —
 * bei drei Modellen und sechs Sprachen ist ein Aufklappmenue nur ein Klick extra, und
 * man sieht ohne Antippen, was gerade eingestellt ist. Der Cloud-Block klappt erst auf,
 * wenn der Schalter an ist. Persistiert via [Prefs].
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var modelOptions: LinearLayout
    private lateinit var languageChips: LinearLayout
    private lateinit var autoStopChips: LinearLayout
    private lateinit var autoStopBlock: View
    private lateinit var apiBlock: View

    /** Modell, dessen Download gerade laeuft — blockiert weitere Downloads. */
    private var downloading: WhisperModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.get(this)
        setContentView(R.layout.activity_settings)

        modelOptions = findViewById(R.id.model_options)
        languageChips = findViewById(R.id.language_chips)
        autoStopChips = findViewById(R.id.auto_stop_chips)
        autoStopBlock = findViewById(R.id.auto_stop_block)
        apiBlock = findViewById(R.id.api_block)

        buildLanguageChips()
        buildAutoStopChips()
        buildModelOptions()
        setupSwitches()
        setupApiSection()
    }

    override fun onPause() {
        super.onPause()
        // API-Textfelder persistieren (Leereingabe -> Defaults).
        prefs.apiBaseUrl = field(R.id.api_url).ifBlank { Prefs.DEFAULT_API_URL }
        prefs.apiKey = field(R.id.api_key)
        prefs.apiModel = field(R.id.api_model).ifBlank { Prefs.DEFAULT_API_MODEL }
    }

    private fun field(id: Int) = findViewById<EditText>(id).text.toString().trim()

    // --- Modell -------------------------------------------------------------

    private fun buildModelOptions() {
        modelOptions.removeAllViews()
        val inflater = LayoutInflater.from(this)
        for (model in WhisperModel.entries) {
            val row = inflater.inflate(R.layout.item_model_option, modelOptions, false)
            row.findViewById<TextView>(R.id.model_name).text = model.shortName
            row.setOnClickListener {
                // Auswaehlen darf man nur, was auch lokal vorliegt — sonst schluege
                // das naechste Diktat fehl statt einfach zu funktionieren.
                if (ModelManager.isAvailable(this, model)) {
                    prefs.model = model
                    buildModelOptions()
                } else {
                    startDownload(model)
                }
            }
            row.findViewById<Button>(R.id.model_download).setOnClickListener { startDownload(model) }
            modelOptions.addView(row)
            renderModelRow(row, model)
        }
    }

    private fun renderModelRow(row: View, model: WhisperModel) {
        val available = ModelManager.isAvailable(this, model)
        val selected = prefs.model == model
        val state = row.findViewById<TextView>(R.id.model_state)
        val download = row.findViewById<Button>(R.id.model_download)

        row.findViewById<View>(R.id.model_dot).setBackgroundResource(
            if (selected) R.drawable.bg_dot_ok else R.drawable.bg_dot_todo,
        )
        state.text = when {
            downloading == model -> getString(R.string.model_downloading)
            model.bundled -> "${model.tagline} · ${getString(R.string.model_bundled)}"
            available -> "${model.tagline} · ${getString(R.string.model_available)}"
            else -> "${model.tagline} · ~${model.approxMb} MB"
        }
        download.visibility = if (!available && downloading == null) View.VISIBLE else View.GONE
        download.isEnabled = downloading == null
    }

    private fun startDownload(model: WhisperModel) {
        if (downloading != null || model.bundled) return
        downloading = model
        buildModelOptions()
        io.submit {
            val ok = ModelManager.download(this, model) { pct ->
                main.post { showDownloadProgress(model, pct) }
            }
            main.post {
                downloading = null
                if (ok) prefs.model = model
                else Toast.makeText(this, R.string.model_download_failed, Toast.LENGTH_LONG).show()
                buildModelOptions()
            }
        }
    }

    /** Fortschritt direkt in der Zeile des Modells zeigen, ohne die Liste neu zu bauen. */
    private fun showDownloadProgress(model: WhisperModel, percent: Int) {
        val index = WhisperModel.entries.indexOf(model)
        val row = modelOptions.getChildAt(index) ?: return
        row.findViewById<TextView>(R.id.model_state).text =
            "${getString(R.string.model_downloading)} $percent %"
    }

    // --- Sprache + Pausenlaenge ---------------------------------------------

    private fun buildLanguageChips() {
        languageChips.removeAllViews()
        for ((code, name) in Prefs.LANGUAGES) {
            val c = chip(name, prefs.language == code) {
                prefs.language = code
                buildLanguageChips()
            }
            languageChips.addView(c)
        }
    }

    private fun buildAutoStopChips() {
        autoStopChips.removeAllViews()
        for ((label, ms) in Prefs.AUTO_STOP_CHOICES) {
            val c = chip(label, prefs.autoStopMs == ms) {
                prefs.autoStopMs = ms
                buildAutoStopChips()
            }
            autoStopChips.addView(c)
        }
    }

    private fun chip(label: String, selected: Boolean, onClick: () -> Unit): Button {
        val b = Button(this, null, 0, R.style.Chip)
        b.text = label
        b.isSelected = selected
        b.setOnClickListener { onClick() }
        // Die Layout-Attribute des Styles greifen bei programmatisch erzeugten Views nicht.
        b.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(38),
        ).apply { marginEnd = dp(8) }
        return b
    }

    // --- Schalter -----------------------------------------------------------

    private fun setupSwitches() {
        bind(R.id.switch_fast, prefs.fastMode) { prefs.fastMode = it }
        bind(R.id.switch_haptics, prefs.haptics) { prefs.haptics = it }
        bind(R.id.switch_autostart, prefs.bubbleAutoStart) { prefs.bubbleAutoStart = it }
        bind(R.id.switch_fillers, prefs.removeFillers) { prefs.removeFillers = it }
        bind(R.id.switch_cap, prefs.autoCapitalize) { prefs.autoCapitalize = it }
        bind(R.id.switch_space, prefs.trailingSpace) { prefs.trailingSpace = it }

        bind(R.id.switch_hands_free, prefs.handsFree) {
            prefs.handsFree = it
            // Die Pausenlaenge ist nur im Freihand-Modus wirksam.
            autoStopBlock.visibility = if (it) View.VISIBLE else View.GONE
        }
        autoStopBlock.visibility = if (prefs.handsFree) View.VISIBLE else View.GONE
    }

    private fun bind(id: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
        findViewById<Switch>(id).apply {
            isChecked = initial
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
    }

    // --- Cloud-API ----------------------------------------------------------

    private fun setupApiSection() {
        bind(R.id.switch_api, prefs.useApi) {
            prefs.useApi = it
            apiBlock.visibility = if (it) View.VISIBLE else View.GONE
        }
        apiBlock.visibility = if (prefs.useApi) View.VISIBLE else View.GONE
        findViewById<EditText>(R.id.api_url).setText(prefs.apiBaseUrl)
        findViewById<EditText>(R.id.api_key).setText(prefs.apiKey)
        findViewById<EditText>(R.id.api_model).setText(prefs.apiModel)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
