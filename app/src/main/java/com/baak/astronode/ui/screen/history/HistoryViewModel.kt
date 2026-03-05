package com.baak.astronode.ui.screen.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.domain.usecase.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

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

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState = _exportState.asStateFlow()

    fun exportToCsv() {
        viewModelScope.launch {
            _exportState.value = ExportState.Loading
            _exportState.value = try {
                val uri = exportDataUseCase.exportToCsv()
                if (uri != null) ExportState.Success(uri) else ExportState.Error("Veri yok")
            } catch (e: Exception) {
                ExportState.Error(e.message ?: "Dışa aktarma başarısız")
            }
        }
    }

    fun clearExportState() {
        _exportState.value = ExportState.Idle
    }
}

sealed class ExportState {
    data object Idle : ExportState()
    data object Loading : ExportState()
    data class Success(val uri: Uri) : ExportState()
    data class Error(val message: String) : ExportState()
}
