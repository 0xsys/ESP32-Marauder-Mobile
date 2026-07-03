package com.marauder.mobile.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MarauderCyan,
    onPrimary = MarauderBg,
    primaryContainer = MarauderSurfaceHi,
    onPrimaryContainer = MarauderCyan,
    secondary = CatWifi,
    onSecondary = MarauderBg,
    tertiary = CatGeneral,
    onTertiary = MarauderBg,
    background = MarauderBg,
    onBackground = MarauderText,
    surface = MarauderSurface,
    onSurface = MarauderText,
    surfaceVariant = MarauderSurfaceHi,
    onSurfaceVariant = MarauderTextDim,
    outline = MarauderOutline,
    outlineVariant = MarauderOutline,
    error = Danger,
    onError = MarauderBg,
)

private val LightColorScheme = lightColorScheme(
    primary = MarauderCyanDeep,
    onPrimary = Color.White,
    primaryContainer = MarauderSurfaceHiLight,
    onPrimaryContainer = MarauderCyanDeep,
    secondary = CatWifi,
    onSecondary = Color.White,
    tertiary = CatGeneral,
    onTertiary = Color.White,
    background = MarauderBgLight,
    onBackground = MarauderTextLight,
    surface = MarauderSurfaceLight,
    onSurface = MarauderTextLight,
    surfaceVariant = MarauderSurfaceHiLight,
    onSurfaceVariant = MarauderTextDimLight,
    outline = MarauderOutlineLight,
    outlineVariant = MarauderOutlineLight,
    error = Danger,
    onError = Color.White,
)

@Composable
fun MarauderTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Keep the status/navigation bar icon contrast in step with the theme.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MarauderTypography,
        content = content,
    )
}
