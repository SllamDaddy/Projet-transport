package com.example.gareter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private val LightColorScheme = lightColorScheme(
    primary              = Blue700,
    onPrimary            = CardWhite,
    primaryContainer     = Blue500,
    onPrimaryContainer   = CardWhite,
    secondary            = Blue600,
    onSecondary          = CardWhite,
    secondaryContainer   = BlueBg,
    onSecondaryContainer = TextPrimary,
    background           = BlueBg,
    onBackground         = TextPrimary,
    surface              = CardWhite,
    onSurface            = TextPrimary,
    surfaceVariant       = CardSurface,
    onSurfaceVariant     = TextSecondary,
    outline              = Blue200,
    error                = DangerRed,
    onError              = CardWhite,
)

private val DarkColorScheme = darkColorScheme(
    primary              = Blue200,
    onPrimary            = DarkSurface,
    primaryContainer     = Blue700,
    onPrimaryContainer   = DarkOnSurface,
    secondary            = Blue500,
    onSecondary          = DarkBg,
    secondaryContainer   = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    background           = DarkBg,
    onBackground         = DarkOnSurface,
    surface              = DarkSurface,
    onSurface            = DarkOnSurface,
    surfaceVariant       = DarkSurfaceVariant,
    onSurfaceVariant     = DarkOnSurfaceVariant,
    outline              = DarkOutline,
    error                = DangerRedDark,
    onError              = DarkBg,
)

@Composable
fun GareTERTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT  -> false
        ThemeMode.DARK   -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkColorScheme else LightColorScheme,
        typography  = Typography,
        shapes      = Shapes,
        content     = content,
    )
}
