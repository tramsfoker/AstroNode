package com.baak.astronode.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.model.WeatherData
import com.baak.astronode.core.util.MoonCalc
import com.baak.astronode.core.util.NetworkMonitor
import com.baak.astronode.core.util.ObservingScore
import com.baak.astronode.core.util.SunCalc
import com.baak.astronode.data.sensor.LocationData
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.data.sensor.OrientationData
import com.baak.astronode.data.sensor.OrientationProvider
import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.data.api.WeatherService
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
import com.baak.astronode.core.util.MoonData
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.util.Log
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

data class WeatherWidgetState(
    val weather: WeatherData? = null,
    val moon: MoonData = MoonCalc.getMoonPhase(),
    val observingCondition: com.baak.astronode.core.util.ObservingCondition = ObservingScore.calculate(null, MoonCalc.getMoonPhase()),
    val observingTimeStatus: com.baak.astronode.core.util.ObservingTimeStatus = SunCalc.isGoodTimeForObserving(0.0, 0.0)
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
    private val weatherService: WeatherService,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private var weatherJob: Job? = null
    private var lastWeatherFetchLat: Double? = null
    private var lastWeatherFetchLng: Double? = null
    private var lastWeatherFetchTime: Long = 0
    private companion object { const val WEATHER_DEBOUNCE_MS = 30_000L }

    /** Güneş batışı günde 1 kez hesaplanır, countdown her 60sn güncellenir */
    private var cachedSunsetData: Pair<Long, com.baak.astronode.core.util.ObservingTimeStatus>? = null

    private fun getObservingStatus(lat: Double, lng: Double): com.baak.astronode.core.util.ObservingTimeStatus {
        val today = System.currentTimeMillis() / 86400000
        val cached = cachedSunsetData
        if (cached != null && cached.first == today) {
            return cached.second.copy(detail = SunCalc.getCountdownDetail(cached.second.sunsetTime))
        }
        val status = SunCalc.isGoodTimeForObserving(lat, lng)
        cachedSunsetData = Pair(today, status)
        return status
    }

    private val _weatherWidgetState = MutableStateFlow(WeatherWidgetState())
    val weatherWidgetState: StateFlow<WeatherWidgetState> = _weatherWidgetState.asStateFlow()

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

    init {
        viewModelScope.launch {
            combine(
                locationProvider.location,
                flow { while (true) { emit(Unit); delay(60_000) } }
            ) { loc, _ -> loc }.collect { loc ->
                if (loc != null) {
                    val moon = MoonCalc.getMoonPhase()
                    val timeStatus = getObservingStatus(loc.lat, loc.lng)
                    val locChanged = lastWeatherFetchLat == null || lastWeatherFetchLng == null ||
                        kotlin.math.abs(loc.lat - lastWeatherFetchLat!!) > 0.01 ||
                        kotlin.math.abs(loc.lng - lastWeatherFetchLng!!) > 0.01
                    val debounceOk = System.currentTimeMillis() - lastWeatherFetchTime > WEATHER_DEBOUNCE_MS
                    if (locChanged && debounceOk && networkMonitor.isOnline.value) {
                        weatherJob?.cancel()
                        _weatherWidgetState.value = _weatherWidgetState.value.copy(
                            moon = moon,
                            observingTimeStatus = timeStatus
                        )
                        weatherJob = viewModelScope.launch {
                            delay(500)
                            val weather = weatherService.getWeatherData(loc.lat, loc.lng)
                            lastWeatherFetchLat = loc.lat
                            lastWeatherFetchLng = loc.lng
                            lastWeatherFetchTime = System.currentTimeMillis()
                            val observing = ObservingScore.calculate(weather, moon)
                            _weatherWidgetState.value = WeatherWidgetState(
                                weather = weather,
                                moon = moon,
                                observingCondition = observing,
                                observingTimeStatus = getObservingStatus(loc.lat, loc.lng)
                            )
                        }
                    } else {
                        val weather = _weatherWidgetState.value.weather
                        val observing = ObservingScore.calculate(weather, moon)
                        _weatherWidgetState.value = _weatherWidgetState.value.copy(
                            moon = moon,
                            observingCondition = observing,
                            observingTimeStatus = timeStatus
                        )
                    }
                }
            }
        }
    }

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

    private val _pendingDaytimeMeasurement = MutableStateFlow<String?>(null)
    val pendingDaytimeMeasurement: StateFlow<String?> = _pendingDaytimeMeasurement.asStateFlow()

    fun onMeasureClick(note: String?, isTest: Boolean = false) {
        android.util.Log.d("BUTON", "=== ViewModel'a ulaştı ===")
        _measurementState.value = MeasurementUiState(isLoading = true)
        performMeasurement(note, isTest = isTest, forceDaytimeTest = false)
    }

    fun requestDaytimeMeasurement(note: String?) {
        _pendingDaytimeMeasurement.value = note
    }

    fun onConfirmDaytimeTest(note: String?) {
        _pendingDaytimeMeasurement.value = null
        performMeasurement(note, isTest = true, forceDaytimeTest = true)
    }

    fun onDismissDaytimeWarning() {
        _pendingDaytimeMeasurement.value = null
    }

    private fun performMeasurement(note: String?, isTest: Boolean, forceDaytimeTest: Boolean) {
        Log.d("HOME", "performMeasurement başladı, isTest=$isTest, forceDaytimeTest=$forceDaytimeTest")
        viewModelScope.launch {
            val session = sessionSelectionManager.selectedSession.value
            val result = takeMeasurementUseCase(
                orientationEnabled = _orientationEnabled.value,
                note = note?.takeIf { it.isNotBlank() },
                sessionId = session?.id,
                sessionName = session?.name,
                isTest = isTest,
                forceDaytimeTest = forceDaytimeTest
            )

            _measurementState.value = result.fold(
                onSuccess = { measurement ->
                    Log.d("HOME", "Ölçüm başarılı: ${measurement.sqmValue}")
                    MeasurementUiState(lastMeasurement = measurement)
                },
                onFailure = { error ->
                    Log.e("HOME", "Ölçüm hatası: ${error.message}")
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
