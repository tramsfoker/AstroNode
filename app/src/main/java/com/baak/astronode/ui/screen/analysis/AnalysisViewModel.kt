package com.baak.astronode.ui.screen.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.data.firebase.FirestoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
    val avgMpsas: Double,
    val minMpsas: Double,
    val maxMpsas: Double,
    val date: Long
)

enum class TimeRange(val label: String) {
    LAST_7_DAYS("Son 7 Gün"),
    LAST_30_DAYS("Son 30 Gün"),
    ALL_TIME("Tümü")
}

data class AnalysisState(
    val totalMeasurements: Int = 0,
    val averageMpsas: Double = 0.0,
    val minMpsas: Double = 0.0,
    val maxMpsas: Double = 0.0,
    val minBortleClass: Int = 9,
    val maxBortleClass: Int = 1,
    val bortleDistribution: Map<Int, Int> = emptyMap(),
    val timeSeriesData: List<TimeSeriesPoint> = emptyList(),
    val sessionStats: List<SessionStat> = emptyList(),
    val selectedTimeRange: TimeRange = TimeRange.LAST_30_DAYS,
    val selectedSessionId: String? = null
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val firestoreManager: FirestoreManager
) : ViewModel() {

    val activeSessions: StateFlow<List<Session>> = firestoreManager
        .getActiveSessions()
        .stateIn(
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

    val analysisState: StateFlow<AnalysisState> = combine(
        measurements,
        _selectedTimeRange,
        _selectedSessionId
    ) { all, timeRange, sessionId ->
        val filtered = applyFilters(all, timeRange, sessionId)
        computeAnalysisState(filtered, timeRange, sessionId)
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

    private fun applyFilters(
        measurements: List<SkyMeasurement>,
        timeRange: TimeRange,
        sessionId: String?
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

        return result.sortedBy { it.timestamp }
    }

    private fun computeAnalysisState(
        filtered: List<SkyMeasurement>,
        timeRange: TimeRange,
        sessionId: String?
    ): AnalysisState {
        if (filtered.isEmpty()) {
            return AnalysisState(
                selectedTimeRange = timeRange,
                selectedSessionId = sessionId
            )
        }

        val totalMeasurements = filtered.size
        val mpsasValues = filtered.map { it.sqmValue }
        val averageMpsas = mpsasValues.average()
        val minMpsas = mpsasValues.minOrNull() ?: 0.0
        val maxMpsas = mpsasValues.maxOrNull() ?: 0.0

        val bortleValues = filtered.map { it.bortleClass }
        val minBortleClass = bortleValues.minOrNull() ?: 9
        val maxBortleClass = bortleValues.maxOrNull() ?: 1

        val bortleDistribution = filtered.groupingBy { it.bortleClass }.eachCount()
            .toSortedMap()

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
                SessionStat(
                    sessionId = sid,
                    sessionName = first.sessionName ?: "Etkinlik",
                    measurementCount = list.size,
                    avgMpsas = list.map { it.sqmValue }.average(),
                    minMpsas = list.minOf { it.sqmValue },
                    maxMpsas = list.maxOf { it.sqmValue },
                    date = list.minOf { it.timestamp }
                )
            }
            .sortedByDescending { it.date }

        return AnalysisState(
            totalMeasurements = totalMeasurements,
            averageMpsas = averageMpsas,
            minMpsas = minMpsas,
            maxMpsas = maxMpsas,
            minBortleClass = minBortleClass,
            maxBortleClass = maxBortleClass,
            bortleDistribution = bortleDistribution,
            timeSeriesData = timeSeriesData,
            sessionStats = sessionStats,
            selectedTimeRange = timeRange,
            selectedSessionId = sessionId
        )
    }
}
