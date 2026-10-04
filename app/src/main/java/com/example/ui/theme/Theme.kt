package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val UbaidLightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = Color.White,
    primaryContainer = AppleBlueLight,
    onPrimaryContainer = AppleDarkBlue,
    secondary = AppleTextSecondary,
    onSecondary = Color.White,
    background = AppleBackground,
    onBackground = AppleTextPrimary,
    surface = AppleSurface,
    onSurface = AppleTextPrimary,
    surfaceVariant = AppleSurfaceVariant,
    onSurfaceVariant = AppleTextSecondary,
    outline = AppleBorder,
    outlineVariant = AppleBorderLight,
    error = AppleRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use intentional Apple-inspired palette
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = UbaidLightColorScheme,
        typography = Typography,
        content = content
    )
}
