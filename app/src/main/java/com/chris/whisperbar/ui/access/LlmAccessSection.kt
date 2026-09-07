package com.chris.whisperbar.ui.access

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chris.whisperbar.Engine
import com.chris.whisperbar.R
import com.chris.whisperbar.api.AccessResolver
import com.chris.whisperbar.api.Provider
import com.chris.whisperbar.api.ProviderCatalog
import com.chris.whisperbar.api.ServerUrlCheck
import com.chris.whisperbar.ui.components.ApiKeyField
import com.chris.whisperbar.ui.components.InfoCard
import com.chris.whisperbar.ui.components.SnackController
import com.chris.whisperbar.ui.components.SwitchRow
import com.chris.whisperbar.ui.components.WbDropdown
import com.chris.whisperbar.ui.components.WbIcon
import com.chris.whisperbar.ui.components.modelLabel
import com.chris.whisperbar.ui.components.providerLabel
import com.chris.whisperbar.ui.components.providerShortName
import com.chris.whisperbar.ui.state.LocalAppEnv
import com.chris.whisperbar.ui.theme.wb

/** Karte "Zugang fuer die Textverbesserung" (E2, Spec §2.5): Schalter, eigener Anbieter, Modell, Test. */
@Composable
fun LlmAccessSection(snack: SnackController) {
    val prefs = LocalAppEnv.current.prefs
    val stt = prefs.sttAccess()
    val llm = prefs.llmAccess()
    val provider = llm.provider
    val useOwn = prefs.llmUseOwn
    val offlineWithoutOwn = prefs.engine == Engine.OFFLINE && !useOwn
    val providers = ProviderCatalog.llmProviders
    val labels = providers.associate { it.id to providerLabel(it) }
    var showKeySheet by rememberSaveable { mutableStateOf(false) }
    var showCustomModel by rememberSaveable { mutableStateOf(false) }

    // Eigener Zugang: den Erkennungs-Anbieter uebernehmen, wenn er Textmodelle hat, sonst OpenAI.
    fun switchToOwn() {
        prefs.llmProviderId = if (stt.provider.hasLlm) stt.provider.id else ProviderCatalog.OPENAI_ID
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SwitchRow(
            headline = stringResource(R.string.text_own_access),
            supporting = if (useOwn) stringResource(R.string.text_own_access_on)
            else stringResource(R.string.text_own_access_off, providerShortName(stt.provider)),
            checked = useOwn,
            onCheckedChange = { on -> if (on) switchToOwn() else prefs.llmProviderId = AccessResolver.LLM_SAME },
        )

        AnimatedVisibility(visible = useOwn) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WbDropdown(
                    label = stringResource(R.string.text_llm_provider),
                    value = labels[provider.id] ?: provider.name,
                    options = providers,
                    optionLabel = { labels[it.id] ?: it.name },
                    onSelect = { p ->
                        if (p.id != prefs.llmProviderId) {
                            // Kein Key darf versehentlich an einen anderen Anbieter gehen.
                            prefs.llmProviderId = p.id
                            prefs.llmUrl = ""
                            prefs.llmKey = ""
                            prefs.llmModel = ""
                        }
                    },
                    supportingText = if (!provider.isCustom) ({ Text(llm.baseUrl) }) else null,
                )
                if (provider.isCustom) {
                    val problem = if (prefs.llmUrl.isBlank()) null else ServerUrlCheck.check(prefs.llmUrl, provider)
                    OutlinedTextField(
                        value = prefs.llmUrl,
                        onValueChange = { prefs.llmUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.rec_base_url)) },
                        placeholder = { Text("http://server:11434/v1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        isError = problem?.severity == ServerUrlCheck.Severity.ERROR,
                        supportingText = {
                            Text(if (problem != null) urlProblemText(problem) else stringResource(R.string.rec_base_url_hint))
                        },
                    )
                }
                ApiKeyField(value = prefs.llmKey, onValueChange = { prefs.llmKey = it }, optional = provider.isCustom)
                ProviderNote(provider)
                TextButton(onClick = { showKeySheet = true }) {
                    WbIcon(R.drawable.ic_help, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rec_key_where))
                }
            }
        }

        when {
            offlineWithoutOwn -> InfoCard(
                text = stringResource(R.string.text_needs_online),
                icon = R.drawable.ic_warning,
                container = MaterialTheme.wb.warningContainer,
                onContainer = MaterialTheme.wb.onWarningContainer,
                action = {
                    FilledTonalButton(onClick = { switchToOwn() }) { Text(stringResource(R.string.text_add_access)) }
                },
            )
            provider.llmModels.isEmpty() -> OutlinedTextField(
                value = prefs.llmModel,
                onValueChange = { prefs.llmModel = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.text_llm_model)) },
                placeholder = { Text(stringResource(R.string.text_llm_model_placeholder)) },
                singleLine = true,
                isError = llm.model.isBlank(),
                supportingText = { Text(stringResource(R.string.model_custom_info)) },
            )
            else -> WbDropdown(
                label = stringResource(R.string.text_llm_model),
                value = modelLabel(llm),
                options = provider.llmModels,
                optionLabel = { it.label },
                onSelect = { prefs.llmModel = it.id },
                extraOption = stringResource(R.string.text_model_custom),
                onExtra = { showCustomModel = true },
            )
        }

        TestAccessRow(label = stringResource(R.string.text_test), enabled = !offlineWithoutOwn) {
            AccessTest.llm(prefs.llmAccess(), prefs.language)
        }
    }

    if (showKeySheet) KeySheet(providers, snack) { showKeySheet = false }
    if (showCustomModel) {
        CustomModelSheet(
            placeholder = stringResource(R.string.text_llm_model_placeholder),
            initial = if (llm.modelOption == null) llm.model else "",
            onApply = { prefs.llmModel = it },
            onDismiss = { showCustomModel = false },
        )
    }
}

/** Hinweis-Chips zu Gemini (Training), DeepSeek (China), Anthropic (Kompatibilitaetsschicht). */
@Composable
private fun ProviderNote(provider: Provider) {
    val wb = MaterialTheme.wb
    when (provider.id) {
        "gemini" -> InfoCard(stringResource(R.string.text_gemini_warning), icon = R.drawable.ic_warning, container = wb.warningContainer, onContainer = wb.onWarningContainer)
        "deepseek" -> InfoCard(stringResource(R.string.text_deepseek_warning), icon = R.drawable.ic_warning, container = wb.warningContainer, onContainer = wb.onWarningContainer)
        "anthropic" -> InfoCard(stringResource(R.string.text_anthropic_note))
    }
}
