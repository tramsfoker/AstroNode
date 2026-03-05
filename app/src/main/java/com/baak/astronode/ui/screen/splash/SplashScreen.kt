package com.baak.astronode.ui.screen.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.baak.astronode.core.theme.PrimaryAccent
import com.baak.astronode.core.theme.Surface
import com.baak.astronode.core.theme.TextSecondary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "fadeIn"
    )

    LaunchedEffect(Unit) {
        visible = true
        delay(2000)
        onNavigateToHome()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = PrimaryAccent
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "BAAK BİLİM KULÜBÜ",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = PrimaryAccent
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AstroNode — Işık Kirliliği Haritalama",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
