package com.chris.whisperloom.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.chris.whisperloom.BuildConfig
import com.chris.whisperloom.R
import com.chris.whisperloom.ui.components.LinkRow
import com.chris.whisperloom.ui.components.SnackController
import com.chris.whisperloom.ui.components.LoomSheet

const val GITHUB_URL = "https://github.com/CTreitges/whisperloom-android"

/** E6 Ueber WhisperLoom (Spec §2.3): Version, Lizenzen, Quellcode-Link. */
@Composable
fun AboutSheet(snack: SnackController, onDismiss: () -> Unit) {
    LoomSheet(title = stringResource(R.string.settings_group_about), onDismiss = onDismiss) { dismiss ->
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
