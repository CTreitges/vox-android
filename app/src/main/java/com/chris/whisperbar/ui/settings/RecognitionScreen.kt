package com.chris.whisperbar.ui.settings

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
import com.chris.whisperbar.Engine
import com.chris.whisperbar.Prefs
import com.chris.whisperbar.R
import com.chris.whisperbar.ui.access.PrivacyLine
import com.chris.whisperbar.ui.access.SttAccessSection
import com.chris.whisperbar.ui.components.DetailScaffold
import com.chris.whisperbar.ui.components.OutlinedSection
import com.chris.whisperbar.ui.components.ScrollColumn
import com.chris.whisperbar.ui.components.SectionCard
import com.chris.whisperbar.ui.components.WbDropdown
import com.chris.whisperbar.ui.components.WbIcon
import com.chris.whisperbar.ui.components.WbRow
import com.chris.whisperbar.ui.components.fileSize
import com.chris.whisperbar.ui.components.languageLabel
import com.chris.whisperbar.ui.components.offlineModelDetails
import com.chris.whisperbar.ui.components.offlineModelLabel
import com.chris.whisperbar.ui.components.providerShortName
import com.chris.whisperbar.ui.components.rememberSnack
import com.chris.whisperbar.ui.nav.NavState
import com.chris.whisperbar.ui.nav.Screen
import com.chris.whisperbar.ui.state.LocalAppEnv
import com.chris.whisperbar.ui.theme.wb
import com.chris.whisperbar.whisper.ModelCatalog

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
                OutlinedSection(borderColor = MaterialTheme.wb.warningContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WbIcon(R.drawable.ic_warning, null, Modifier.size(24.dp), MaterialTheme.wb.warning)
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
                    WbRow(
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
                WbDropdown(
                    label = stringResource(R.string.rec_language),
                    value = languageLabel(prefs.language),
                    options = Prefs.LANGUAGES,
                    optionLabel = { it.second },
                    onSelect = { prefs.language = it.first },
                )
                val wordList = !offline && !stt.provider.sttSendsPrompt
                OutlinedTextField(
                    value = prefs.apiPrompt,
                    onValueChange = { prefs.apiPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (wordList) R.string.rec_context_words else R.string.pref_api_prompt_hint)) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    supportingText = {
                        Text(stringResource(if (wordList) R.string.rec_context_words_info else R.string.pref_api_prompt_info))
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
