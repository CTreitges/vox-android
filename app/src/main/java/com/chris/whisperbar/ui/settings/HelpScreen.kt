package com.chris.whisperbar.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chris.whisperbar.BuildConfig
import com.chris.whisperbar.R
import com.chris.whisperbar.api.ProviderCatalog
import com.chris.whisperbar.ui.access.helpKeyText
import com.chris.whisperbar.ui.components.DetailScaffold
import com.chris.whisperbar.ui.components.ExpandableCard
import com.chris.whisperbar.ui.components.LinkRow
import com.chris.whisperbar.ui.components.OutlinedSection
import com.chris.whisperbar.ui.components.SnackController
import com.chris.whisperbar.ui.components.StepBadge
import com.chris.whisperbar.ui.components.SystemIntents
import com.chris.whisperbar.ui.components.WbIcon
import com.chris.whisperbar.ui.components.WbRow
import com.chris.whisperbar.ui.components.openLink
import com.chris.whisperbar.ui.components.providerLabel
import com.chris.whisperbar.ui.components.rememberSnack
import com.chris.whisperbar.ui.nav.NavState
import com.chris.whisperbar.ui.nav.Screen
import com.chris.whisperbar.ui.nav.SetupRouter
import com.chris.whisperbar.ui.setup.stepName
import com.chris.whisperbar.ui.state.LocalAppEnv

/** E5 — Anleitung & Hilfe (UX-Spec §2.8): sieben aufklappbare Abschnitte, [section] initial offen. */
@Composable
fun HelpScreen(section: Int, nav: NavState) {
    val ctx = LocalContext.current
    val status = LocalAppEnv.current.status
    val snack = rememberSnack()
    // Offene Abschnitte als Bitmaske (rememberSaveable-tauglich).
    var openMask by rememberSaveable { mutableIntStateOf(1 shl section.coerceIn(1, 7)) }
    fun isOpen(n: Int) = openMask and (1 shl n) != 0
    fun toggle(n: Int) { openMask = openMask xor (1 shl n) }

    DetailScaffold(title = stringResource(R.string.help_title), onBack = { nav.pop() }, snack = snack) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ExpandableCard(stringResource(R.string.help_s1_title), isOpen(1), { toggle(1) }, icon = R.drawable.ic_touch_app) {
                    HelpLine(R.drawable.ic_touch_app, stringResource(R.string.help_s1_bubble))
                    HelpLine(R.drawable.ic_keyboard, stringResource(R.string.help_s1_keyboard))
                    HelpLine(R.drawable.ic_voicemail, stringResource(R.string.help_s1_share))
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s2_title), isOpen(2), { toggle(2) }, icon = R.drawable.ic_checklist) {
                    val steps = (SetupRouter.STEP_ENGINE..SetupRouter.STEP_KEYBOARD).filter { it != SetupRouter.STEP_NOTIF || status.notifNeeded }
                    steps.forEach { step ->
                        WbRow(
                            headline = stepName(step),
                            leading = { StepBadge(step) },
                            trailing = {
                                TextButton(onClick = { nav.push(Screen.Setup(step)) }) { Text(stringResource(R.string.common_open)) }
                            },
                        )
                    }
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s3_title), isOpen(3), { toggle(3) }, icon = R.drawable.ic_key) {
                    Text(stringResource(R.string.help_s3_intro), style = MaterialTheme.typography.bodyMedium)
                    ProviderCatalog.providers.filter { it.keyUrl.isNotEmpty() }.forEach { p ->
                        LinkRow(headline = providerLabel(p), supporting = helpKeyText(p.id), url = p.keyUrl, snack = snack)
                    }
                    Text(
                        stringResource(R.string.help_prices_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s4_title), isOpen(4), { toggle(4) }, icon = R.drawable.ic_dns) {
                    Text(stringResource(R.string.help_s4_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s5_title), isOpen(5), { toggle(5) }, icon = R.drawable.ic_offline_bolt) {
                    Text(stringResource(R.string.help_s5_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s6_title), isOpen(6), { toggle(6) }, icon = R.drawable.ic_lock) {
                    PrivacyCards()
                    HelpLine(R.drawable.ic_accessibility_new, stringResource(R.string.help_s6_a11y))
                }
            }
            item {
                ExpandableCard(stringResource(R.string.help_s7_title), isOpen(7), { toggle(7) }, icon = R.drawable.ic_build) {
                    ProblemEntry(stringResource(R.string.help_p1), stringResource(R.string.help_p1_body)) { nav.push(Screen.Setup(SetupRouter.STEP_OVERLAY)) }
                    ProblemEntry(stringResource(R.string.help_p2), stringResource(R.string.help_p2_body)) { nav.push(Screen.Setup(SetupRouter.STEP_A11Y)) }
                    ProblemEntry(stringResource(R.string.help_p3), stringResource(R.string.help_p3_body)) { nav.push(Screen.Recognition) }
                    ProblemEntry(stringResource(R.string.help_p4), stringResource(R.string.help_p4_body)) {
                        SystemIntents.open(ctx, SystemIntents.appDetails(ctx))
                    }
                    ProblemEntry(stringResource(R.string.help_p5), stringResource(R.string.help_p5_body)) { nav.push(Screen.Models) }
                }
            }
            item { HelpFooter(snack) }
        }
    }
}

@Composable
private fun HelpLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        WbIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Datenschutz: zwei Karten nebeneinander, auf schmalen Geraeten untereinander. */
@Composable
private fun PrivacyCards() {
    BoxWithConstraints {
        if (maxWidth >= 480.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedSection(Modifier.weight(1f)) { PrivacyText(R.string.help_s6_online) }
                OutlinedSection(Modifier.weight(1f)) { PrivacyText(R.string.help_s6_offline) }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedSection { PrivacyText(R.string.help_s6_online) }
                OutlinedSection { PrivacyText(R.string.help_s6_offline) }
            }
        }
    }
}

@Composable
private fun PrivacyText(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium)
}

/** Akkordeon-Eintrag "Wenn etwas nicht klappt": Titel, aufklappbarer Text, Button zum Ziel. */
@Composable
private fun ProblemEntry(title: String, body: String, onAction: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        WbRow(headline = title, onClick = { open = !open }, trailing = {
            WbIcon(R.drawable.ic_expand_more, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        })
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = onAction) { Text(stringResource(R.string.common_open)) }
            }
        }
    }
}

@Composable
private fun HelpFooter(snack: SnackController) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = { openLink(ctx, GITHUB_URL, snack) }) {
            Text(stringResource(R.string.about_source), style = MaterialTheme.typography.bodySmall)
            WbIcon(R.drawable.ic_open_in_new, stringResource(R.string.cd_open_link), Modifier.padding(start = 4.dp).size(16.dp))
        }
    }
}
