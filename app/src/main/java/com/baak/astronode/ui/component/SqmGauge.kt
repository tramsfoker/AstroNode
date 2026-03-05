package com.baak.astronode.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.ui.theme.AstroCardBackground
import com.baak.astronode.ui.theme.AstroTextSecondary

@Composable
fun SqmGauge(
    mpsas: Double?,
    bortleClass: Int?,
    modifier: Modifier = Modifier
) {
    val displayValue = mpsas?.let { String.format("%.2f", it) } ?: "—"
    val ringColor = bortleClass?.let { BortleScale.bortleColor(it) }
        ?: AstroCardBackground
    val bortleLabel = bortleClass?.let { "Bortle $it" } ?: ""

    Box(modifier = modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
            drawArc(
                color = AstroCardBackground,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayValue,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Mag/arcsec²",
                style = MaterialTheme.typography.labelSmall,
                color = AstroTextSecondary
            )
            if (bortleLabel.isNotEmpty()) {
                Text(
                    text = bortleLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ringColor
                )
            }
        }
    }
}
