package com.baak.astronode.ui.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.theme.CardBackground
import com.baak.astronode.core.theme.PrimaryAccent
import com.baak.astronode.core.theme.Surface
import com.baak.astronode.core.theme.TextPrimary
import com.baak.astronode.core.theme.TextSecondary
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BURSA_LAT = 40.19
private const val BURSA_LNG = 29.06
private const val DEFAULT_ZOOM = 10f
private const val CIRCLE_RADIUS_METERS = 300.0

private fun formatInfoWindow(m: SkyMeasurement): String {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val base = "MPSAS: ${String.format("%.2f", m.sqmValue)} | Bortle: ${m.bortleClass} | ${dateFormat.format(Date(m.timestamp))}"
    return if (!m.note.isNullOrBlank()) "$base\n${m.note}" else base
}

@Composable
fun MapScreen(
    initialFocusLat: Double? = null,
    initialFocusLng: Double? = null,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val mapDataState by viewModel.mapDataState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    var selectedMeasurement by remember { mutableStateOf<SkyMeasurement?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        val (lat, lng) = when {
            initialFocusLat != null && initialFocusLng != null -> initialFocusLat to initialFocusLng
            else -> BURSA_LAT to BURSA_LNG
        }
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), DEFAULT_ZOOM)
    }

    LaunchedEffect(initialFocusLat, initialFocusLng) {
        if (initialFocusLat != null && initialFocusLng != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(initialFocusLat, initialFocusLng),
                    DEFAULT_ZOOM
                )
            )
        }
    }

    val mapProperties = remember {
        MapProperties(
            mapStyleOptions = com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                context,
                R.raw.map_style
            )
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            measurements.forEach { measurement ->
                Circle(
                    center = LatLng(measurement.latitude, measurement.longitude),
                    radius = CIRCLE_RADIUS_METERS,
                    fillColor = BortleScale.toBortleColor(measurement.bortleClass),
                    strokeColor = BortleScale.toBortleColor(measurement.bortleClass),
                    strokeWidth = 2f,
                    clickable = true,
                    onClick = { selectedMeasurement = measurement }
                )
            }
        }

        when (val state = mapDataState) {
            is MapDataState.Loading -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = PrimaryAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            is MapDataState.Error -> {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                ) {
                    Text(
                        text = state.message,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            else -> {}
        }

        var goToMyLocation by remember { mutableStateOf(0) }
        FloatingActionButton(
            onClick = { goToMyLocation++ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = PrimaryAccent,
            contentColor = TextPrimary
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = "Konumuma Git",
                modifier = Modifier.size(24.dp)
            )
        }

        LaunchedEffect(goToMyLocation) {
            if (goToMyLocation > 0) {
                userLocation?.let { loc ->
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(loc.lat, loc.lng),
                            DEFAULT_ZOOM
                        )
                    )
                }
            }
        }

        selectedMeasurement?.let { m ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 72.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                onClick = { selectedMeasurement = null }
            ) {
                Text(
                    text = formatInfoWindow(m),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
