package com.baak.astronode.data.session

import com.baak.astronode.core.model.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionSelectionManager @Inject constructor() {

    private val _selectedSession = MutableStateFlow<Session?>(null)
    val selectedSession: StateFlow<Session?> = _selectedSession.asStateFlow()

    fun selectSession(session: Session?) {
        _selectedSession.value = session
    }

    fun clearSelection() {
        _selectedSession.value = null
    }
}
