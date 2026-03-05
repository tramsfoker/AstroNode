package com.baak.astronode.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val BaakDarkColorScheme = darkColorScheme(
    primary = AstroPrimary,
    onPrimary = AstroTextPrimary,
    secondary = AstroSecondary,
    onSecondary = AstroTextPrimary,
    tertiary = AstroWarning,
    background = AstroSurface,
    surface = AstroSurface,
    surfaceVariant = AstroCardBackground,
    onBackground = AstroTextPrimary,
    onSurface = AstroTextPrimary,
    onSurfaceVariant = AstroTextSecondary,
    error = AstroError,
    onError = AstroTextPrimary
)

@Composable
fun AstroNodeTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.parseColor("#0A0A0A")
            window.navigationBarColor = android.graphics.Color.parseColor("#0A0A0A")
            val controller = WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    MaterialTheme(
        colorScheme = BaakDarkColorScheme,
        typography = Typography,
        content = content
    )
}
