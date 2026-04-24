package com.darkwizards.payments.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WizardColorScheme = darkColorScheme(
    primary = Color(0xFF8A2CE2),
    onPrimary = Color.White,
    secondary = Color(0xFF7C68EE),
    onSecondary = Color.White,
    tertiary = Color(0xFF6A5BCD),
    onTertiary = Color.White,
    surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF5E4B8B),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFE0D0FF),
    background = Color(0xFF4A0080),
    onBackground = Color.White,
    error = Color(0xFFCF6679),
    onError = Color.Black
)

@Composable
fun ConstellationPaymentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WizardColorScheme,
        typography = Typography,
        content = content
    )
}
