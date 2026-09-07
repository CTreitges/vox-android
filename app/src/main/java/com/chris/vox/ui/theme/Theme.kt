package com.chris.vox.ui.theme

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
import com.chris.vox.R

/**
 * Zusatz-Farben, die M3 nicht kennt (UX-Spec §3.1 "Zusatz-Tokens"):
 * Aufnahme-Rot, Erfolg, Warnung — jeweils mit on-/Container-Variante.
 * Zugriff im UI ueber `MaterialTheme.vox.recording` usw.
 */
@Immutable
data class VoxColors(
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

/** Ausserhalb von VoxTheme (Preview ohne Theme, Tests) gilt die VoxPalette-Fassung. */
val LocalVoxColors = staticCompositionLocalOf { VoxPalette.voxColors }

/** `MaterialTheme.vox.recording` — Gegenstueck zu `MaterialTheme.colorScheme.primary`. */
val MaterialTheme.vox: VoxColors
    @Composable
    @ReadOnlyComposable
    get() = LocalVoxColors.current

// M3-Schema aus res/values/colors.xml (einzige Farb-Wahrheit, Spec §0.2).
// Rollen ohne eigenen Spec-Token werden aus Spec-Tokens abgeleitet (siehe Kommentare),
// damit keine M3-Baseline-Farbe (lila/grau) durchscheint.
@Composable
@ReadOnlyComposable
private fun voxColorScheme(): ColorScheme = darkColorScheme(
    primary = colorResource(R.color.vox_primary),
    onPrimary = colorResource(R.color.vox_onPrimary),
    primaryContainer = colorResource(R.color.vox_primaryContainer),
    onPrimaryContainer = colorResource(R.color.vox_onPrimaryContainer),
    secondary = colorResource(R.color.vox_secondary),
    onSecondary = colorResource(R.color.vox_onSecondary),
    secondaryContainer = colorResource(R.color.vox_secondaryContainer),
    onSecondaryContainer = colorResource(R.color.vox_onSecondaryContainer),
    tertiary = colorResource(R.color.vox_tertiary),
    onTertiary = colorResource(R.color.vox_onTertiary),
    tertiaryContainer = colorResource(R.color.vox_tertiaryContainer),
    onTertiaryContainer = colorResource(R.color.vox_onTertiaryContainer),
    error = colorResource(R.color.vox_error),
    onError = colorResource(R.color.vox_onError),
    errorContainer = colorResource(R.color.vox_errorContainer),
    onErrorContainer = colorResource(R.color.vox_onErrorContainer),
    background = colorResource(R.color.vox_background),
    onBackground = colorResource(R.color.vox_onSurface), // kein eigener Token: = onSurface
    surface = colorResource(R.color.vox_surface),
    onSurface = colorResource(R.color.vox_onSurface),
    surfaceVariant = colorResource(R.color.vox_surfaceContainerHighest), // Alt-Rolle, M3-Tonwert = Highest
    onSurfaceVariant = colorResource(R.color.vox_onSurfaceVariant),
    surfaceTint = colorResource(R.color.vox_primary), // M3-Standard: = primary
    outline = colorResource(R.color.vox_outline),
    outlineVariant = colorResource(R.color.vox_outlineVariant),
    surfaceBright = colorResource(R.color.vox_surfaceBright),
    surfaceDim = colorResource(R.color.vox_background), // M3-Dark: surfaceDim = surface
    surfaceContainer = colorResource(R.color.vox_surfaceContainer),
    surfaceContainerHigh = colorResource(R.color.vox_surfaceContainerHigh),
    surfaceContainerHighest = colorResource(R.color.vox_surfaceContainerHighest),
    surfaceContainerLow = colorResource(R.color.vox_surfaceContainerLow),
    surfaceContainerLowest = colorResource(R.color.vox_surfaceContainerLowest),
    // Inverse Rollen (Snackbar): helle Flaeche mit dunklem Text, Aktion in dunklem Tuerkis.
    inverseSurface = colorResource(R.color.vox_onSurface),
    inverseOnSurface = colorResource(R.color.vox_surfaceContainer),
    inversePrimary = colorResource(R.color.vox_primaryContainer),
)

@Composable
@ReadOnlyComposable
private fun voxColors(): VoxColors = VoxColors(
    recording = colorResource(R.color.vox_recording),
    onRecording = colorResource(R.color.vox_onRecording),
    recordingContainer = colorResource(R.color.vox_recordingContainer),
    recordingText = colorResource(R.color.vox_recordingText),
    success = colorResource(R.color.vox_success),
    onSuccess = colorResource(R.color.vox_onSuccess),
    successContainer = colorResource(R.color.vox_successContainer),
    onSuccessContainer = colorResource(R.color.vox_onSuccessContainer),
    warning = colorResource(R.color.vox_warning),
    onWarning = colorResource(R.color.vox_onWarning),
    warningContainer = colorResource(R.color.vox_warningContainer),
    onWarningContainer = colorResource(R.color.vox_onWarningContainer),
)

/**
 * Fest dunkel, KEIN dynamicDarkColorScheme (Spec §0.2: Dynamic Color = nein);
 * isSystemInDarkTheme() wird bewusst ignoriert. Typografie = M3-Default (Spec §3.2).
 * https://developer.android.com/develop/ui/compose/designsystems/material3
 */
@Composable
fun VoxTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalVoxColors provides voxColors()) {
        MaterialTheme(
            colorScheme = voxColorScheme(),
            typography = Typography(),
            content = content,
        )
    }
}
