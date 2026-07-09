package com.chris.whisperbar

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.chris.whisperbar.a11y.TextInserterAccessibilityService
import com.chris.whisperbar.overlay.FloatingMicService

/**
 * Onboarding: (A) klassische Diktat-Tastatur (IME) und (B) — empfohlen — schwebender
 * Mikro-Button, der ohne Tastatur-Wechsel funktioniert (Overlay + Bedienungshilfe).
 * Bewusst reines Framework (kein AppCompat).
 */
class SetupActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        // A) IME-Weg
        findViewById<Button>(R.id.btn_mic).setOnClickListener {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
        }
        findViewById<Button>(R.id.btn_enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_select).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        findViewById<Button>(R.id.btn_prefs).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // B) Schwebender Button
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                ),
            )
        }
        findViewById<Button>(R.id.btn_a11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_notif).apply {
            if (Build.VERSION.SDK_INT >= 33) {
                setOnClickListener {
                    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                }
            } else {
                visibility = android.view.View.GONE
            }
        }
        findViewById<Button>(R.id.btn_bubble).setOnClickListener { toggleBubble() }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }

    private fun toggleBubble() {
        if (FloatingMicService.isRunning) {
            FloatingMicService.stop(this)
        } else {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.float_no_overlay, Toast.LENGTH_LONG).show()
                return
            }
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), REQ_MIC)
                return
            }
            FloatingMicService.start(this)
        }
        // Service-Status braucht einen Moment.
        window.decorView.postDelayed({ refreshStatus() }, 500)
    }

    private fun refreshStatus() {
        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        setStatus(R.id.status_mic, micGranted)

        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        val imeEnabled = imm.enabledInputMethodList.any { it.packageName == packageName }
        setStatus(R.id.status_enabled, imeEnabled)

        setStatus(R.id.status_overlay, Settings.canDrawOverlays(this))
        setStatus(R.id.status_a11y, TextInserterAccessibilityService.isRunning())

        findViewById<Button>(R.id.btn_bubble).setText(
            if (FloatingMicService.isRunning) R.string.setup_stop_bubble else R.string.setup_start_bubble,
        )
    }

    private fun setStatus(viewId: Int, done: Boolean) {
        findViewById<TextView>(viewId).text =
            getString(if (done) R.string.status_done else R.string.status_open)
    }

    companion object {
        private const val REQ_MIC = 1001
        private const val REQ_NOTIF = 1002
    }
}
