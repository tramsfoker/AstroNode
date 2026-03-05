package com.baak.astronode.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baak.astronode.data.sensor.OrientationData

@Composable
fun OrientationDisplay(
    orientation: OrientationData?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OrientationValue("Az", orientation?.azimuth, "°")
        OrientationValue("Pitch", orientation?.pitch, "°")
        OrientationValue("Roll", orientation?.roll, "°")
    }
}

@Composable
private fun OrientationValue(label: String, value: Float?, unit: String) {
    val display = value?.let { String.format("%.0f%s", it, unit) } ?: "—"
    Text(
        text = "$label: $display",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
