package com.baak.astronode.ui.screen.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.baak.astronode.core.theme.Surface
import com.baak.astronode.core.theme.TextSecondary

@Composable
fun HistoryScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Geçmiş ölçümler yakında...",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
    }
}
