package com.baak.astronode.ui.screen.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baak.astronode.R
import kotlinx.coroutines.delay

private val SplashBackground = Color(0xFF000000)
private val AstroNodeRed = Color(0xFFE83E2E)
private val VersionGray = Color(0xFF666666)

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit
) {
    var logoVisible by remember { mutableStateOf(false) }
    var textVisible by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0.6f,
        animationSpec = tween(durationMillis = 600, easing = EaseOutBack),
        label = "scale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (logoVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "logoAlpha"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (textVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "textAlpha"
    )

    LaunchedEffect(Unit) {
        logoVisible = true
        delay(200)
        textVisible = true
        delay(2500)
        onNavigateToHome()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.logo_splash_large_red),
            contentDescription = null,
            modifier = Modifier
                .size(160.dp)
                .scale(scale)
                .alpha(logoAlpha)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "AstroNode",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = AstroNodeRed,
            modifier = Modifier.alpha(textAlpha)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "v1.0.0",
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = VersionGray,
            modifier = Modifier.alpha(textAlpha)
        )
    }
}
