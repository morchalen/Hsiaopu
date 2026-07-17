package com.example.hsiaopu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hsiaopu.data.ThemeSettings
import com.example.hsiaopu.viewmodel.ChatViewModel

// ╔══════════════════════════════════════════════════════════╗
// ║  主题系统 v2.0 — 深色/浅色双模式，黑白灰为主基调       ║
// ║  FunctionalBlue 作为唯一主色调（低饱和度）              ║
// ╚══════════════════════════════════════════════════════════╝

private fun darkScheme() = darkColorScheme(
    primary = FunctionalBlueLight,
    onPrimary = Black,
    primaryContainer = FunctionalBlueContainer,
    onPrimaryContainer = DarkOnBackground,
    secondary = SystemGray5,
    onSecondary = DarkOnBackground,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    error = FunctionalRed,
    onError = OnPrimary,
    errorContainer = FunctionalRedContainer,
    onErrorContainer = DarkOnBackground,
    outline = DarkOnSurfaceVariant.copy(alpha = 0.3f),
    outlineVariant = DarkOnSurfaceVariant.copy(alpha = 0.15f),
    scrim = Black.copy(alpha = 0.6f)
)

private fun lightScheme() = lightColorScheme(
    primary = FunctionalBlue,
    onPrimary = OnPrimary,
    primaryContainer = FunctionalBlueContainer.copy(alpha = 0.15f),
    onPrimaryContainer = LightOnBackground,
    secondary = SystemGray4,
    onSecondary = LightOnBackground,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    error = FunctionalRed,
    onError = OnPrimary,
    errorContainer = FunctionalRedContainer.copy(alpha = 0.15f),
    onErrorContainer = LightOnBackground,
    outline = LightOnSurfaceVariant.copy(alpha = 0.4f),
    outlineVariant = LightOnSurfaceVariant.copy(alpha = 0.2f),
    scrim = Black.copy(alpha = 0.6f)
)

@Composable
fun HsiaopuTheme(
    content: @Composable () -> Unit
) {
    val viewModel: ChatViewModel = hiltViewModel()
    val themeSettings by viewModel.themeSettings.collectAsState()

    val isDark = when (themeSettings.isDarkTheme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) darkScheme() else lightScheme()
    val typography = AppTypography()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        content = content
    )
}