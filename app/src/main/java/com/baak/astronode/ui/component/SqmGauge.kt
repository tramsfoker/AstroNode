package com.baak.astronode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.baak.astronode.core.theme.Bortle1
import com.baak.astronode.core.theme.Bortle2
import com.baak.astronode.core.theme.Bortle3
import com.baak.astronode.core.theme.Bortle4
import com.baak.astronode.core.theme.Bortle5
import com.baak.astronode.core.theme.Bortle6
import com.baak.astronode.core.theme.Bortle7
import com.baak.astronode.core.theme.Bortle8
import com.baak.astronode.core.theme.Bortle9
import com.baak.astronode.core.theme.CardBackground
import com.baak.astronode.core.theme.Disabled
import com.baak.astronode.core.theme.TextPrimary
import com.baak.astronode.core.theme.TextSecondary

private val bortleColors = listOf(
    Bortle1, Bortle2, Bortle3, Bortle4, Bortle5,
    Bortle6, Bortle7, Bortle8, Bortle9
)

@Composable
fun SqmGauge(
    value: Double?,
    bortleClass: Int?,
    modifier: Modifier = Modifier
) {
    val bortleColor = bortleClass?.let { bortleColors.getOrNull(it - 1) } ?: Disabled

    Column(
        modifier = modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(CardBackground)
            .border(2.dp, bortleColor, CircleShape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value?.let { "%.2f".format(it) } ?: "—",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary
        )
        Text(
            text = "Mag/arcsec²",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        if (bortleClass != null) {
            Text(
                text = "Bortle $bortleClass",
                style = MaterialTheme.typography.labelMedium,
                color = bortleColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
