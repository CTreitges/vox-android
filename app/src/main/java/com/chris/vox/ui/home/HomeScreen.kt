package com.chris.vox.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.chris.vox.BuildConfig
import com.chris.vox.Engine
import com.chris.vox.R
import com.chris.vox.RefineMode
import com.chris.vox.overlay.BubbleAnimators
import com.chris.vox.ui.components.HeroLabelStyle
import com.chris.vox.ui.components.HeroShape
import com.chris.vox.ui.components.OutlinedSection
import com.chris.vox.ui.components.PrimaryButton
import com.chris.vox.ui.components.ScrollColumn
import com.chris.vox.ui.components.SectionCard
import com.chris.vox.ui.components.SmallTopBar
import com.chris.vox.ui.components.StatusIcon
import com.chris.vox.ui.components.Tone
import com.chris.vox.ui.components.VoxIcon
import com.chris.vox.ui.components.VoxRow
import com.chris.vox.ui.components.fileSize
import com.chris.vox.ui.components.levelLabel
import com.chris.vox.ui.components.modelLabel
import com.chris.vox.ui.components.offlineModelLabel
import com.chris.vox.ui.components.providerShortName
import com.chris.vox.ui.components.rememberSnack
import com.chris.vox.ui.nav.NavState
import com.chris.vox.ui.nav.Screen
import com.chris.vox.ui.nav.SetupRouter
import com.chris.vox.ui.state.LocalAppEnv
import com.chris.vox.ui.theme.vox
import com.chris.vox.whisper.ModelCatalog

