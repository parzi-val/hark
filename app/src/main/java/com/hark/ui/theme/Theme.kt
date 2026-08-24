package com.hark.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalHarkColors = staticCompositionLocalOf { LightHarkColors }

/** Access Hark's semantic palette from any composable: `Hark.colors.rust`. */
object Hark {
    val colors: HarkColors
        @Composable @ReadOnlyComposable
        get() = LocalHarkColors.current
}

@Composable
fun HarkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkHarkColors else LightHarkColors
    // Map the Hark palette onto Material's scheme so stock M3 surfaces inherit it too.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.rust, onPrimary = colors.paper,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.paper, onSurface = colors.ink,
        )
    } else {
        lightColorScheme(
            primary = colors.rust, onPrimary = colors.paper,
            background = colors.paper, onBackground = colors.ink,
            surface = colors.paper, onSurface = colors.ink,
        )
    }
    CompositionLocalProvider(LocalHarkColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
    }
}
