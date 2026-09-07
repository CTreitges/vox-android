package com.chris.whisperbar.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chris.whisperbar.BuildConfig
import com.chris.whisperbar.R
import com.chris.whisperbar.ui.components.LinkRow
import com.chris.whisperbar.ui.components.SnackController
import com.chris.whisperbar.ui.components.WbSheet

const val GITHUB_URL = "https://github.com/CTreitges/whisperbar-android"

/** E6 Ueber WhisperBar (Spec §2.3): Version, Lizenzen, Quellcode-Link. */
@Composable
fun AboutSheet(snack: SnackController, onDismiss: () -> Unit) {
    WbSheet(title = stringResource(R.string.settings_group_about), onDismiss = onDismiss) { dismiss ->
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinkRow(headline = stringResource(R.string.about_source), url = GITHUB_URL, snack = snack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) }
        }
    }
}
