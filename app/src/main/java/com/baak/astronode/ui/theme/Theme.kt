package com.baak.astronode.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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

private val BaakLightColorScheme = lightColorScheme(
    primary = Color(0xFFB71C1C),           // Koyu kırmızı
    onPrimary = Color(0xFFFFFFFF),         // Beyaz (buton içi yazı)
    primaryContainer = Color(0xFFFFCDD2),   // Açık kırmızı konteyner
    onPrimaryContainer = Color(0xFF4E0000),
    secondary = Color(0xFF1565C0),         // Mavi
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5F5),        // Açık gri arka plan
    onBackground = Color(0xFF212121),       // Koyu metin arka plan üstünde
    surface = Color(0xFFFFFFFF),            // Beyaz kart
    onSurface = Color(0xFF212121),         // Koyu metin kart üstünde
    surfaceVariant = Color(0xFFE8E8E8),     // Hafif gri
    onSurfaceVariant = Color(0xFF757575),   // İkincil metin
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFFBDBDBD),            // Kenarlıklar
    tertiary = LightWarning
)

@Composable
fun BaakTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) BaakDarkColorScheme else BaakLightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = if (darkTheme) {
                android.graphics.Color.parseColor("#121212")
            } else {
                android.graphics.Color.parseColor("#B71C1C")
            }
            window.navigationBarColor = colorScheme.surface.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun AstroNodeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    BaakTheme(darkTheme = darkTheme, content = content)
}
