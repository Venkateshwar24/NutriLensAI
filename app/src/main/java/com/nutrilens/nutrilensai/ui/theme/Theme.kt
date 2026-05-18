package com.nutrilens.nutrilensai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary             = NutriPrimary,
    onPrimary           = Color.White,
    primaryContainer    = NutriPrimaryLight,
    onPrimaryContainer  = NutriPrimaryDark,
    secondary           = NutriPrimaryAccent,
    onSecondary         = Color.White,
    background          = NutriBackground,
    onBackground        = NutriTextPrimary,
    surface             = NutriSurface,
    onSurface           = NutriTextPrimary,
    surfaceVariant      = NutriPrimaryLight,
    onSurfaceVariant    = NutriTextSecondary,
    outline             = NutriDivider,
    outlineVariant      = Color(0xFFCBD5E1),
)

private val DarkColorScheme = darkColorScheme(
    primary             = NutriPrimaryAccent,
    onPrimary           = Color(0xFF1E1B4B),
    primaryContainer    = NutriPrimaryDark,
    onPrimaryContainer  = NutriPrimaryLight,
    secondary           = NutriPrimary,
    background          = Color(0xFF0F172A),
    onBackground        = Color(0xFFF1F5F9),
    surface             = Color(0xFF1E293B),
    onSurface           = Color(0xFFE2E8F0),
    surfaceVariant      = Color(0xFF334155),
    onSurfaceVariant    = Color(0xFF94A3B8),
)

@Composable
fun NutriLensAITheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = NutriTypography,
        content     = content
    )
}
