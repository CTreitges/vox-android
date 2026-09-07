package com.chris.whisperloom.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.chris.whisperloom.R

/**
 * Zusatz-Farben, die M3 nicht kennt (UX-Spec §3.1 "Zusatz-Tokens"):
 * Aufnahme-Rot, Erfolg, Warnung — jeweils mit on-/Container-Variante.
 * Zugriff im UI ueber `MaterialTheme.loom.recording` usw.
 */
@Immutable
data class LoomColors(
    val recording: Color,
    val onRecording: Color,
    val recordingContainer: Color,
    val recordingText: Color,
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

/** Ausserhalb von WhisperLoomTheme (Preview ohne Theme, Tests) gilt die LoomPalette-Fassung. */
val LocalLoomColors = staticCompositionLocalOf { LoomPalette.loomColors }

/** `MaterialTheme.loom.recording` — Gegenstueck zu `MaterialTheme.colorScheme.primary`. */
val MaterialTheme.loom: LoomColors
    @Composable
    @ReadOnlyComposable
    get() = LocalLoomColors.current

// M3-Schema aus res/values/colors.xml (einzige Farb-Wahrheit, Spec §0.2).
// Rollen ohne eigenen Spec-Token werden aus Spec-Tokens abgeleitet (siehe Kommentare),
// damit keine M3-Baseline-Farbe (lila/grau) durchscheint.
@Composable
@ReadOnlyComposable
private fun loomColorScheme(): ColorScheme = darkColorScheme(
    primary = colorResource(R.color.loom_primary),
    onPrimary = colorResource(R.color.loom_onPrimary),
    primaryContainer = colorResource(R.color.loom_primaryContainer),
    onPrimaryContainer = colorResource(R.color.loom_onPrimaryContainer),
    secondary = colorResource(R.color.loom_secondary),
    onSecondary = colorResource(R.color.loom_onSecondary),
    secondaryContainer = colorResource(R.color.loom_secondaryContainer),
    onSecondaryContainer = colorResource(R.color.loom_onSecondaryContainer),
    tertiary = colorResource(R.color.loom_tertiary),
    onTertiary = colorResource(R.color.loom_onTertiary),
    tertiaryContainer = colorResource(R.color.loom_tertiaryContainer),
    onTertiaryContainer = colorResource(R.color.loom_onTertiaryContainer),
    error = colorResource(R.color.loom_error),
    onError = colorResource(R.color.loom_onError),
    errorContainer = colorResource(R.color.loom_errorContainer),
    onErrorContainer = colorResource(R.color.loom_onErrorContainer),
    background = colorResource(R.color.loom_background),
    onBackground = colorResource(R.color.loom_onSurface), // kein eigener Token: = onSurface
    surface = colorResource(R.color.loom_surface),
    onSurface = colorResource(R.color.loom_onSurface),
    surfaceVariant = colorResource(R.color.loom_surfaceContainerHighest), // Alt-Rolle, M3-Tonwert = Highest
    onSurfaceVariant = colorResource(R.color.loom_onSurfaceVariant),
    surfaceTint = colorResource(R.color.loom_primary), // M3-Standard: = primary
    outline = colorResource(R.color.loom_outline),
    outlineVariant = colorResource(R.color.loom_outlineVariant),
    surfaceBright = colorResource(R.color.loom_surfaceBright),
    surfaceDim = colorResource(R.color.loom_background), // M3-Dark: surfaceDim = surface
    surfaceContainer = colorResource(R.color.loom_surfaceContainer),
    surfaceContainerHigh = colorResource(R.color.loom_surfaceContainerHigh),
    surfaceContainerHighest = colorResource(R.color.loom_surfaceContainerHighest),
    surfaceContainerLow = colorResource(R.color.loom_surfaceContainerLow),
    surfaceContainerLowest = colorResource(R.color.loom_surfaceContainerLowest),
    // Inverse Rollen (Snackbar): helle Flaeche mit dunklem Text, Aktion in dunklem Tuerkis.
    inverseSurface = colorResource(R.color.loom_onSurface),
    inverseOnSurface = colorResource(R.color.loom_surfaceContainer),
    inversePrimary = colorResource(R.color.loom_primaryContainer),
)

@Composable
@ReadOnlyComposable
private fun loomColors(): LoomColors = LoomColors(
    recording = colorResource(R.color.loom_recording),
    onRecording = colorResource(R.color.loom_onRecording),
    recordingContainer = colorResource(R.color.loom_recordingContainer),
    recordingText = colorResource(R.color.loom_recordingText),
    success = colorResource(R.color.loom_success),
    onSuccess = colorResource(R.color.loom_onSuccess),
    successContainer = colorResource(R.color.loom_successContainer),
    onSuccessContainer = colorResource(R.color.loom_onSuccessContainer),
    warning = colorResource(R.color.loom_warning),
    onWarning = colorResource(R.color.loom_onWarning),
    warningContainer = colorResource(R.color.loom_warningContainer),
    onWarningContainer = colorResource(R.color.loom_onWarningContainer),
)

/**
 * Fest dunkel, KEIN dynamicDarkColorScheme (Spec §0.2: Dynamic Color = nein);
 * isSystemInDarkTheme() wird bewusst ignoriert. Typografie = M3-Default (Spec §3.2).
 * https://developer.android.com/develop/ui/compose/designsystems/material3
 */
@Composable
fun WhisperLoomTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLoomColors provides loomColors()) {
        MaterialTheme(
            colorScheme = loomColorScheme(),
            typography = Typography(),
            content = content,
        )
    }
}
