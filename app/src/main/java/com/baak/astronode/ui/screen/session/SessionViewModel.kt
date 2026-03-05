package com.baak.astronode.ui.screen.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.model.Session
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.session.SessionSelectionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun selectSession(session: Session?) {
        sessionSelectionManager.selectSession(session)
    }

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
}
