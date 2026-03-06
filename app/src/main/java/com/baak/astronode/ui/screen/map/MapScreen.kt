package com.baak.astronode.ui.screen.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.rememberCameraPositionState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val BURSA_LAT = 40.19
private const val BURSA_LNG = 29.06
private const val DEFAULT_ZOOM = 10f

private fun formatInfoWindow(m: SkyMeasurement): String {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    val base = "MPSAS: ${String.format("%.2f", m.sqmValue)} | Bortle: ${m.bortleClass} | ${dateFormat.format(Date(m.timestamp))}"
    return if (!m.note.isNullOrBlank()) "$base\n${m.note}" else base
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(
    initialFocusLat: Double? = null,
    initialFocusLng: Double? = null,
    viewModel: MapViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val measurements by viewModel.measurements.collectAsStateWithLifecycle()
    val showTestMeasurements by viewModel.showTestMeasurements.collectAsStateWithLifecycle()
    val mapDataState by viewModel.mapDataState.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    var showOfflineBanner by remember { mutableStateOf(false) }

    LaunchedEffect(isOnline, mapDataState) {
        if (isOnline || mapDataState is MapDataState.Success) {
            showOfflineBanner = false
        } else if (!isOnline) {
            kotlinx.coroutines.delay(3000)
            if (!isOnline) showOfflineBanner = true
        }
    }
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val mapError by viewModel.error.collectAsStateWithLifecycle()
    var selectedMeasurement by remember { mutableStateOf<SkyMeasurement?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mapError) {
        mapError?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

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

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.position }
            .collect { position ->
                viewModel.onCameraPositionChanged(
                    position.target.latitude,
                    position.target.longitude,
                    position.zoom
                )
            }
    }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.2f
    val mapProperties = remember(isDarkTheme) {
        MapProperties(
            mapStyleOptions = if (isDarkTheme) {
                com.google.android.gms.maps.model.MapStyleOptions.loadRawResourceStyle(
                    context,
                    R.raw.map_style
                )
            } else null,  // Light temada varsayılan harita stili
            isMyLocationEnabled = false  // Varsayılan mavi noktayı KAPAT (cluster ile çakışmasın)
        )
    }

    val mapUiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            mapToolbarEnabled = false,  // Beyaz toolbar panelini kapatır
            myLocationButtonEnabled = false,
            compassEnabled = true,
            rotationGesturesEnabled = true,
            scrollGesturesEnabled = true,
            tiltGesturesEnabled = true,
            zoomGesturesEnabled = true
        )
    }

    var clusterManager by remember { mutableStateOf<ClusterManager<MeasurementClusterItem>?>(null) }

    val colorScheme = MaterialTheme.colorScheme
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .background(colorScheme.surface)
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = mapProperties,
            uiSettings = mapUiSettings
        ) {
            MapEffect(measurements) { googleMap ->
                val manager = clusterManager
                if (manager == null) {
                    val newManager = ClusterManager<MeasurementClusterItem>(context, googleMap).apply {
                        setRenderer(BortleClusterRenderer(context, googleMap, this))
                        setOnClusterItemClickListener { item ->
                            selectedMeasurement = item.measurement
                            true
                        }
                    }
                    googleMap.setOnCameraIdleListener(newManager)
                    googleMap.setOnMarkerClickListener(newManager)
                    clusterManager = newManager
                    newManager.clearItems()
                    newManager.addItems(measurements.map { MeasurementClusterItem(it) })
                    newManager.cluster()
                } else {
                    manager.clearItems()
                    manager.addItems(measurements.map { MeasurementClusterItem(it) })
                    manager.cluster()
                }
            }
        }

        // Test ölçümlerini göster toggle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Test ölçümlerini göster",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Switch(
                        checked = showTestMeasurements,
                        onCheckedChange = { viewModel.onShowTestMeasurementsToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.primary,
                            checkedTrackColor = colorScheme.surfaceVariant,
                            uncheckedThumbColor = colorScheme.outline,
                            uncheckedTrackColor = colorScheme.surface
                        )
                    )
                }
            }
        }

        // Offline harita uyarısı — sadece gerçekten offline ise (3 sn delay + harita yüklendiyse gizle)
        if (showOfflineBanner) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "Harita çevrimdışı kullanılamıyor",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(12.dp)
                    )
                }
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
                        color = colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            is MapDataState.Error -> { /* Snackbar ile gösteriliyor */ }
            else -> {}
        }

        var goToMyLocation by remember { mutableStateOf(0) }
        FloatingActionButton(
            onClick = { goToMyLocation++ },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = colorScheme.primary,
            contentColor = colorScheme.onPrimary
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
                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                onClick = { selectedMeasurement = null }
            ) {
                Text(
                    text = formatInfoWindow(m),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
    }
}
