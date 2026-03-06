package com.baak.astronode.ui.screen.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.MoonCalc
import com.baak.astronode.core.util.ObservingScore
import com.baak.astronode.data.api.WeatherService
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.domain.usecase.ExportDataUseCase
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.core.util.NetworkMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimeSeriesPoint(
    val timestamp: Long,
    val mpsas: Double,
    val bortleClass: Int
)

data class SessionStat(
    val sessionId: String,
    val sessionName: String,
    val measurementCount: Int,
    val participantCount: Int?,
    val avgMpsas: Double,
    val bestMpsas: Double,
    val worstMpsas: Double,
    val date: Long,
    val avgWeather: com.baak.astronode.core.model.WeatherData? = null,
    val avgMoonPhase: String? = null,
    val avgMoonEmoji: String? = null,
    val avgMoonIllumination: Int? = null,
    val avgObservingScore: Int? = null,
    val avgObservingRating: String? = null
)

enum class TimeRange(val label: String) {
    LAST_7_DAYS("Son 7 Gün"),
    LAST_30_DAYS("Son 30 Gün"),
    ALL_TIME("Tümü")
}

data class AnalysisState(
    val totalMeasurements: Int = 0,
    val uniqueDays: Int = 0,
    val averageMpsas: Double = 0.0,
    val bestMpsas: Double = 0.0,
    val worstMpsas: Double = 0.0,
    val bestBortleClass: Int = 1,
    val worstBortleClass: Int = 9,
    val bortleDistribution: Map<Int, Int> = emptyMap(),
    val bortleGrouped: Map<String, Int> = emptyMap(),
    val timeSeriesData: List<TimeSeriesPoint> = emptyList(),
    val sessionStats: List<SessionStat> = emptyList(),
    val filteredMeasurements: List<SkyMeasurement> = emptyList(),
    val selectedTimeRange: TimeRange = TimeRange.LAST_30_DAYS,
    val selectedSessionId: String? = null,
    val excludeDaytimeMeasurements: Boolean = true
)

