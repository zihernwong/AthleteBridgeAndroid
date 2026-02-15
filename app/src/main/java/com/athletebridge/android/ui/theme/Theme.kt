package com.athletebridge.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = LogoBlue,
    onPrimary = Color.White,
    primaryContainer = LogoBlue.copy(alpha = 0.12f),
    onPrimaryContainer = LogoBlue,
    secondary = LogoGreen,
    onSecondary = Color.White,
    secondaryContainer = LogoGreen.copy(alpha = 0.12f),
    onSecondaryContainer = LogoGreen,
    error = StatusCancelled,
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

private val DarkColorScheme = darkColorScheme(
    primary = LogoBlueDark,
    onPrimary = Color.White,
    primaryContainer = LogoBlueDark.copy(alpha = 0.2f),
    onPrimaryContainer = LogoBlueDark,
    secondary = LogoGreenDark,
    onSecondary = Color.White,
    secondaryContainer = LogoGreenDark.copy(alpha = 0.2f),
    onSecondaryContainer = LogoGreenDark,
    error = StatusCancelled,
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2B2B),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

@Composable
fun AthleteBridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
