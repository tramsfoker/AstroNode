package com.baak.astronode.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.data.sensor.LocationData
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.data.sensor.OrientationData
import com.baak.astronode.data.sensor.OrientationProvider
import com.baak.astronode.data.usb.SqmUsbManager
import com.baak.astronode.data.usb.UsbConnectionState
import com.baak.astronode.domain.usecase.TakeMeasurementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MeasurementUiState(
    val isLoading: Boolean = false,
    val lastMeasurement: SkyMeasurement? = null,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val takeMeasurementUseCase: TakeMeasurementUseCase,
    private val sqmUsbManager: SqmUsbManager,
    private val locationProvider: LocationProvider,
    private val orientationProvider: OrientationProvider
) : ViewModel() {

    private val _measurementState = MutableStateFlow(MeasurementUiState())
    val measurementState: StateFlow<MeasurementUiState> = _measurementState.asStateFlow()

    private val _orientationEnabled = MutableStateFlow(true)
    val orientationEnabled: StateFlow<Boolean> = _orientationEnabled.asStateFlow()

    val usbConnectionState: StateFlow<UsbConnectionState> = sqmUsbManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsbConnectionState.DISCONNECTED)

    val locationState: StateFlow<LocationData?> = locationProvider.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val orientationState: StateFlow<OrientationData?> = orientationProvider.orientation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectedDriverName: String? get() = sqmUsbManager.connectedDriverName

    init {
        sqmUsbManager.registerReceiver()
        try {
            sqmUsbManager.tryConnect()
        } catch (e: Exception) {
            // USB bağlantısı başarısız, crash engelle
        }
        locationProvider.startUpdates()
        orientationProvider.startListening()
    }

    fun onOrientationToggle(enabled: Boolean) {
        _orientationEnabled.value = enabled
        if (enabled) orientationProvider.startListening()
        else orientationProvider.stopListening()
    }

    fun onMeasureClick(note: String?) {
        if (_measurementState.value.isLoading) return

        viewModelScope.launch {
            _measurementState.value = MeasurementUiState(isLoading = true)

            val result = takeMeasurementUseCase(
                orientationEnabled = _orientationEnabled.value,
                note = note
            )

            _measurementState.value = result.fold(
                onSuccess = { measurement ->
                    MeasurementUiState(lastMeasurement = measurement)
                },
                onFailure = { error ->
                    MeasurementUiState(error = error.message ?: "Bilinmeyen hata")
                }
            )
        }
    }

    fun clearError() {
        _measurementState.value = _measurementState.value.copy(error = null)
    }

    override fun onCleared() {
        super.onCleared()
        sqmUsbManager.unregisterReceiver()
        sqmUsbManager.disconnect()
        locationProvider.stopUpdates()
        orientationProvider.stopListening()
    }
}
