package com.baak.astronode.ui.screen.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.sensor.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class MapDataState {
    data object Loading : MapDataState()
    data class Success(val measurements: List<SkyMeasurement>) : MapDataState()
    data class Error(val message: String) : MapDataState()
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val firestoreManager: FirestoreManager,
    private val locationProvider: LocationProvider
) : ViewModel() {

    private val _mapDataState = MutableStateFlow<MapDataState>(MapDataState.Loading)
    val mapDataState: StateFlow<MapDataState> = _mapDataState.asStateFlow()

    val measurements: StateFlow<List<SkyMeasurement>> = firestoreManager
        .getMeasurements()
        .catch { e ->
            _mapDataState.value = MapDataState.Error(e.message ?: "Veri yüklenemedi")
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userLocation: StateFlow<com.baak.astronode.data.sensor.LocationData?> =
        locationProvider.location

    init {
        viewModelScope.launch {
            measurements.collect { list ->
                if (_mapDataState.value is MapDataState.Loading || _mapDataState.value is MapDataState.Success) {
                    _mapDataState.value = MapDataState.Success(list)
                }
            }
        }
    }
}
