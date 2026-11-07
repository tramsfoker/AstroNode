package com.baak.astronode.ui.screen.discover

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.R
import com.baak.astronode.core.model.NewsItem
import com.baak.astronode.core.model.RegionStat
import com.baak.astronode.core.model.SkyCell
import com.baak.astronode.core.util.AstroCalendar
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.core.util.NetworkMonitor
import com.baak.astronode.core.util.TonightScore
import com.baak.astronode.data.api.WeatherService
import com.baak.astronode.data.content.ContentRepository
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.sensor.LocationProvider
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class RegionSort { DARKEST, BRIGHTEST, MOST }

data class TonightUi(
    val result: TonightScore.Result,
    val forecastAvailable: Boolean
)

data class DarkSpot(val cell: SkyCell, val distanceKm: Double)

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val content: ContentRepository,
    private val weatherService: WeatherService,
    private val locationProvider: LocationProvider,
    private val authManager: FirebaseAuthManager,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val hasLocationPermission: StateFlow<Boolean> = locationProvider.hasPermission
    fun onLocationPermissionResult() = locationProvider.onPermissionResult()

    // ---------- Haberler: önce sabitlenmiş duyurular ----------
    val news: StateFlow<List<NewsItem>> = authManager.uid
        .flatMapLatest { if (it != null) content.getNews() else flowOf(emptyList()) }
        .map { list -> list.sortedWith(compareByDescending<NewsItem> { it.pinned }.thenByDescending { it.publishedAt }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---------- Takvim (çevrimdışı) ----------
    val moonPhases: List<AstroCalendar.MoonEvent> = AstroCalendar.nextMoonPhases(System.currentTimeMillis(), 6)
    val showers: List<AstroCalendar.ShowerOccurrence> = AstroCalendar.upcomingShowers(System.currentTimeMillis(), 120)

    // ---------- Bu gece ----------
    private val _tonight = MutableStateFlow<TonightUi?>(null)
    val tonight: StateFlow<TonightUi?> = _tonight.asStateFlow()

    private val _darkSpots = MutableStateFlow<List<DarkSpot>?>(null)
    val darkSpots: StateFlow<List<DarkSpot>?> = _darkSpots.asStateFlow()

    // ---------- Bölgeler ----------
    private val _sort = MutableStateFlow(RegionSort.DARKEST)
    val sort: StateFlow<RegionSort> = _sort.asStateFlow()

    private val _selectedProvince = MutableStateFlow<String?>(null)
    val selectedProvince: StateFlow<String?> = _selectedProvince.asStateFlow()

    private val provincesRaw: StateFlow<List<RegionStat>> = authManager.uid
        .flatMapLatest { if (it != null) content.getProvinceStats() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val provinces: StateFlow<List<RegionStat>> = combine(provincesRaw, _sort) { list, s -> sortRegions(list, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val districts: StateFlow<List<RegionStat>> = combine(
        _selectedProvince.flatMapLatest { p -> if (p != null) content.getDistrictStats(p) else flowOf(emptyList()) },
        _sort
    ) { list, s -> sortRegions(list, s) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _message = MutableStateFlow<Int?>(null)
    @get:StringRes val message: StateFlow<Int?> = _message.asStateFlow()

    init {
        locationProvider.startUpdates()
        viewModelScope.launch {
            // Konum ~2 km değişince yeniden hesapla
            locationProvider.location.filterNotNull()
                .map { (it.lat * 50).toInt() to (it.lng * 50).toInt() }
                .distinctUntilChanged()
                .collect { refreshForLocation() }
        }
        viewModelScope.launch {
            // Konum yoksa bile Ay bilgisini göster
            if (_tonight.value == null) _tonight.value = TonightUi(TonightScore.calculate(null, tonightMoon()), false)
        }
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online -> if (online) refreshForLocation() }
        }
    }

    private fun tonightMoon(): Double {
        val c = Calendar.getInstance().apply {
            if (get(Calendar.HOUR_OF_DAY) >= 4) set(Calendar.HOUR_OF_DAY, 23) else set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
        }
        return AstroCalendar.moonIlluminationAt(c.timeInMillis)
    }

    fun refreshForLocation() {
        val loc = locationProvider.location.value ?: return
        viewModelScope.launch {
            if (authManager.uidOrNull() == null) return@launch
            val cells = content.getNearbyCells(loc.lat, loc.lng)
            val center = GeoLocation(loc.lat, loc.lng)
            val spots = cells.map { DarkSpot(it, GeoFireUtils.getDistanceBetween(GeoLocation(it.centerLat, it.centerLng), center) / 1000) }
            _darkSpots.value = spots.take(MAX_DARK_SPOTS)
            val localBortle = spots.minByOrNull { it.distanceKm }?.takeIf { it.distanceKm < 10 }
                ?.let { BortleScale.toBortleClass(it.cell.average) }
            val cloud = weatherService.getTonightCloudCover(loc.lat, loc.lng)
            _tonight.value = TonightUi(TonightScore.calculate(cloud, tonightMoon(), localBortle), cloud != null)
        }
    }

    fun setSort(s: RegionSort) { _sort.value = s }
    fun selectProvince(name: String?) { _selectedProvince.value = name }

    fun report(targetType: String, targetId: String, reason: String, details: String?) {
        viewModelScope.launch {
            val uid = authManager.uidOrNull() ?: run { _message.value = R.string.report_failed; return@launch }
            content.submitReport(uid, targetType, targetId, reason, details)
                .onSuccess { _message.value = R.string.report_sent }
                .onFailure { _message.value = R.string.report_failed }
        }
    }

    fun clearMessage() { _message.value = null }

    override fun onCleared() {
        locationProvider.stopUpdates()
        super.onCleared()
    }

    companion object {
        private const val MAX_DARK_SPOTS = 8
        /** Sıralamada en az bu kadar ölçümü olan bölgeler üste çıkar (tek ölçümle "en karanlık il" olmasın) */
        const val MIN_COUNT_FOR_RANKING = 3

        fun sortRegions(list: List<RegionStat>, s: RegionSort): List<RegionStat> {
            val (reliable, few) = list.partition { it.count >= MIN_COUNT_FOR_RANKING }
            val cmp: Comparator<RegionStat> = when (s) {
                RegionSort.DARKEST -> compareByDescending { it.average ?: 0.0 }
                RegionSort.BRIGHTEST -> compareBy { it.average ?: 99.0 }
                RegionSort.MOST -> compareByDescending { it.count }
            }
            return reliable.sortedWith(cmp) + few.sortedWith(cmp)
        }
    }
}
