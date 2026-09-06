package com.chris.whisperbar.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Fest dunkel, KEIN dynamicDarkColorScheme (Entscheidung: Dynamic Color = nein).
// https://developer.android.com/develop/ui/compose/designsystems/material3
private val WhisperBarColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentPressed,
    onPrimaryContainer = Color.White,
    secondary = OnDarkDim,
    onSecondary = Background,
    error = Recording,
    onError = Color.White,
    background = Background,
    onBackground = OnDark,
    surface = Surface,
    onSurface = OnDark,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = OnDarkDim,
    outline = OnDarkDim,
)

@Composable
fun WhisperBarTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme() bewusst ignoriert: die App ist immer dunkel.
    MaterialTheme(
        colorScheme = WhisperBarColors,
        typography = Typography(),
        content = content,
    )
}
