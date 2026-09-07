package com.chris.whisperbar.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.chris.whisperbar.Engine
import com.chris.whisperbar.R
import com.chris.whisperbar.ui.components.WbIcon
import com.chris.whisperbar.ui.components.WbRow
import com.chris.whisperbar.ui.components.WbSheet
import com.chris.whisperbar.ui.components.modelLabel
import com.chris.whisperbar.ui.components.offlineModelLabel
import com.chris.whisperbar.ui.components.providerShortName
import com.chris.whisperbar.ui.nav.SetupFacts
import com.chris.whisperbar.ui.nav.SetupRouter
import com.chris.whisperbar.ui.nav.StepState
import com.chris.whisperbar.ui.state.LocalAppEnv
import com.chris.whisperbar.ui.theme.wb

/** W-Ue Schritt-Uebersicht (Spec §2.2): alle sichtbaren Schritte mit Status, antippbar. */
@Composable
fun SetupOverviewSheet(facts: SetupFacts, onDismiss: () -> Unit, onGoTo: (Int) -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    WbSheet(title = stringResource(R.string.setup_overview_title), onDismiss = onDismiss) { dismiss ->
        Column {
            SetupRouter.visibleSteps(facts).forEach { step ->
                val state = SetupRouter.stepState(step, facts)
                val status = if (step == SetupRouter.STEP_ACCESS && state == StepState.DONE) {
                    val stt = prefs.sttAccess()
                    if (facts.engine == Engine.OFFLINE) "Offline · ${offlineModelLabel(prefs.offlineModel)}"
                    else "${providerShortName(stt.provider)} · ${modelLabel(stt)}"
                } else {
                    stateText(state)
                }
                WbRow(
                    headline = stepName(step),
                    supporting = status,
                    leading = { OverviewIcon(state) },
                    trailing = {
                        WbIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    onClick = { onGoTo(step) },
                    stateDescription = status,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.common_close)) }
        }
    }
}

@Composable
private fun OverviewIcon(state: StepState) {
    val m = Modifier.size(24.dp)
    when (state) {
        StepState.DONE -> WbIcon(R.drawable.ic_check_circle, null, m, MaterialTheme.wb.success)
        StepState.SKIPPED -> WbIcon(R.drawable.ic_remove_circle_outline, null, m, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> WbIcon(R.drawable.ic_radio_button_unchecked, null, m, MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun stepName(step: Int): String = stringResource(
    when (step) {
        SetupRouter.STEP_ENGINE -> R.string.setup_step_1
        SetupRouter.STEP_ACCESS -> R.string.setup_step_2
        SetupRouter.STEP_MIC -> R.string.setup_step_3
        SetupRouter.STEP_OVERLAY -> R.string.setup_step_4
        SetupRouter.STEP_A11Y -> R.string.setup_step_5
        SetupRouter.STEP_NOTIF -> R.string.setup_step_6
        else -> R.string.setup_step_7
    },
)
