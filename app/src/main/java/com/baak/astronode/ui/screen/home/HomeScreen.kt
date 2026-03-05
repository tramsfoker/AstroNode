package com.baak.astronode.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baak.astronode.core.theme.CardBackground
import com.baak.astronode.core.theme.Surface
import com.baak.astronode.core.theme.TextSecondary
import com.baak.astronode.ui.component.ConnectionBadge
import com.baak.astronode.ui.component.MeasureButton
import com.baak.astronode.ui.component.OrientationDisplay
import com.baak.astronode.ui.component.SqmGauge

@Composable
fun HomeScreen() {
    var orientationEnabled by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Üst bar
        Text(
            text = "BAAK BİLİM KULÜBÜ",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // ConnectionBadge — placeholder: bağlı değil
        ConnectionBadge(
            isConnected = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        // SqmGauge — placeholder: veri yok
        SqmGauge(
            value = null,
            bortleClass = null,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        // OrientationDisplay — placeholder
        OrientationDisplay(
            azimuth = null,
            pitch = null,
            roll = null,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Yönelim Verisini Ekle toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Yönelim Verisini Ekle",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = orientationEnabled,
                onCheckedChange = { orientationEnabled = it }
            )
        }

        // Konum göstergesi — placeholder
        Text(
            text = "📍 Konum alınıyor...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // MeasureButton
        MeasureButton(
            onClick = { /* placeholder */ },
            isLoading = isLoading,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Not TextField
        BasicTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier
                .fillMaxWidth()
                .background(CardBackground)
                .padding(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            decorationBox = { innerTextField ->
                Box {
                    if (note.isEmpty()) {
                        Text(
                            text = "Not ekle... (opsiyonel)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}
