package com.baak.astronode.core.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// SADECE dark theme — light theme YAZILMADI (MASTERPLAN Bölüm 4.1)
private val BaakDarkColorScheme = darkColorScheme(
    primary = PrimaryAccent,
    onPrimary = Surface,
    primaryContainer = PrimaryAccent,
    onPrimaryContainer = Surface,
    secondary = SecondaryAccent,
    onSecondary = TextPrimary,
    secondaryContainer = SecondaryAccent,
    onSecondaryContainer = TextPrimary,
    tertiary = SecondaryAccent,
    onTertiary = TextPrimary,
    error = Error,
    onError = Surface,
    errorContainer = Error,
    onErrorContainer = Surface,
    background = Surface,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = CardBackground,
    onSurfaceVariant = TextSecondary,
    outline = Disabled,
    outlineVariant = Disabled
)

@Composable
fun BaakDarkTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = BaakDarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Surface.toArgb()
            window.navigationBarColor = Surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BaakTypography,
        content = content
    )
}
