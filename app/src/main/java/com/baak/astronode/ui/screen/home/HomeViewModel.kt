package com.baak.astronode.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.data.sensor.LocationData
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.data.sensor.OrientationData
import com.baak.astronode.data.sensor.OrientationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val locationState: LocationData? = null,
    val orientationState: OrientationData? = null,
    val isOrientationEnabled: Boolean = false
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
    private val orientationProvider: OrientationProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val isSensorAvailable: StateFlow<Boolean> = MutableStateFlow(orientationProvider.isSensorAvailable).asStateFlow()

    init {
        viewModelScope.launch {
            locationProvider.locationState.collect { location ->
                _uiState.update { it.copy(locationState = location) }
            }
        }
        viewModelScope.launch {
            orientationProvider.orientationState.collect { orientation ->
                _uiState.update { it.copy(orientationState = orientation) }
            }
        }
    }

    fun startLocationUpdates() {
        locationProvider.startUpdates()
    }

    fun stopLocationUpdates() {
        locationProvider.stopUpdates()
    }

    fun setOrientationEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isOrientationEnabled = enabled) }
        if (enabled) {
            orientationProvider.startListening()
        } else {
            orientationProvider.stopListening()
        }
    }

    fun hasOrientationSensor(): Boolean = orientationProvider.hasSensor()

    override fun onCleared() {
        super.onCleared()
        locationProvider.stopUpdates()
        orientationProvider.stopListening()
    }
}
