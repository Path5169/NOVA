package com.nova.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val NovaDarkColorScheme = darkColorScheme(
    primary = NovaAccent,
    onPrimary = NovaBackground,
    secondary = NovaAccentDim,
    background = NovaBackground,
    onBackground = NovaTextPrimary,
    surface = NovaSurface,
    onSurface = NovaTextPrimary,
    surfaceVariant = NovaSurfaceRaised,
    onSurfaceVariant = NovaTextSecondary,
    outline = NovaSurfaceOutline,
    error = NovaBad,
    onError = NovaTextPrimary
)

// NOVA is dark-first per spec. A light scheme exists so the app doesn't break
// on forced light-mode devices, but it deliberately reuses the same instrument
// palette rather than inverting to a generic white Material layout.
private val NovaLightColorScheme = lightColorScheme(
    primary = NovaAccentDim,
    onPrimary = Color_White,
    secondary = NovaAccent,
    background = Color_White,
    onBackground = Color_Ink,
    surface = Color_White,
    onSurface = Color_Ink,
    surfaceVariant = Color_LightSurface,
    onSurfaceVariant = Color_Ink,
    outline = Color_LightOutline
)

@Composable
fun NovaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) NovaDarkColorScheme else NovaLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}
