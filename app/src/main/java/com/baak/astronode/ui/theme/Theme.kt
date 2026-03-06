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
    primary = Color(0xFFE83E2E),           // Kırmızı vurgu
    onPrimary = Color(0xFF000000),         // SİYAH (kırmızı buton üstünde)
    primaryContainer = Color(0xFF3D0000),  // Koyu kırmızı konteyner
    onPrimaryContainer = Color(0xFFFF6B6B), // Açık kırmızı yazı koyu üstünde
    secondary = Color(0xFF1A3A5C),         // Koyu mavi
    onSecondary = Color(0xFFFFFFFF),       // Beyaz
    background = Color(0xFF121212),        // Koyu arka plan
    onBackground = Color(0xFFFF6B6B),      // Açık kırmızı yazı
    surface = Color(0xFF1E1E1E),          // Kart arka planı
    onSurface = Color(0xFFFF6B6B),         // Açık kırmızı yazı kart üstünde
    surfaceVariant = Color(0xFF2A2A2A),   // Hafif açık kart
    onSurfaceVariant = Color(0xFFCC8888),  // Soluk kırmızı ikincil yazı
    tertiary = Color(0xFF665522),         // Uyarı rengi
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF336633), // Başarı rengi
    onTertiaryContainer = Color(0xFFCCFFCC),
    error = Color(0xFFEF5350),
    onError = Color(0xFF000000),           // Siyah
    outline = Color(0xFF555555)
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
    tertiary = Color(0xFFF57F17),
    tertiaryContainer = Color(0xFF2E7D32),
    onTertiaryContainer = Color(0xFFE8F5E9)
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
