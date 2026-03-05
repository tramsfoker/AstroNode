package com.baak.astronode.ui.screen.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.domain.usecase.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.LinkedHashMap
import java.util.Locale
import javax.inject.Inject

enum class TimeFilter(val label: String) {
    TODAY("Bugün"),
    THIS_WEEK("Bu Hafta"),
    THIS_MONTH("Bu Ay"),
    ALL("Tümü")
}

enum class BortleFilter(val label: String, val range: IntRange?) {
    ALL("Tümü", null),
    DARK_1_3("Karanlık (1-3)", 1..3),
    MEDIUM_4_6("Orta (4-6)", 4..6),
    BRIGHT_7_9("Aydınlık (7-9)", 7..9)
}

enum class SortOrder(val label: String) {
    NEWEST_FIRST("Yeniden Eskiye"),
    OLDEST_FIRST("Eskiden Yeniye"),
    DARKEST("En Karanlık"),
    BRIGHTEST("En Aydınlık")
}

data class HistoryFilterState(
    val timeFilter: TimeFilter = TimeFilter.ALL,
    val sessionFilter: String? = null,  // null = tümü, SESSION_FREE = serbest, sessionId = o etkinlik
    val bortleFilter: BortleFilter = BortleFilter.ALL,
    val sortOrder: SortOrder = SortOrder.NEWEST_FIRST,
    val searchQuery: String = ""
)

const val SESSION_FREE = "FREE"

private val dateFormatGroupKey = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("tr"))

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val firestoreManager: FirestoreManager,
    private val exportDataUseCase: ExportDataUseCase
) : ViewModel() {

    val measurements: StateFlow<List<SkyMeasurement>> = firestoreManager
        .getMeasurements()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeSessions: StateFlow<List<Session>> = firestoreManager
        .getActiveSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _filterState = MutableStateFlow(HistoryFilterState())
    val filterState = _filterState.asStateFlow()

    val filteredMeasurements: StateFlow<List<SkyMeasurement>> = combine(
        measurements,
        _filterState
    ) { all, filter ->
        applyFilters(all, filter)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val groupedMeasurements: StateFlow<Map<String, List<SkyMeasurement>>> = filteredMeasurements
        .map { groupByDate(it) }
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState = _exportState.asStateFlow()

    fun setTimeFilter(filter: TimeFilter) {
        _filterState.value = _filterState.value.copy(timeFilter = filter)
    }

    fun setSessionFilter(sessionId: String?) {
        _filterState.value = _filterState.value.copy(sessionFilter = sessionId)
    }

    fun setBortleFilter(filter: BortleFilter) {
        _filterState.value = _filterState.value.copy(bortleFilter = filter)
    }

    fun setSortOrder(order: SortOrder) {
        _filterState.value = _filterState.value.copy(sortOrder = order)
    }

    fun setSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query.trim())
    }

    fun exportToCsv() {
        viewModelScope.launch {
            val toExport = filteredMeasurements.value
            if (toExport.isEmpty()) {
                _exportState.value = ExportState.Error("Dışa aktarılacak ölçüm yok")
                return@launch
            }
            _exportState.value = ExportState.Loading
            _exportState.value = try {
                val uri = exportDataUseCase.exportToCsv(toExport)
                if (uri != null) ExportState.Success(uri) else ExportState.Error("Veri yok")
            } catch (e: Exception) {
                ExportState.Error(e.message ?: "Dışa aktarma başarısız")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }

    private fun applyFilters(
        measurements: List<SkyMeasurement>,
        filter: HistoryFilterState
    ): List<SkyMeasurement> {
        var result = measurements

        // TimeFilter
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        result = when (filter.timeFilter) {
            TimeFilter.TODAY -> {
                val startOfDay = cal.timeInMillis
                result.filter { it.timestamp >= startOfDay }
            }
            TimeFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val startOfWeek = cal.timeInMillis
                result.filter { it.timestamp >= startOfWeek }
            }
            TimeFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val startOfMonth = cal.timeInMillis
                result.filter { it.timestamp >= startOfMonth }
            }
            TimeFilter.ALL -> result
        }

        // SessionFilter
        result = when (filter.sessionFilter) {
            null -> result
            SESSION_FREE -> result.filter { it.sessionId.isNullOrBlank() }
            else -> result.filter { it.sessionId == filter.sessionFilter }
        }

        // BortleFilter
        filter.bortleFilter.range?.let { range ->
            result = result.filter { it.bortleClass in range }
        }

        // SearchQuery (note içinde arama)
        if (filter.searchQuery.isNotBlank()) {
            val q = filter.searchQuery.lowercase()
            result = result.filter { m ->
                m.note?.lowercase()?.contains(q) == true
            }
        }

        // SortOrder
        result = when (filter.sortOrder) {
            SortOrder.NEWEST_FIRST -> result.sortedByDescending { it.timestamp }
            SortOrder.OLDEST_FIRST -> result.sortedBy { it.timestamp }
            SortOrder.DARKEST -> result.sortedByDescending { it.sqmValue }
            SortOrder.BRIGHTEST -> result.sortedBy { it.sqmValue }
        }

        return result
    }

    private fun groupByDate(measurements: List<SkyMeasurement>): Map<String, List<SkyMeasurement>> {
        return measurements
            .groupBy { m -> dateFormatGroupKey.format(Date(m.timestamp)) }
            .toList()
            .sortedByDescending { (_, list) -> list.maxOfOrNull { it.timestamp } ?: 0L }
            .toMap(LinkedHashMap())
    }
}

sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    data class Success(val uri: Uri) : ExportState()
    data class Error(val message: String) : ExportState()
}
