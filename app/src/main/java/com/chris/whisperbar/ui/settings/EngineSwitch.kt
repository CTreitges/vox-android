package com.chris.whisperbar.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperbar.Engine
import com.chris.whisperbar.R
import com.chris.whisperbar.ui.components.SnackController
import com.chris.whisperbar.ui.components.WbIcon
import com.chris.whisperbar.ui.state.LocalAppEnv

/**
 * Umschalter Online-Dienst / Offline-Modell (E1 und E4, Spec §2.4/§2.7). [requireModelForOffline]:
 * E4 laesst Offline nur zu, wenn ein Modell installiert ist (Snackbar), E1 zeigt stattdessen die Warnkarte.
 */
@Composable
fun EngineSwitch(snack: SnackController, requireModelForOffline: Boolean) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val needDownload = stringResource(R.string.models_need_download)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(48.dp)) {
        SegmentedButton(
            selected = prefs.engine == Engine.ONLINE,
            onClick = { prefs.engine = Engine.ONLINE },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = { WbIcon(R.drawable.ic_cloud, null, Modifier.size(18.dp)) },
            label = { Text(stringResource(R.string.rec_engine_online)) },
        )
        SegmentedButton(
            selected = prefs.engine == Engine.OFFLINE,
            enabled = status.offlineSupported,
            onClick = {
                if (requireModelForOffline && status.installedModels.isEmpty()) {
                    snack.show(needDownload)
                } else {
                    prefs.engine = Engine.OFFLINE
                }
            },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = { WbIcon(R.drawable.ic_offline_bolt, null, Modifier.size(18.dp)) },
            label = { Text(stringResource(R.string.rec_engine_offline)) },
        )
    }
}
