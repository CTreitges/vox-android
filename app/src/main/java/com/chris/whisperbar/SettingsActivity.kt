package com.chris.whisperbar

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * Einstellungen: Modell (Qualität ↔ Tempo, Download nur auf expliziten Button-Druck),
 * Sprache, Nachbearbeitung und optional eine Cloud-API. Persistiert via [Prefs].
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val models = WhisperModel.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)

        setupModelSpinner()
        setupLanguageSpinner()
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

    private fun setupModelSpinner() {
        val spinner = findViewById<Spinner>(R.id.spinner_model)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, models.map { it.display },
        )
        spinner.setSelection(models.indexOf(prefs.model))
        refreshModelUi(prefs.model)

        // Kein Auto-Download: die Auswahl aktiviert nur ein bereits vorhandenes Modell.
        // Nicht vorhandene Modelle werden NUR per Button geladen. (Fix: kein Download beim Öffnen.)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val chosen = models[position]
                if (ModelManager.isAvailable(this@SettingsActivity, chosen)) prefs.model = chosen
                refreshModelUi(chosen)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        findViewById<Button>(R.id.btn_model_download).setOnClickListener {
            val chosen = models[spinner.selectedItemPosition]
            if (ModelManager.isAvailable(this, chosen)) {
                prefs.model = chosen
                refreshModelUi(chosen)
            } else {
                downloadModel(chosen)
            }
        }
    }

    private fun downloadModel(model: WhisperModel) {
        val status = findViewById<TextView>(R.id.model_status)
        val btn = findViewById<Button>(R.id.btn_model_download)
        val spinner = findViewById<Spinner>(R.id.spinner_model)
        btn.isEnabled = false
        spinner.isEnabled = false
        status.text = "${getString(R.string.model_downloading)} 0%"
        io.submit {
            val ok = ModelManager.download(this, model) { pct ->
                main.post { status.text = "${getString(R.string.model_downloading)} $pct%" }
            }
            main.post {
                btn.isEnabled = true
                spinner.isEnabled = true
                if (ok) prefs.model = model
                else Toast.makeText(this, R.string.model_download_failed, Toast.LENGTH_LONG).show()
                refreshModelUi(model)
            }
        }
    }

    private fun refreshModelUi(model: WhisperModel) {
        val status = findViewById<TextView>(R.id.model_status)
        val btn = findViewById<Button>(R.id.btn_model_download)
        val available = ModelManager.isAvailable(this, model)
        status.text = when {
            model.bundled -> "✓ im APK enthalten"
            available -> if (model == prefs.model) getString(R.string.model_ready) else "geladen"
            else -> "nicht geladen (~${model.approxMb} MB)"
        }
        btn.visibility = if (!available) android.view.View.VISIBLE else android.view.View.GONE
    }

    // --- Sprache + Schalter -------------------------------------------------

    private fun setupLanguageSpinner() {
        val codes = Prefs.LANGUAGES.map { it.first }
        val names = Prefs.LANGUAGES.map { it.second }
        val spinner = findViewById<Spinner>(R.id.spinner_language)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.setSelection(codes.indexOf(prefs.language).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.language = codes[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupSwitches() {
        findViewById<Switch>(R.id.switch_fillers).apply {
            isChecked = prefs.removeFillers
            setOnCheckedChangeListener { _, checked -> prefs.removeFillers = checked }
        }
        findViewById<Switch>(R.id.switch_cap).apply {
            isChecked = prefs.autoCapitalize
            setOnCheckedChangeListener { _, checked -> prefs.autoCapitalize = checked }
        }
        findViewById<Switch>(R.id.switch_space).apply {
            isChecked = prefs.trailingSpace
            setOnCheckedChangeListener { _, checked -> prefs.trailingSpace = checked }
        }
    }

    // --- Cloud-API ----------------------------------------------------------

    private fun setupApiSection() {
        findViewById<Switch>(R.id.switch_api).apply {
            isChecked = prefs.useApi
            setOnCheckedChangeListener { _, checked -> prefs.useApi = checked }
        }
        findViewById<EditText>(R.id.api_url).setText(prefs.apiBaseUrl)
        findViewById<EditText>(R.id.api_key).setText(prefs.apiKey)
        findViewById<EditText>(R.id.api_model).setText(prefs.apiModel)
    }
}
