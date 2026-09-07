package com.chris.whisperbar.ui.theme

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
import com.chris.whisperbar.R

/**
 * Zusatz-Farben, die M3 nicht kennt (UX-Spec §3.1 "Zusatz-Tokens"):
 * Aufnahme-Rot, Erfolg, Warnung — jeweils mit on-/Container-Variante.
 * Zugriff im UI ueber `MaterialTheme.wb.recording` usw.
 */
@Immutable
data class WbColors(
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

/** Ausserhalb von WhisperBarTheme (Preview ohne Theme, Tests) gilt die WbPalette-Fassung. */
val LocalWbColors = staticCompositionLocalOf { WbPalette.wbColors }

/** `MaterialTheme.wb.recording` — Gegenstueck zu `MaterialTheme.colorScheme.primary`. */
val MaterialTheme.wb: WbColors
    @Composable
    @ReadOnlyComposable
    get() = LocalWbColors.current

// M3-Schema aus res/values/colors.xml (einzige Farb-Wahrheit, Spec §0.2).
// Rollen ohne eigenen Spec-Token werden aus Spec-Tokens abgeleitet (siehe Kommentare),
// damit keine M3-Baseline-Farbe (lila/grau) durchscheint.
@Composable
@ReadOnlyComposable
private fun wbColorScheme(): ColorScheme = darkColorScheme(
    primary = colorResource(R.color.wb_primary),
    onPrimary = colorResource(R.color.wb_onPrimary),
    primaryContainer = colorResource(R.color.wb_primaryContainer),
    onPrimaryContainer = colorResource(R.color.wb_onPrimaryContainer),
    secondary = colorResource(R.color.wb_secondary),
    onSecondary = colorResource(R.color.wb_onSecondary),
    secondaryContainer = colorResource(R.color.wb_secondaryContainer),
    onSecondaryContainer = colorResource(R.color.wb_onSecondaryContainer),
    tertiary = colorResource(R.color.wb_tertiary),
    onTertiary = colorResource(R.color.wb_onTertiary),
    tertiaryContainer = colorResource(R.color.wb_tertiaryContainer),
    onTertiaryContainer = colorResource(R.color.wb_onTertiaryContainer),
    error = colorResource(R.color.wb_error),
    onError = colorResource(R.color.wb_onError),
    errorContainer = colorResource(R.color.wb_errorContainer),
    onErrorContainer = colorResource(R.color.wb_onErrorContainer),
    background = colorResource(R.color.wb_background),
    onBackground = colorResource(R.color.wb_onSurface), // kein eigener Token: = onSurface
    surface = colorResource(R.color.wb_surface),
    onSurface = colorResource(R.color.wb_onSurface),
    surfaceVariant = colorResource(R.color.wb_surfaceContainerHighest), // Alt-Rolle, M3-Tonwert = Highest
    onSurfaceVariant = colorResource(R.color.wb_onSurfaceVariant),
    surfaceTint = colorResource(R.color.wb_primary), // M3-Standard: = primary
    outline = colorResource(R.color.wb_outline),
    outlineVariant = colorResource(R.color.wb_outlineVariant),
    surfaceBright = colorResource(R.color.wb_surfaceBright),
    surfaceDim = colorResource(R.color.wb_background), // M3-Dark: surfaceDim = surface
    surfaceContainer = colorResource(R.color.wb_surfaceContainer),
    surfaceContainerHigh = colorResource(R.color.wb_surfaceContainerHigh),
    surfaceContainerHighest = colorResource(R.color.wb_surfaceContainerHighest),
    surfaceContainerLow = colorResource(R.color.wb_surfaceContainerLow),
    surfaceContainerLowest = colorResource(R.color.wb_surfaceContainerLowest),
    // Inverse Rollen (Snackbar): helle Flaeche mit dunklem Text, Aktion in dunklem Tuerkis.
    inverseSurface = colorResource(R.color.wb_onSurface),
    inverseOnSurface = colorResource(R.color.wb_surfaceContainer),
    inversePrimary = colorResource(R.color.wb_primaryContainer),
)

@Composable
@ReadOnlyComposable
private fun wbColors(): WbColors = WbColors(
    recording = colorResource(R.color.wb_recording),
    onRecording = colorResource(R.color.wb_onRecording),
    recordingContainer = colorResource(R.color.wb_recordingContainer),
    recordingText = colorResource(R.color.wb_recordingText),
    success = colorResource(R.color.wb_success),
    onSuccess = colorResource(R.color.wb_onSuccess),
    successContainer = colorResource(R.color.wb_successContainer),
    onSuccessContainer = colorResource(R.color.wb_onSuccessContainer),
    warning = colorResource(R.color.wb_warning),
    onWarning = colorResource(R.color.wb_onWarning),
    warningContainer = colorResource(R.color.wb_warningContainer),
    onWarningContainer = colorResource(R.color.wb_onWarningContainer),
)

/**
 * Fest dunkel, KEIN dynamicDarkColorScheme (Spec §0.2: Dynamic Color = nein);
 * isSystemInDarkTheme() wird bewusst ignoriert. Typografie = M3-Default (Spec §3.2).
 * https://developer.android.com/develop/ui/compose/designsystems/material3
 */
@Composable
fun WhisperBarTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWbColors provides wbColors()) {
        MaterialTheme(
            colorScheme = wbColorScheme(),
            typography = Typography(),
            content = content,
        )
    }
}
