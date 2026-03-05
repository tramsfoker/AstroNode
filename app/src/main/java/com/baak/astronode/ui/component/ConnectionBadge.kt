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
import com.baak.astronode.ui.theme.AstroCardBackground
import com.baak.astronode.ui.theme.AstroDisabled
import com.baak.astronode.ui.theme.AstroError
import com.baak.astronode.ui.theme.AstroSuccess
import com.baak.astronode.ui.theme.AstroWarning

@Composable
fun ConnectionBadge(
    state: UsbConnectionState,
    driverName: String?,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (state) {
        UsbConnectionState.CONNECTED -> AstroSuccess to "USB: Bağlı${driverName?.let { " ($it)" } ?: ""}"
        UsbConnectionState.PERMISSION_PENDING -> AstroWarning to "USB: İzin bekleniyor"
        UsbConnectionState.ERROR -> AstroError to "USB: Hata"
        UsbConnectionState.DISCONNECTED -> AstroDisabled to "USB: Bağlı Değil"
    }

    Row(
        modifier = modifier
            .background(AstroCardBackground, RoundedCornerShape(8.dp))
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
