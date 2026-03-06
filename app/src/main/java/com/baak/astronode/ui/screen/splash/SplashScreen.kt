package com.baak.astronode.ui.screen.splash

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baak.astronode.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
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
        if (FirebaseAuth.getInstance().currentUser == null) {
            try {
                FirebaseAuth.getInstance().signInAnonymously().await()
            } catch (_: Exception) { }
        }
        logoVisible = true
        delay(200)
        textVisible = true
        delay(2500)
        onNavigateToHome()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
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
            text = "BAAK BİLİM KULÜBÜ",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(textAlpha)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AstroNode",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = colorScheme.primary,
            modifier = Modifier.alpha(textAlpha)
        )
    }
}
