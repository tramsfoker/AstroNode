package com.baak.astronode.ui.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.NetworkMonitor
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.sensor.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Türkiye merkezi (zoom < 6 için) */
private const val TURKEY_CENTER_LAT = 39.9
private const val TURKEY_CENTER_LNG = 32.8
private const val TURKEY_RADIUS_KM = 600.0
private const val CAMERA_DEBOUNCE_MS = 500L

sealed class MapDataState {
    data object Loading : MapDataState()
    data class Success(val measurements: List<SkyMeasurement>) : MapDataState()
    data class Error(val message: String) : MapDataState()
}

data class MapRegion(
    val centerLat: Double,
    val centerLng: Double,
    val zoom: Float
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val firestoreManager: FirestoreManager,
    private val locationProvider: LocationProvider,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val isOnline = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _mapDataState = MutableStateFlow<MapDataState>(MapDataState.Loading)
    val mapDataState: StateFlow<MapDataState> = _mapDataState.asStateFlow()

    private val _measurements = MutableStateFlow<List<SkyMeasurement>>(emptyList())
    private val _showTestMeasurements = MutableStateFlow(false)
    val showTestMeasurements: StateFlow<Boolean> = _showTestMeasurements.asStateFlow()

    val measurements: StateFlow<List<SkyMeasurement>> = combine(
        _measurements,
        _showTestMeasurements
    ) { list, showTest ->
        if (showTest) list else list.filter { !it.isTest }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _mapRegionUpdates = MutableSharedFlow<MapRegion>(replay = 0)
    private var fetchJob: Job? = null

    val userLocation: StateFlow<com.baak.astronode.data.sensor.LocationData?> =
        locationProvider.location

    init {
        _mapRegionUpdates
            .debounce(CAMERA_DEBOUNCE_MS)
            .onEach { region ->
                fetchMeasurementsForRegion(region)
            }
            .launchIn(viewModelScope)

        // İlk yüklemede Türkiye genelini çek
        viewModelScope.launch {
            _mapRegionUpdates.emit(
                MapRegion(TURKEY_CENTER_LAT, TURKEY_CENTER_LNG, 6f)
            )
        }
    }

    fun onShowTestMeasurementsToggle(show: Boolean) {
        _showTestMeasurements.value = show
    }

    /** Harita kamera hareket ettiğinde çağrılır (debounce 500ms) */
    fun onCameraPositionChanged(centerLat: Double, centerLng: Double, zoom: Float) {
        viewModelScope.launch {
            _mapRegionUpdates.emit(MapRegion(centerLat, centerLng, zoom))
        }
    }

    private fun fetchMeasurementsForRegion(region: MapRegion) {
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _mapDataState.value = MapDataState.Loading
            val radiusKm = if (region.zoom < 6) {
                TURKEY_RADIUS_KM
            } else {
                (20000.0 / (1 shl region.zoom.toInt().coerceIn(0, 20))).coerceAtLeast(5.0)
            }
            firestoreManager.getMeasurementsInRadius(
                centerLat = region.centerLat,
                centerLng = region.centerLng,
                radiusKm = radiusKm
            )
                .catch { e ->
                    _mapDataState.value = MapDataState.Error(e.message ?: "Veri yüklenemedi")
                }
                .collect { list ->
                    _measurements.value = list
                    _mapDataState.value = MapDataState.Success(list)
                }
        }
    }
}
