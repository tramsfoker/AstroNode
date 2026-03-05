package com.baak.astronode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.baak.astronode.core.theme.Error
import com.baak.astronode.core.theme.Success

@Composable
fun ConnectionBadge(
    isConnected: Boolean,
    deviceName: String? = null,
    modifier: Modifier = Modifier
) {
    val color = if (isConnected) Success else Error
    val text = if (isConnected) {
        "USB: Bağlı" + (deviceName?.let { " ($it)" } ?: "")
    } else {
        "USB: Bağlı Değil"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
