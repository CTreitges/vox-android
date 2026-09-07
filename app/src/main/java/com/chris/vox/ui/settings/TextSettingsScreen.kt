package com.chris.vox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.chris.vox.R
import com.chris.vox.RefineMode
import com.chris.vox.ui.access.LlmAccessSection
import com.chris.vox.ui.components.DetailScaffold
import com.chris.vox.ui.components.ScrollColumn
import com.chris.vox.ui.components.SectionCard
import com.chris.vox.ui.components.SwitchRow
import com.chris.vox.ui.components.VoxRow
import com.chris.vox.ui.components.levelLabel
import com.chris.vox.ui.components.rememberSnack
import com.chris.vox.ui.nav.NavState
import com.chris.vox.ui.state.LocalAppEnv

/** E2 — Text (UX-Spec §2.5): Stufe, KI-Fuellwoerter, eigener LLM-Zugang, Regeln ohne KI, Sheet B3. */
@Composable
fun TextSettingsScreen(nav: NavState) {
    val prefs = LocalAppEnv.current.prefs
    val snack = rememberSnack()
    var showFillers by rememberSaveable { mutableStateOf(false) }
    val off = prefs.refineMode == RefineMode.OFF

    DetailScaffold(title = stringResource(R.string.text_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            SectionCard(
                title = stringResource(R.string.text_card_refine),
                titleIcon = R.drawable.ic_auto_fix_high,
                titleIconTint = MaterialTheme.colorScheme.tertiary,
                gap = 4.dp,
            ) {
                Column(Modifier.selectableGroup()) {
                    RefineMode.SETTINGS.forEach { mode ->
                        val selected = prefs.refineMode == mode
                        VoxRow(
                            headline = levelLabel(mode),
                            supporting = stringResource(levelSubtitle(mode)),
                            modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { prefs.refineMode = mode },
                            trailing = { RadioButton(selected = selected, onClick = null) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.text_level_cost),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    headline = stringResource(R.string.pref_smart_fillers),
                    supporting = stringResource(if (off) R.string.text_smart_needs_level else R.string.pref_smart_fillers_info),
                    checked = prefs.smartFillers,
                    onCheckedChange = { prefs.smartFillers = it },
                    enabled = !off,
                )
            }

            SectionCard(title = stringResource(R.string.text_card_access)) {
                LlmAccessSection(snack)
            }

            SectionCard(title = stringResource(R.string.text_card_rules), gap = 4.dp) {
                SwitchRow(
                    headline = stringResource(R.string.pref_remove_fillers),
                    supporting = stringResource(R.string.text_fillers_sub),
                    checked = prefs.removeFillers,
                    onCheckedChange = { prefs.removeFillers = it },
                )
                TextButton(onClick = { showFillers = true }, enabled = prefs.removeFillers) {
                    Text(stringResource(R.string.text_fillers_edit))
                }
                SwitchRow(
                    headline = stringResource(R.string.pref_auto_cap),
                    checked = prefs.autoCapitalize,
                    onCheckedChange = { prefs.autoCapitalize = it },
                )
                SwitchRow(
                    headline = stringResource(R.string.pref_trailing_space),
                    checked = prefs.trailingSpace,
                    onCheckedChange = { prefs.trailingSpace = it },
                )
                if (prefs.smartFillers && !off) {
                    Text(
                        stringResource(R.string.text_fillers_paused),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showFillers) FillersSheet { showFillers = false }
}

private fun levelSubtitle(mode: RefineMode): Int = when (mode) {
    RefineMode.OFF -> R.string.text_level_off_sub
    RefineMode.POLISH, RefineMode.PARAGRAPHS -> R.string.text_level_smooth_sub
    RefineMode.BEAUTIFY -> R.string.text_level_beautify_sub
    RefineMode.SUMMARIZE -> R.string.text_level_summarize_sub
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
