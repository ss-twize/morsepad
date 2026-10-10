package com.morsepad.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.morsepad.core.settings.ThemeMode

/** Flat, high-contrast palette: solid background, contrasting keys, one accent (spec 4.3). */
@Immutable
data class PadColors(
    val background: Color,
    val keyFill: Color,
    val keyBorder: Color,
    val keyActive: Color,
    val label: Color,
    val labelActive: Color,
    val text: Color,
    val textSecondary: Color,
    /** Pending prefix. */
    val accent: Color,
    /** Cancelled prefix, save errors. */
    val warning: Color,
)

val DarkPadColors = PadColors(
    background = Color(0xFF0A0A0A),
    keyFill = Color(0xFF1B1B1B),
    keyBorder = Color(0xFF3D3D3D),
    keyActive = Color(0xFFF2F2F2),
    label = Color(0xFF8A8A8A),
    labelActive = Color(0xFF0A0A0A),
    text = Color(0xFFF2F2F2),
    textSecondary = Color(0xFF8A8A8A),
    accent = Color(0xFFFFB300),
    warning = Color(0xFFFF5252),
)

val LightPadColors = PadColors(
    background = Color(0xFFF2F2F2),
    keyFill = Color(0xFFFFFFFF),
    keyBorder = Color(0xFFB8B8B8),
    keyActive = Color(0xFF111111),
    label = Color(0xFF6B6B6B),
    labelActive = Color(0xFFF2F2F2),
    text = Color(0xFF111111),
    textSecondary = Color(0xFF6B6B6B),
    accent = Color(0xFFB26A00),
    warning = Color(0xFFC62828),
)

val LocalPadColors = staticCompositionLocalOf { DarkPadColors }

@Composable
fun MorsePadTheme(theme: ThemeMode, content: @Composable () -> Unit) {
    val pad = if (theme == ThemeMode.DARK) DarkPadColors else LightPadColors
    val scheme = if (theme == ThemeMode.DARK) {
        darkColorScheme(
            primary = pad.text, onPrimary = pad.background,
            secondary = pad.text, onSecondary = pad.background,
            background = pad.background, onBackground = pad.text,
            surface = pad.background, onSurface = pad.text,
            surfaceVariant = pad.keyFill, onSurfaceVariant = pad.textSecondary,
            surfaceContainerHigh = pad.keyFill, surfaceContainer = pad.keyFill,
            primaryContainer = pad.keyFill, onPrimaryContainer = pad.text,
            secondaryContainer = Color(0xFF3A3A3A), onSecondaryContainer = pad.text,
            outline = pad.keyBorder, error = pad.warning,
        )
    } else {
        lightColorScheme(
            primary = pad.text, onPrimary = pad.background,
            secondary = pad.text, onSecondary = pad.background,
            background = pad.background, onBackground = pad.text,
            surface = pad.background, onSurface = pad.text,
            surfaceVariant = pad.keyFill, onSurfaceVariant = pad.textSecondary,
            surfaceContainerHigh = pad.keyFill, surfaceContainer = pad.keyFill,
            primaryContainer = pad.keyFill, onPrimaryContainer = pad.text,
            secondaryContainer = Color(0xFFD6D6D6), onSecondaryContainer = pad.text,
            outline = pad.keyBorder, error = pad.warning,
        )
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPadColors provides pad, content = content)
    }
}
