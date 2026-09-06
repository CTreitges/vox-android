package com.chris.whisperbar

import android.app.Activity
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Switch

/**
 * Einstellungen: Zugang zur Transkriptions-API, Sprache und Nachbearbeitung.
 * Persistiert via [Prefs].
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)

        setupApiSection()
        setupLanguageSpinner()
        setupSwitches()
    }

    override fun onPause() {
        super.onPause()
        // Textfelder roh persistieren (leer -> Anbieter-Default, siehe AccessResolver).
        prefs.apiBaseUrl = field(R.id.api_url)
        prefs.apiKey = field(R.id.api_key)
        prefs.apiModel = field(R.id.api_model)
        prefs.apiPrompt = field(R.id.api_prompt)
        prefs.llmModel = field(R.id.llm_model)
        // Uebergang bis zum Compose-Setup (WP4): wer hier einen Key eintraegt, will online.
        if (prefs.engine == null && prefs.apiKey.isNotBlank()) prefs.engine = Engine.ONLINE
    }

    private fun field(id: Int) = findViewById<EditText>(id).text.toString().trim()

    // --- API ----------------------------------------------------------------

    private fun setupApiSection() {
        // Aufgeloeste Werte anzeigen (Anbieter-Defaults statt leerer Felder).
        val stt = prefs.sttAccess()
        findViewById<EditText>(R.id.api_url).setText(stt.baseUrl)
        findViewById<EditText>(R.id.api_key).setText(stt.apiKey)
        findViewById<EditText>(R.id.api_model).setText(stt.model)
        findViewById<EditText>(R.id.api_prompt).setText(prefs.apiPrompt)
        findViewById<EditText>(R.id.llm_model).setText(prefs.llmAccess().model)
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

        val smart = findViewById<Switch>(R.id.switch_smart_fillers).apply {
            isChecked = prefs.smartFillers
            setOnCheckedChangeListener { _, checked -> prefs.smartFillers = checked }
        }
        findViewById<Switch>(R.id.switch_llm).apply {
            // Der alte Schalter bildet nur OFF/POLISH ab; die weiteren Modi kommen mit dem Compose-UI.
            isChecked = prefs.refineMode != RefineMode.OFF
            // Die KI-Fuellwortentscheidung haengt an der Veredelung — ohne sie gibt es
            // keinen zweiten Aufruf, in dem sie stattfinden koennte.
            smart.isEnabled = isChecked
            setOnCheckedChangeListener { _, checked ->
                prefs.refineMode = if (checked) RefineMode.POLISH else RefineMode.OFF
                smart.isEnabled = checked
            }
        }
    }
}
