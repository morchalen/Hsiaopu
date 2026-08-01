package com.example.hsiaopu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hsiaopu.viewmodel.ChatViewModel

/**
 * 当前 App 实际是否为深色模式（跟随设置页主题开关，而非系统主题）。
 * 所有需要"深浅配色二选一"的组件都应读取它，禁止直接使用 isSystemInDarkTheme()，
 * 否则用户手动切换主题后，部分界面会仍然跟随系统主题导致配色错乱。
 */
val LocalIsDark = staticCompositionLocalOf { false }

@Composable
fun HsiaopuTheme(
    content: @Composable () -> Unit
) {
    val viewModel: ChatViewModel = hiltViewModel()
    val themeSettings by viewModel.themeSettings.collectAsState()

    val isDark = when (themeSettings.themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) darkScheme() else lightScheme()
    val typography = AppTypography()

    CompositionLocalProvider(LocalIsDark provides isDark) {
        // 状态栏/导航栏图标明暗跟随 App 实际主题（保证系统栏始终可见，不随系统主题错乱）
        val view = LocalView.current
        if (!view.isInEditMode) {
            SideEffect {
                val window = (view.context as Activity).window
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

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