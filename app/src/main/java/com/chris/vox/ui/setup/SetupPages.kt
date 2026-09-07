package com.chris.vox.ui.setup

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chris.vox.Engine
import com.chris.vox.R
import com.chris.vox.ui.components.PrimaryButton
import com.chris.vox.ui.components.SectionCard
import com.chris.vox.ui.components.StatusIcon
import com.chris.vox.ui.components.StepBadge
import com.chris.vox.ui.components.Tone
import com.chris.vox.ui.components.VoxIcon
import com.chris.vox.ui.components.VoxRow
import com.chris.vox.ui.components.modelLabel
import com.chris.vox.ui.components.offlineModelLabel
import com.chris.vox.ui.components.providerShortName
import com.chris.vox.ui.nav.SetupFacts
import com.chris.vox.ui.nav.SetupRouter
import com.chris.vox.ui.nav.StepState
import com.chris.vox.ui.state.LocalAppEnv
import com.chris.vox.ui.theme.vox

/** W1 Willkommen (Spec §2.2): App-Motiv, Titel, drei Punkte, "Los geht's". */
@Composable
fun WelcomePage(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(120.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(96.dp))
            }
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.welcome_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.fillMaxWidth()) {
                WelcomePoint(R.drawable.ic_cloud, stringResource(R.string.welcome_point_1))
                WelcomePoint(R.drawable.ic_lock, stringResource(R.string.welcome_point_2))
                WelcomePoint(R.drawable.ic_schedule, stringResource(R.string.welcome_point_3))
            }
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            PrimaryButton(text = stringResource(R.string.welcome_start), onClick = onStart)
        }
    }
}

@Composable
private fun WelcomePoint(icon: Int, text: String) {
    VoxRow(headline = text, leading = { VoxIcon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.primary) })
}

/** W9 Fertig (Spec §2.2): Erfolgs-Kreis (Spring), Zusammenfassung, Kurzanleitung, Start-Button. */
@Composable
fun DonePage(facts: SetupFacts, onGoToStep: (Int) -> Unit, onFinish: () -> Unit) {
    val prefs = LocalAppEnv.current.prefs
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "hero",
    )

    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(MaterialTheme.vox.successContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                VoxIcon(R.drawable.ic_check, null, Modifier.size(48.dp), MaterialTheme.vox.onSuccessContainer)
            }
            Text(stringResource(R.string.setup_done_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)

            SectionCard(title = stringResource(R.string.setup_done_summary), gap = 0.dp) {
                val stt = prefs.sttAccess()
                val recognition = when (facts.engine) {
                    Engine.ONLINE -> "Online · ${providerShortName(stt.provider)} · ${modelLabel(stt)}"
                    Engine.OFFLINE -> "Offline · ${offlineModelLabel(prefs.offlineModel)}"
                    null -> stringResource(R.string.setup_status_missing)
                }
                SummaryRow(stringResource(R.string.setup_step_1), recognition, if (facts.engine != null) Tone.SUCCESS else Tone.ERROR)
                SummaryRow(stringResource(R.string.setup_step_3), stateText(SetupRouter.stepState(SetupRouter.STEP_MIC, facts)), toneOf(facts, SetupRouter.STEP_MIC))
                SummaryRow(
                    stringResource(R.string.setup_step_4), stateText(SetupRouter.stepState(SetupRouter.STEP_OVERLAY, facts)),
                    toneOf(facts, SetupRouter.STEP_OVERLAY), onClick = { onGoToStep(SetupRouter.STEP_OVERLAY) },
                )
                SummaryRow(
                    stringResource(R.string.setup_step_5),
                    if (facts.a11yRunning) stringResource(R.string.setup_status_done) else stringResource(R.string.setup_done_a11y_skipped),
                    toneOf(facts, SetupRouter.STEP_A11Y),
                    onClick = if (facts.a11yRunning) null else ({ onGoToStep(SetupRouter.STEP_A11Y) }),
                )
                if (facts.notifNeeded) {
                    SummaryRow(
                        stringResource(R.string.setup_step_6), stateText(SetupRouter.stepState(SetupRouter.STEP_NOTIF, facts)),
                        toneOf(facts, SetupRouter.STEP_NOTIF),
                        onClick = if (facts.notifGranted) null else ({ onGoToStep(SetupRouter.STEP_NOTIF) }),
                    )
                }
                SummaryRow(
                    stringResource(R.string.setup_step_7), stateText(SetupRouter.stepState(SetupRouter.STEP_KEYBOARD, facts)),
                    toneOf(facts, SetupRouter.STEP_KEYBOARD),
                    onClick = if (facts.imeEnabled) null else ({ onGoToStep(SetupRouter.STEP_KEYBOARD) }),
                )
            }

            Column(Modifier.fillMaxWidth()) {
                HowToRow(1, stringResource(R.string.help_dictate_1))
                HowToRow(2, stringResource(R.string.help_dictate_2))
                HowToRow(3, stringResource(R.string.help_dictate_3))
            }
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
            PrimaryButton(
                text = stringResource(if (prefs.overlaySkipped) R.string.setup_done_home else R.string.setup_done_start),
                onClick = onFinish,
                leadingIcon = if (prefs.overlaySkipped) null else R.drawable.ic_play_arrow,
            )
        }
    }
}

@Composable
private fun SummaryRow(headline: String, status: String, tone: Tone, onClick: (() -> Unit)? = null) {
    VoxRow(
        headline = headline,
        supporting = status,
        leading = { StatusIcon(tone, R.drawable.ic_remove_circle_outline) },
        onClick = onClick,
        stateDescription = status,
    )
}

@Composable
fun HowToRow(number: Int, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepBadge(number)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun stateText(state: StepState): String = stringResource(
    when (state) {
        StepState.DONE -> R.string.setup_status_done
        StepState.OPEN -> R.string.setup_status_missing
        StepState.SKIPPED -> R.string.setup_status_skipped
        StepState.OPTIONAL -> R.string.setup_chip_optional
    },
)

private fun toneOf(facts: SetupFacts, step: Int): Tone = when (SetupRouter.stepState(step, facts)) {
    StepState.DONE -> Tone.SUCCESS
    StepState.OPEN -> Tone.ERROR
    StepState.SKIPPED, StepState.OPTIONAL -> Tone.NEUTRAL
}
