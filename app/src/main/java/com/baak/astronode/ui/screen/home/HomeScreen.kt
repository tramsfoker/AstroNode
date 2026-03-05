package com.baak.astronode.ui.screen.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.baak.astronode.core.theme.CardBackground
import com.baak.astronode.core.theme.Surface
import com.baak.astronode.core.theme.TextSecondary
import com.baak.astronode.ui.component.ConnectionBadge
import com.baak.astronode.ui.component.MeasureButton
import com.baak.astronode.ui.component.OrientationDisplay
import com.baak.astronode.ui.component.SqmGauge

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSensorAvailable by viewModel.isSensorAvailable.collectAsState()
    var note by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showPermissionDeniedDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            viewModel.startLocationUpdates()
        } else {
            showPermissionDeniedDialog = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(uiState.isOrientationEnabled, isSensorAvailable) {
        if (uiState.isOrientationEnabled && !isSensorAvailable) {
            viewModel.setOrientationEnabled(false)
        }
    }

    if (showPermissionDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDeniedDialog = false },
            title = { Text("Konum İzni Gerekli") },
            text = {
                Text(
                    "Ölçüm noktalarını haritada gösterebilmek için konum iznine ihtiyacımız var. " +
                            "Lütfen ayarlardan konum iznini verin."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionDeniedDialog = false }) {
                    Text("Tamam")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "BAAK BİLİM KULÜBÜ",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        ConnectionBadge(
            isConnected = false,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )

        SqmGauge(
            value = null,
            bortleClass = null,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        if (isSensorAvailable) {
            val orientation = uiState.orientationState
            OrientationDisplay(
                azimuth = if (uiState.isOrientationEnabled) orientation?.azimuth else null,
                pitch = if (uiState.isOrientationEnabled) orientation?.pitch else null,
                roll = if (uiState.isOrientationEnabled) orientation?.roll else null,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

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
            Spacer(modifier = Modifier.fillMaxWidth())
            Switch(
                checked = uiState.isOrientationEnabled,
                onCheckedChange = { viewModel.setOrientationEnabled(it) },
                enabled = isSensorAvailable
            )
        }

        if (!isSensorAvailable) {
            Text(
                text = "Bu cihazda yönelim sensörü bulunmuyor",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val location = uiState.locationState
        val locationText = when {
            location != null -> "📍 %.5f, %.5f (h: %.0fm)".format(
                location.lat,
                location.lng,
                location.altitude
            )
            else -> "📍 Konum alınıyor..."
        }
        Text(
            text = locationText,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        MeasureButton(
            onClick = { /* placeholder */ },
            isLoading = isLoading,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
