package com.baak.astronode.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.NetworkMonitor
import com.baak.astronode.data.sensor.LocationData
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.data.sensor.OrientationData
import com.baak.astronode.data.sensor.OrientationProvider
import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.UserManager
import com.baak.astronode.data.session.SessionSelectionManager
import com.baak.astronode.data.usb.SqmUsbManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.baak.astronode.data.usb.UsbConnectionState
import com.baak.astronode.domain.usecase.TakeMeasurementUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
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
    private val networkMonitor: NetworkMonitor,
    private val userManager: UserManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("astro_node_profile", android.content.Context.MODE_PRIVATE)

    private val _measurementState = MutableStateFlow(MeasurementUiState())
    private val _needsProfileSetup = MutableStateFlow(false)
    val needsProfileSetup: StateFlow<Boolean> = _needsProfileSetup.asStateFlow()
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

    val userProfile: StateFlow<com.baak.astronode.core.model.UserProfile?> = flow {
        val uid = try { firebaseAuthManager.ensureAnonymousAuth() } catch (_: Exception) { null }
        emit(uid)
    }.flatMapLatest { uid ->
        if (uid != null) userManager.getUserProfile(uid) else flowOf(null as com.baak.astronode.core.model.UserProfile?)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _orientationEnabled = MutableStateFlow(true)
    val orientationEnabled: StateFlow<Boolean> = _orientationEnabled.asStateFlow()

    private val _isTestMeasurement = MutableStateFlow(false)
    val isTestMeasurement: StateFlow<Boolean> = _isTestMeasurement.asStateFlow()

    val usbConnectionState: StateFlow<UsbConnectionState> = sqmUsbManager.connectionState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UsbConnectionState.DISCONNECTED)

    val locationState: StateFlow<LocationData?> = locationProvider.location
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val orientationState: StateFlow<OrientationData?> = orientationProvider.orientation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val connectedDriverName: String? get() = sqmUsbManager.connectedDriverName

    init {
        viewModelScope.launch {
            val setupDone = prefs.getBoolean(AppConstants.PREF_PROFILE_SETUP_DONE, false)
            if (!setupDone) {
                val uid = try { firebaseAuthManager.ensureAnonymousAuth() } catch (_: Exception) { return@launch }
                val profile = userManager.getUserProfile(uid).first()
                val displayNameBlank = profile?.displayName?.isBlank() != false
                _needsProfileSetup.value = displayNameBlank
            }
        }
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

    fun onTestMeasurementToggle(checked: Boolean) {
        _isTestMeasurement.value = checked
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
                sessionName = session?.name,
                isTest = _isTestMeasurement.value
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

    suspend fun completeProfileSetup(displayName: String) {
        val uid = firebaseAuthManager.ensureAnonymousAuth()
        userManager.ensureUserProfile(uid, displayName)
        userManager.updateDisplayName(uid, displayName).onFailure { /* Firestore güncelleme */ }
        prefs.edit().putBoolean(AppConstants.PREF_PROFILE_SETUP_DONE, true).apply()
        _needsProfileSetup.value = false
    }

    override fun onCleared() {
        super.onCleared()
        sqmUsbManager.unregisterReceiver()
        sqmUsbManager.disconnect()
        locationProvider.stopUpdates()
        orientationProvider.stopListening()
    }
}
