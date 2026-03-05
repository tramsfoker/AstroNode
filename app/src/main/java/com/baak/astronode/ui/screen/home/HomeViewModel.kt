package com.baak.astronode.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.NetworkMonitor
import com.baak.astronode.data.sensor.LocationData
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.data.sensor.OrientationData
import com.baak.astronode.data.sensor.OrientationProvider
import com.baak.astronode.data.session.SessionSelectionManager
import com.baak.astronode.data.usb.SqmUsbManager
import com.baak.astronode.data.usb.UsbConnectionState
import com.baak.astronode.domain.usecase.TakeMeasurementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Bağlantı banner durumu: Online (gizle), Offline, Offline+Pending, Synced */
sealed class ConnectionBannerState {
    data object Online : ConnectionBannerState()
    data class Offline(val pendingCount: Int) : ConnectionBannerState()
    data object Synced : ConnectionBannerState()
}

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
    private val orientationProvider: OrientationProvider,
    private val sessionSelectionManager: SessionSelectionManager,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _measurementState = MutableStateFlow(MeasurementUiState())
    private val _showSyncedBanner = MutableStateFlow(false)

    val connectionBannerState: StateFlow<ConnectionBannerState> = combine(
        networkMonitor.isOnline,
        networkMonitor.pendingWriteCount,
        _showSyncedBanner
    ) { isOnline, pendingCount, showSynced ->
        when {
            showSynced -> ConnectionBannerState.Synced
            isOnline -> ConnectionBannerState.Online
            else -> ConnectionBannerState.Offline(pendingCount)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionBannerState.Online)

    init {
        viewModelScope.launch {
            var wasOffline = false
            networkMonitor.isOnline.collect { isOnline ->
                    if (isOnline && wasOffline) {
                        _showSyncedBanner.value = true
                        delay(2000)
                        _showSyncedBanner.value = false
                        wasOffline = false
                    }
                    if (!isOnline) wasOffline = true
                }
        }
    }

    val selectedSession = sessionSelectionManager.selectedSession
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

            val session = sessionSelectionManager.selectedSession.value
            val result = takeMeasurementUseCase(
                orientationEnabled = _orientationEnabled.value,
                note = note,
                sessionId = session?.id,
                sessionName = session?.name
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
