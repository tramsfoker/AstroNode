package com.baak.astronode.ui.screen.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.Session
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.session.SessionSelectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val firestoreManager: FirestoreManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val sessionSelectionManager: SessionSelectionManager
) : ViewModel() {

    val activeSessions: StateFlow<List<Session>> = firestoreManager
        .getActiveSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val selectedSession: StateFlow<Session?> = sessionSelectionManager.selectedSession

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun selectSession(session: Session?) {
        sessionSelectionManager.selectSession(session)
    }

    val currentUid: String? get() = firebaseAuthManager.currentUid

    fun createSession(name: String, description: String?) {
        viewModelScope.launch {
            val uid = try {
                firebaseAuthManager.ensureAnonymousAuth()
            } catch (_: Exception) {
                ""
            }
            val session = Session(
                name = name,
                description = description?.takeIf { it.isNotBlank() },
                createdBy = uid
            )
            firestoreManager.createSession(session)
            sessionSelectionManager.selectSession(session)
        }
    }

    fun endSession(sessionId: String) {
        viewModelScope.launch {
            firestoreManager.endSession(sessionId)
            if (sessionSelectionManager.selectedSession.value?.id == sessionId) {
                sessionSelectionManager.selectSession(null)
            }
        }
    }

    fun cancelSession(sessionId: String) {
        viewModelScope.launch {
            firestoreManager.cancelSession(sessionId)
            if (sessionSelectionManager.selectedSession.value?.id == sessionId) {
                sessionSelectionManager.selectSession(null)
            }
        }
    }

    suspend fun canDeleteSession(session: Session): Boolean {
        val count = firestoreManager.getMeasurementCountBySession(session.id)
        val participants = session.participantCount ?: 0
        return count == 0 && participants == 0
    }

    fun deleteSession(session: Session) {
        viewModelScope.launch {
            if (canDeleteSession(session)) {
                firestoreManager.deleteSession(session.id)
                if (sessionSelectionManager.selectedSession.value?.id == session.id) {
                    sessionSelectionManager.selectSession(null)
                }
            } else {
                _error.value = "Etkinlik silinemez: katılımcı veya ölçüm var"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