data class ObservingQualityState(
    val weather: com.baak.astronode.core.model.WeatherData? = null,
    val moon: com.baak.astronode.core.util.MoonData = MoonCalc.getMoonPhase(),
    val condition: com.baak.astronode.core.util.ObservingCondition = ObservingScore.calculate(null, MoonCalc.getMoonPhase())
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val firestoreManager: FirestoreManager,
    private val weatherService: WeatherService,
    private val locationProvider: LocationProvider,
    private val networkMonitor: NetworkMonitor,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    private val _observingQuality = MutableStateFlow(ObservingQualityState())
    val observingQuality: StateFlow<ObservingQualityState> = _observingQuality.asStateFlow()

    init {
        viewModelScope.launch {
            locationProvider.location.collect { loc ->
                val moon = MoonCalc.getMoonPhase()
                val weather = if (loc != null && networkMonitor.isOnline.value) {
                    weatherService.getWeatherData(loc.lat, loc.lng)
                } else null
                val condition = ObservingScore.calculate(weather, moon)
                _observingQuality.value = ObservingQualityState(
                    weather = weather,
                    moon = moon,
                    condition = condition
                )
            }
        }
    }

    val activeSessions: StateFlow<List<Session>> = kotlinx.coroutines.flow.flow {
        val uid = try { firebaseAuthManager.ensureAnonymousAuth() } catch (_: Exception) { "" }
        emit(uid)
    }.flatMapLatest { uid ->
        combine(
            firestoreManager.getActiveSessions(),
            if (uid.isNotBlank()) firestoreManager.getMyActiveSessions(uid) else flowOf(emptyList())
        ) { public, my -> (public + my).distinctBy { it.id } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val measurements: StateFlow<List<SkyMeasurement>> = firestoreManager
        .getMeasurements()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedTimeRange = MutableStateFlow(TimeRange.LAST_30_DAYS)
    private val _selectedSessionId = MutableStateFlow<String?>(null)
    private val _excludeDaytime = MutableStateFlow(true)

    val analysisState: StateFlow<AnalysisState> = combine(
        measurements,
        _selectedTimeRange,
        _selectedSessionId,
        _excludeDaytime,
        activeSessions
    ) { all, timeRange, sessionId, excludeDaytime, sessions ->
        val filtered = applyFilters(all, timeRange, sessionId, excludeDaytime)
        computeAnalysisState(filtered, timeRange, sessionId, excludeDaytime, sessions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AnalysisState()
    )

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
    }

    fun setSessionFilter(sessionId: String?) {
        _selectedSessionId.value = sessionId
    }

    fun setExcludeDaytime(exclude: Boolean) {
        _excludeDaytime.value = exclude
    }

    suspend fun exportCsv(measurements: List<SkyMeasurement>): android.net.Uri? {
        return exportDataUseCase.exportToCsv(measurements.ifEmpty { null })
    }

    private fun applyFilters(
        measurements: List<SkyMeasurement>,
        timeRange: TimeRange,
        sessionId: String?,
        excludeDaytime: Boolean
    ): List<SkyMeasurement> {
        var result = measurements

        val now = System.currentTimeMillis()
        val cutoff = when (timeRange) {
            TimeRange.LAST_7_DAYS -> now - 7 * 24 * 60 * 60 * 1000L
            TimeRange.LAST_30_DAYS -> now - 30 * 24 * 60 * 60 * 1000L
            TimeRange.ALL_TIME -> 0L
        }
        result = result.filter { it.timestamp >= cutoff }

        if (sessionId != null) {
            result = result.filter { it.sessionId == sessionId }
        }

        if (excludeDaytime) {
            result = result.filter { !it.isDaytime }
        }

        return result.sortedBy { it.timestamp }
    }

    private fun computeAnalysisState(
        filtered: List<SkyMeasurement>,
        timeRange: TimeRange,
        sessionId: String?,
        excludeDaytime: Boolean,
        sessions: List<Session>
    ): AnalysisState {
        if (filtered.isEmpty()) {
            return AnalysisState(
                selectedTimeRange = timeRange,
                selectedSessionId = sessionId,
                excludeDaytimeMeasurements = excludeDaytime,
                filteredMeasurements = filtered
            )
        }

        val totalMeasurements = filtered.size
        val uniqueDays = filtered.map { it.timestamp / (24 * 60 * 60 * 1000) }.toSet().size
        val mpsasValues = filtered.map { it.sqmValue }
        val averageMpsas = mpsasValues.average()
        val bestMpsas = mpsasValues.maxOrNull() ?: 0.0   // Yüksek MPSAS = karanlık = iyi
        val worstMpsas = mpsasValues.minOrNull() ?: 0.0 // Düşük MPSAS = aydınlık = kötü

        val bortleValues = filtered.map { it.bortleClass }
        val bestBortleClass = bortleValues.minOrNull() ?: 1   // Bortle 1 = en iyi
        val worstBortleClass = bortleValues.maxOrNull() ?: 9  // Bortle 9 = en kötü

        val bortleDistribution = filtered.groupingBy { it.bortleClass }.eachCount().toSortedMap()

        val bortleGrouped = mapOf(
            "B1-3" to filtered.count { it.bortleClass in 1..3 },
            "B4-6" to filtered.count { it.bortleClass in 4..6 },
            "B7-9" to filtered.count { it.bortleClass in 7..9 }
        )

        val timeSeriesData = filtered.map { m ->
            TimeSeriesPoint(
                timestamp = m.timestamp,
                mpsas = m.sqmValue,
                bortleClass = m.bortleClass
            )
        }

        val sessionStats = filtered
            .filter { it.sessionId != null && it.sessionId!!.isNotBlank() }
            .groupBy { it.sessionId!! }
            .map { (sid, list) ->
                val first = list.first()
                val session = sessions.find { it.id == sid }
                val weathers = list.mapNotNull { it.weather }.filter { it.temperature != null || it.cloudCover != null }
                val avgWeather = if (weathers.isNotEmpty()) {
                    val temps = weathers.mapNotNull { it.temperature }
                    val hums = weathers.mapNotNull { it.humidity }
                    val clouds = weathers.mapNotNull { it.cloudCover }
                    val winds = weathers.mapNotNull { it.windSpeed }
                    val vis = weathers.mapNotNull { it.visibility }
                    com.baak.astronode.core.model.WeatherData(
                        temperature = temps.average().takeIf { temps.isNotEmpty() },
                        humidity = hums.average().toInt().takeIf { hums.isNotEmpty() },
                        cloudCover = clouds.average().toInt().takeIf { clouds.isNotEmpty() },
                        windSpeed = winds.average().takeIf { winds.isNotEmpty() },
                        visibility = vis.average().takeIf { vis.isNotEmpty() }
                    )
                } else null
                val moonPhases = list.mapNotNull { it.moonPhase }.distinct()
                val moonEmojis = list.mapNotNull { it.moonEmoji }.distinct()
                val moonIllums = list.mapNotNull { it.moonIllumination }
                val obsScores = list.mapNotNull { it.observingScore }
                val obsRatings = list.mapNotNull { it.observingRating }
                SessionStat(
                    sessionId = sid,
                    sessionName = first.sessionName ?: "Etkinlik",
                    measurementCount = list.size,
                    participantCount = session?.participantIds?.size ?: session?.participantCount,
                    avgMpsas = list.map { it.sqmValue }.average(),
                    bestMpsas = list.maxOf { it.sqmValue },
                    worstMpsas = list.minOf { it.sqmValue },
                    date = list.minOf { it.timestamp },
                    avgWeather = avgWeather.takeIf { avgWeather != null && (avgWeather.temperature != null || avgWeather.cloudCover != null) },
                    avgMoonPhase = moonPhases.firstOrNull(),
                    avgMoonEmoji = moonEmojis.firstOrNull(),
                    avgMoonIllumination = moonIllums.average().toInt().takeIf { moonIllums.isNotEmpty() },
                    avgObservingScore = obsScores.average().toInt().takeIf { obsScores.isNotEmpty() },
                    avgObservingRating = obsRatings.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                )
            }
            .sortedByDescending { it.date }

        return AnalysisState(
            totalMeasurements = totalMeasurements,
            uniqueDays = uniqueDays,
            averageMpsas = averageMpsas,
            bestMpsas = bestMpsas,
            worstMpsas = worstMpsas,
            bestBortleClass = bestBortleClass,
            worstBortleClass = worstBortleClass,
            bortleDistribution = bortleDistribution,
            bortleGrouped = bortleGrouped,
            timeSeriesData = timeSeriesData,
            sessionStats = sessionStats,
            filteredMeasurements = filtered,
            selectedTimeRange = timeRange,
            selectedSessionId = sessionId,
            excludeDaytimeMeasurements = excludeDaytime
        )
    }
}
