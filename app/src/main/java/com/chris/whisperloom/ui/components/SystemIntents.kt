package com.chris.whisperloom.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** System-Intents des Assistenten (Spec §2.2) — an einer Stelle, damit E3 dieselben nutzt. */
object SystemIntents {

    fun overlay(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun accessibility(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    fun inputMethods(): Intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)

    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    /** false, wenn das System keinen Empfaenger hat (Aufrufer zeigt Snackbar). */
    fun open(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    fun showImePicker(ctx: Context) {
        ctx.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
}

/** Die Activity hinter einem (Compose-)Context — fuer Berechtigungs-Rationale. */
fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
