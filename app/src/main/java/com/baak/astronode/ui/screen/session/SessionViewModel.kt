package com.baak.astronode.ui.screen.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.Session
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.session.SessionSelectionManager
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
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val firestoreManager: FirestoreManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val sessionSelectionManager: SessionSelectionManager
) : ViewModel() {

    val currentUid: String? get() = firebaseAuthManager.currentUid

    private val uidFlow = flow {
        while (true) {
            emit(firebaseAuthManager.currentUid)
            delay(3000)
        }
    }

    /** Kendi oluşturduğun veya katıldığın session'lar (tüm tipler) */
    val myActiveSessions: StateFlow<List<Session>> = uidFlow
        .flatMapLatest { uid ->
            if (uid != null) firestoreManager.getMyActiveSessions(uid)
            else flowOf(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _publicSessionsRaw = firestoreManager.getActiveSessions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Herkese açık session'lar (sadece public, katılmadığın) */
    val publicSessions: StateFlow<List<Session>> = combine(
        _publicSessionsRaw,
        myActiveSessions
    ) { pub, my ->
        val myIds = my.map { it.id }.toSet()
        pub.filter { it.id !in myIds }
    }.stateIn(
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

    fun createSession(name: String, description: String?, type: String = "public") {
        viewModelScope.launch {
            val uid = try {
                firebaseAuthManager.ensureAnonymousAuth()
            } catch (_: Exception) {
                ""
            }
            val organizerName = firebaseAuthManager.currentUserDisplayName?.takeIf { it.isNotBlank() }
                ?: "Organizatör"
            val session = Session(
                name = name,
                description = description?.takeIf { it.isNotBlank() },
                createdBy = uid,
                type = type,
                organizerName = organizerName
            )
            firestoreManager.createSession(session).fold(
                onSuccess = { created -> sessionSelectionManager.selectSession(created) },
                onFailure = { _error.value = "Etkinlik oluşturulamadı" }
            )
        }
    }

    fun joinSessionByCode(code: String) {
        viewModelScope.launch {
            val uid = currentUid ?: try {
                firebaseAuthManager.ensureAnonymousAuth()
            } catch (_: Exception) {
                _error.value = "Oturum açmanız gerekir"
                return@launch
            }
            firestoreManager.joinSessionByCode(code, uid).fold(
                onSuccess = { session -> sessionSelectionManager.selectSession(session) },
                onFailure = { _error.value = it.message ?: "Katılım başarısız" }
            )
        }
    }

    fun leaveSession(sessionId: String) {
        viewModelScope.launch {
            val uid = currentUid ?: return@launch
            firestoreManager.leaveSession(sessionId, uid).fold(
                onSuccess = {
                    if (sessionSelectionManager.selectedSession.value?.id == sessionId) {
                        sessionSelectionManager.selectSession(null)
                    }
                },
                onFailure = { _error.value = it.message }
            )
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
        val participants = session.participantIds.size.let { if (it > 0) it else (session.participantCount ?: 0) }
        return count == 0 && participants <= 1
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

    private val _sessionForDetail = MutableStateFlow<Session?>(null)
    val sessionForDetail: StateFlow<Session?> = _sessionForDetail.asStateFlow()
    private val _detailMeasurementCount = MutableStateFlow(0)
    val detailMeasurementCount: StateFlow<Int> = _detailMeasurementCount.asStateFlow()

    fun showSessionDetail(session: Session?) {
        _sessionForDetail.value = session
        if (session != null) {
            viewModelScope.launch {
                _detailMeasurementCount.value = firestoreManager.getMeasurementCountBySession(session.id)
            }
        }
    }
}
