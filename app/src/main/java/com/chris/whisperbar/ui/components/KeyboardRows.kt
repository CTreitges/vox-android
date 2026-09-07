package com.chris.whisperbar.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.chris.whisperbar.R
import com.chris.whisperbar.ui.state.LocalAppEnv

/**
 * Tastatur aktivieren / auswaehlen + Probierfeld (Schritt 7 und E3, Spec §2.2/§2.6).
 * "Auswaehlen" erst, wenn die Tastatur aktiviert ist.
 */
@Composable
fun KeyboardRows(tryFieldMinLines: Int, snack: SnackController) {
    val ctx = LocalContext.current
    val status = LocalAppEnv.current.status
    var tryText by rememberSaveable { mutableStateOf("") }

    WbRow(
        headline = stringResource(R.string.setup_s7_enable),
        leading = { StatusIcon(if (status.imeEnabled) Tone.SUCCESS else Tone.NEUTRAL, R.drawable.ic_radio_button_unchecked) },
        stateDescription = stringResource(if (status.imeEnabled) R.string.setup_status_done else R.string.setup_status_missing),
        trailing = {
            FilledTonalButton(onClick = { openOrSnack(ctx, SystemIntents.inputMethods(), snack) }) {
                Text(stringResource(R.string.setup_s7_enable_btn))
            }
        },
    )
    WbRow(
        headline = stringResource(R.string.setup_s7_select),
        leading = { StatusIcon(if (status.imeSelected) Tone.SUCCESS else Tone.NEUTRAL, R.drawable.ic_radio_button_unchecked) },
        stateDescription = stringResource(if (status.imeSelected) R.string.setup_status_done else R.string.setup_status_missing),
        trailing = {
            FilledTonalButton(onClick = { SystemIntents.showImePicker(ctx) }, enabled = status.imeEnabled) {
                Text(stringResource(R.string.setup_s7_select_btn))
            }
        },
    )
    OutlinedTextField(
        value = tryText,
        onValueChange = { tryText = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.setup_try_hint)) },
        minLines = tryFieldMinLines,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

/** System-Intent oeffnen; ohne Empfaenger Snackbar mit der Aktion als Klartext. */
fun openOrSnack(ctx: android.content.Context, intent: android.content.Intent, snack: SnackController) {
    if (!SystemIntents.open(ctx, intent)) {
        snack.show(ctx.getString(R.string.err_unknown, intent.action ?: "Intent"))
    }
}
