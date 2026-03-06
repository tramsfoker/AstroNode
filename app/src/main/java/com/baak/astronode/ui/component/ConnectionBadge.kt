package com.baak.astronode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.baak.astronode.data.usb.UsbConnectionState

@Composable
fun ConnectionBadge(
    state: UsbConnectionState,
    driverName: String?,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val successColor = colorScheme.tertiaryContainer
    val warningColor = colorScheme.tertiary
    val errorColor = colorScheme.error

    val (dotColor, label) = when (state) {
        UsbConnectionState.CONNECTED -> successColor to "USB: Bağlı${driverName?.let { " ($it)" } ?: ""}"
        UsbConnectionState.PERMISSION_PENDING -> warningColor to "USB: İzin bekleniyor"
        UsbConnectionState.ERROR -> errorColor to "USB: Hata"
        UsbConnectionState.DISCONNECTED -> colorScheme.outline to "USB: Bağlı Değil"
    }

    Row(
        modifier = modifier
            .background(colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
