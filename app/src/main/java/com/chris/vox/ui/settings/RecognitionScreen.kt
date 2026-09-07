package com.chris.vox.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chris.vox.Engine
import com.chris.vox.Prefs
import com.chris.vox.R
import com.chris.vox.ui.access.PrivacyLine
import com.chris.vox.ui.access.SttAccessSection
import com.chris.vox.ui.components.DetailScaffold
import com.chris.vox.ui.components.OutlinedSection
import com.chris.vox.ui.components.ScrollColumn
import com.chris.vox.ui.components.SectionCard
import com.chris.vox.ui.components.VoxDropdown
import com.chris.vox.ui.components.VoxIcon
import com.chris.vox.ui.components.VoxRow
import com.chris.vox.ui.components.fileSize
import com.chris.vox.ui.components.languageLabel
import com.chris.vox.ui.components.offlineModelDetails
import com.chris.vox.ui.components.offlineModelLabel
import com.chris.vox.ui.components.providerShortName
import com.chris.vox.ui.components.rememberSnack
import com.chris.vox.ui.nav.NavState
import com.chris.vox.ui.nav.Screen
import com.chris.vox.ui.state.LocalAppEnv
import com.chris.vox.ui.theme.vox
import com.chris.vox.whisper.ModelCatalog

/** E1 — Erkennung (UX-Spec §2.4): Engine, Transkriptions-Zugang bzw. Offline-Modell, Sprache & Kontext. */
@Composable
fun RecognitionScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    val offline = prefs.engine == Engine.OFFLINE
    val modelInstalled = prefs.offlineModel in status.installedModels
    val stt = prefs.sttAccess()

    DetailScaffold(title = stringResource(R.string.rec_title), onBack = { nav.pop() }, snack = snack) { padding ->
        ScrollColumn(padding) {
            EngineSwitch(snack, requireModelForOffline = false)

            if (offline && !modelInstalled) {
                OutlinedSection(borderColor = MaterialTheme.vox.warningContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VoxIcon(R.drawable.ic_warning, null, Modifier.size(24.dp), MaterialTheme.vox.warning)
                        Text(stringResource(R.string.rec_no_model), style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalButton(onClick = { nav.push(Screen.Models) }) { Text(stringResource(R.string.rec_load_model)) }
                }
            }

            if (!offline) {
                SectionCard(title = stringResource(R.string.rec_card_transcription)) {
                    SttAccessSection(snack, showPrivacy = false)
                }
            } else {
                SectionCard(title = stringResource(R.string.rec_card_offline)) {
                    val model = ModelCatalog.byId(prefs.offlineModel)
                    VoxRow(
                        headline = offlineModelLabel(model.id),
                        supporting = "${fileSize(model.bytes)} · ${offlineModelDetails(model.id).substringAfterLast(" · ")}",
                        trailing = { TextButton(onClick = { nav.push(Screen.Models) }) { Text(stringResource(R.string.common_change)) } },
                    )
                    Text(
                        stringResource(R.string.rec_offline_first_use),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionCard(title = stringResource(R.string.rec_card_language)) {
                VoxDropdown(
                    label = stringResource(R.string.rec_language),
                    value = languageLabel(prefs.language),
                    options = Prefs.LANGUAGES,
                    optionLabel = { it.second },
                    onSelect = { prefs.language = it.first },
                )
                // Mistral/OpenRouter kennen kein prompt-Feld; eine context_bias-Wortliste ist nicht
                // umgesetzt (Spec §2.4, offen) — also ehrlich sagen, dass der Kontext dort nicht ankommt.
                val unsupported = !offline && !stt.provider.sttSendsPrompt
                OutlinedTextField(
                    value = prefs.apiPrompt,
                    onValueChange = { prefs.apiPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.pref_api_prompt_hint)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = {
                        Text(stringResource(if (unsupported) R.string.rec_context_unsupported else R.string.pref_api_prompt_info))
                    },
                )
            }

            PrivacyLine(
                if (offline) stringResource(R.string.rec_privacy_offline)
                else stringResource(R.string.rec_privacy_online, providerShortName(stt.provider)),
            )
        }
    }
}
