package com.chris.whisperbar

import android.app.Activity
import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch

/**
 * Einstellungen: Sprache + Nachbearbeitungs-Schalter. Persistiert direkt via [Prefs].
 */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_settings)

        setupLanguageSpinner()

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

    private fun setupLanguageSpinner() {
        val codes = Prefs.LANGUAGES.map { it.first }
        val names = Prefs.LANGUAGES.map { it.second }
        val spinner = findViewById<Spinner>(R.id.spinner_language)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, names,
        )
        spinner.setSelection(codes.indexOf(prefs.language).coerceAtLeast(0))
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                prefs.language = codes[position]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
}