/** H — Home (UX-Spec §2.1): Hero-Karte, ReadinessBanner, Status-Karte, Teilen-Hinweis, Fusszeile. */
@Composable
fun HomeScreen(nav: NavState) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val snack = rememberSnack()
    val control = rememberBubbleControl(snack) { nav.push(Screen.Setup(SetupRouter.STEP_OVERLAY)) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SmallTopBar(title = stringResource(R.string.app_name)) {
                IconButton(onClick = { nav.push(Screen.Help(1)) }) {
                    VoxIcon(R.drawable.ic_help, stringResource(R.string.home_cd_help))
                }
                IconButton(onClick = { nav.push(Screen.SettingsHub) }) {
                    VoxIcon(R.drawable.ic_settings, stringResource(R.string.home_cd_settings))
                }
            }
        },
        snackbarHost = { SnackbarHost(snack.host) },
    ) { padding ->
        ScrollColumn(padding) {
            HeroCard(control, onFix = { nav.push(Screen.Setup(it)) })

            val modelInstalled = prefs.offlineModel in status.installedModels
            val banner = HomeStatus.banner(status.a11yRunning, prefs.engine, modelInstalled, status.notifNeeded, status.notifGranted)
            if (banner != HomeStatus.Banner.NONE) {
                ReadinessBanner(banner) {
                    when (banner) {
                        HomeStatus.Banner.A11Y -> nav.push(Screen.Setup(SetupRouter.STEP_A11Y))
                        HomeStatus.Banner.MODEL -> nav.push(Screen.Models)
                        HomeStatus.Banner.NOTIF -> nav.push(Screen.Setup(SetupRouter.STEP_NOTIF))
                        HomeStatus.Banner.NONE -> Unit
                    }
                }
            }

            StatusCard(nav, modelInstalled)

            OutlinedSection(contentPadding = PaddingValues(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VoxIcon(R.drawable.ic_voicemail, null, Modifier.size(24.dp), MaterialTheme.colorScheme.tertiary)
                    Text(
                        stringResource(R.string.home_share_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { nav.push(Screen.Tutorial(Screen.Tutorial.PAGE_SHARE)) }) { Text(stringResource(R.string.common_more)) }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.home_version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                TextButton(onClick = { nav.push(Screen.Setup(SetupRouter.STEP_ENGINE)) }) {
                    Text(stringResource(R.string.home_rerun_setup), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** Hero-Karte: Icon-Kreis (Puls im Laufzustand), Titel/Untertitel, 64-dp-Hauptbutton, Warn-Chip. */
@Composable
private fun HeroCard(control: BubbleControl, onFix: (Int) -> Unit) {
    val vox = MaterialTheme.vox
    val running = control.running
    SectionCard(shape = HeroShape) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroIcon(running)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_hero_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (running) R.string.home_hero_sub_on else R.string.home_hero_sub_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val label = stringResource(if (running) R.string.home_hero_btn_stop else R.string.home_hero_btn_start)
        val state = stringResource(if (running) R.string.home_state_running else R.string.home_state_stopped)
        PrimaryButton(
            text = label,
            onClick = control.toggle,
            enabled = control.blocked == HomeStatus.Blocked.NONE,
            working = control.pending,
            leadingIcon = if (running) R.drawable.ic_stop else R.drawable.ic_play_arrow,
            height = 64.dp,
            tonal = running,
            colors = if (running) {
                ButtonDefaults.filledTonalButtonColors(containerColor = vox.recordingContainer, contentColor = vox.recordingText)
            } else {
                null
            },
            textStyle = HeroLabelStyle,
            modifier = Modifier.semantics {
                contentDescription = label
                stateDescription = state
            },
        )
        if (control.blocked != HomeStatus.Blocked.NONE) {
            val mic = control.blocked == HomeStatus.Blocked.MIC
            AssistChip(
                onClick = { onFix(if (mic) SetupRouter.STEP_MIC else SetupRouter.STEP_OVERLAY) },
                label = { Text(stringResource(if (mic) R.string.home_blocked_mic else R.string.home_blocked_overlay)) },
                leadingIcon = { VoxIcon(R.drawable.ic_warning, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = vox.warningContainer,
                    labelColor = vox.onWarningContainer,
                    leadingIconContentColor = vox.onWarningContainer,
                ),
                border = null,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

@Composable
private fun HeroIcon(running: Boolean) {
    val ctx = LocalContext.current
    val vox = MaterialTheme.vox
    val cs = MaterialTheme.colorScheme
    val reduceMotion = remember { BubbleAnimators.reduceMotion(ctx) }
    val fill by animateColorAsState(if (running) vox.recordingContainer else cs.primaryContainer, tween(250), label = "fill")
    val tint by animateColorAsState(if (running) vox.recordingText else cs.onPrimaryContainer, tween(250), label = "tint")
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        if (running && !reduceMotion) PulseRing()
        Box(
            Modifier
                .size(56.dp)
                .background(fill, CircleShape)
                .then(if (running) Modifier.border(2.dp, vox.recording, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            VoxIcon(R.drawable.ic_mic, null, Modifier.size(28.dp), tint)
        }
    }
}

/** Aufnahme-Puls (Spec §5.4): Ring 1,0 -> 1,35, Alpha 0,45 -> 0, 1200 ms, LinearOutSlowIn, Restart. */
@Composable
private fun PulseRing() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val fraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "fraction",
    )
    val color = MaterialTheme.vox.recording
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer {
                val s = 1f + 0.35f * fraction
                scaleX = s
                scaleY = s
                alpha = 0.45f * (1f - fraction)
            }
            .border(2.dp, color, CircleShape),
    )
}

@Composable
private fun ReadinessBanner(banner: HomeStatus.Banner, onFix: () -> Unit) {
    val vox = MaterialTheme.vox
    Column(
        Modifier
            .fillMaxWidth()
            .background(vox.warningContainer, com.chris.vox.ui.components.CardShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VoxIcon(R.drawable.ic_warning, null, Modifier.size(24.dp), vox.onWarningContainer)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.home_banner_title), style = MaterialTheme.typography.titleSmall, color = vox.onWarningContainer)
                Text(
                    stringResource(
                        when (banner) {
                            HomeStatus.Banner.MODEL -> R.string.home_banner_model
                            HomeStatus.Banner.NOTIF -> R.string.home_banner_notif
                            else -> R.string.home_banner_a11y
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = vox.onWarningContainer,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = onFix, modifier = Modifier.height(40.dp)) {
                Text(stringResource(R.string.home_banner_fix))
            }
        }
    }
}

/** Status-Karte: 4–5 klickbare Zeilen mit Status-Icon, Wert als stateDescription. */
@Composable
private fun StatusCard(nav: NavState, modelInstalled: Boolean) {
    val env = LocalAppEnv.current
    val prefs = env.prefs
    val status = env.status
    val stt = prefs.sttAccess()

    val recognitionText = when (prefs.engine) {
        Engine.ONLINE -> stringResource(R.string.home_val_online, providerShortName(stt.provider), modelLabel(stt))
        Engine.OFFLINE -> if (modelInstalled) {
            stringResource(R.string.home_val_offline, offlineModelLabel(prefs.offlineModel), fileSize(ModelCatalog.byId(prefs.offlineModel).bytes))
        } else {
            stringResource(R.string.home_val_offline_missing)
        }
        null -> stringResource(R.string.setup_chip_open)
    }
    val refineText = if (prefs.refineMode == RefineMode.OFF) stringResource(R.string.home_val_refine_off)
    else stringResource(R.string.home_val_refine, levelLabel(prefs.refineMode), modelLabel(prefs.llmAccess()))
    val permTone = HomeStatus.permissionsTone(status.micGranted, status.canDrawOverlays, status.a11yRunning)
    val permText = when (permTone) {
        Tone.SUCCESS -> stringResource(R.string.home_val_perms_ok)
        Tone.WARNING -> stringResource(R.string.home_val_perms_a11y_missing)
        else -> stringResource(R.string.home_val_perms_missing)
    }
    val keyboard = HomeStatus.keyboard(status.imeEnabled, status.imeSelected)
    val keyboardText = stringResource(
        when (keyboard) {
            HomeStatus.Keyboard.ACTIVE -> R.string.home_val_kb_active
            HomeStatus.Keyboard.ENABLED -> R.string.home_val_kb_enabled
            HomeStatus.Keyboard.OFF -> R.string.home_val_kb_off
        },
    )
    val installedCount = status.installedModels.size

    OutlinedSection(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), gap = 0.dp) {
        Text(
            stringResource(R.string.home_status_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        StatusRow(stringResource(R.string.home_row_recognition), recognitionText, HomeStatus.recognitionTone(prefs.engine, modelInstalled)) {
            nav.push(Screen.Recognition)
        }
        StatusRow(stringResource(R.string.home_row_refine), refineText, Tone.NEUTRAL, R.drawable.ic_auto_fix_high) {
            nav.push(Screen.TextSettings)
        }
        StatusRow(stringResource(R.string.home_row_permissions), permText, permTone) { nav.push(Screen.ButtonKeyboard) }
        StatusRow(
            stringResource(R.string.home_row_keyboard), keyboardText,
            if (keyboard == HomeStatus.Keyboard.ACTIVE) Tone.SUCCESS else Tone.NEUTRAL, R.drawable.ic_keyboard,
        ) { nav.push(Screen.ButtonKeyboard) }
        if (HomeStatus.showModelsRow(installedCount, prefs.engine)) {
            val text = if (installedCount > 0) stringResource(R.string.home_val_models, installedCount, fileSize(status.modelsUsedBytes))
            else stringResource(R.string.home_val_models_none)
            StatusRow(stringResource(R.string.home_row_models), text, if (installedCount > 0) Tone.SUCCESS else Tone.WARNING) {
                nav.push(Screen.Models)
            }
        }
    }
}

@Composable
private fun StatusRow(headline: String, value: String, tone: Tone, neutralIcon: Int = R.drawable.ic_info, onClick: () -> Unit) {
    VoxRow(
        headline = headline,
        supporting = value,
        leading = { StatusIcon(tone, neutralIcon) },
        trailing = { VoxIcon(R.drawable.ic_chevron_right, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onClick,
        stateDescription = value,
    )
}
