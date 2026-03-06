package com.baak.astronode.ui.screen.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.R
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.UserProfile
import com.baak.astronode.core.model.UserSettings
import com.baak.astronode.core.util.ThemeMode
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.UserManager
import com.baak.astronode.domain.usecase.ExportDataUseCase
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userManager: UserManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val exportDataUseCase: ExportDataUseCase,
    private val themePreference: com.baak.astronode.core.util.ThemePreference,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uid = MutableStateFlow<String?>(null)
    private val uid: String? get() = _uid.value ?: firebaseAuthManager.currentUid

    init {
        viewModelScope.launch {
            _uid.value = try {
                firebaseAuthManager.ensureAnonymousAuth()
            } catch (_: Exception) {
                null
            }
        }
    }

    private val _displayNameOverride = MutableStateFlow<String?>(null)

    val userProfile: StateFlow<UserProfile?> = combine(
        flow {
            val u = firebaseAuthManager.ensureAnonymousAuth()
            emit(u)
        }.flatMapLatest { u ->
            userManager.getUserProfile(u)
        },
        _displayNameOverride
    ) { profile, override ->
        when {
            profile != null && override != null -> profile.copy(displayName = override)
            else -> profile
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val userSessions: StateFlow<List<Session>> = flow {
        val u = firebaseAuthManager.ensureAnonymousAuth()
        emit(u)
    }.flatMapLatest { u ->
        userManager.getUserSessions(u)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val googleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    fun handleGoogleSignInResult(idToken: String) {
        viewModelScope.launch {
            val result = firebaseAuthManager.linkWithGoogle(idToken)
            result.onSuccess { uid ->
                val user = FirebaseAuth.getInstance().currentUser
                user?.email?.let { email ->
                    userManager.updateEmail(uid, email)
                }
                val currentDisplayName = userProfile.value?.displayName?.takeIf { it.isNotBlank() }
                val googleDisplayName = user?.displayName?.takeIf { it.isNotBlank() }
                if (currentDisplayName.isNullOrBlank() && googleDisplayName != null) {
                    userManager.updateDisplayName(uid, googleDisplayName)
                }
            }
            result.onFailure { e ->
                _error.value = "Google bağlama başarısız: ${e.message}"
            }
            result.onSuccess {
                _authStateVersion.value++
            }
        }
    }

    private val _authStateVersion = MutableStateFlow(0)
    val authStateVersion: StateFlow<Int> = _authStateVersion.asStateFlow()

    val isLinkedWithGoogle: Boolean get() = firebaseAuthManager.isLinkedWithGoogle
    val currentUserEmail: String? get() = firebaseAuthManager.currentUserEmail
    val currentUserDisplayNameFromAuth: String? get() = firebaseAuthManager.currentUserDisplayName

    fun updateDisplayName(name: String) {
        val u = uid ?: return
        viewModelScope.launch {
            val result = userManager.updateDisplayName(u, name)
            if (result.isSuccess) {
                _displayNameOverride.value = name
            } else {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun updateTheme(theme: String) {
        val u = uid ?: return
        val mode = when (theme) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        themePreference.setThemeMode(mode)
        viewModelScope.launch {
            val settings = (userProfile.value?.settings ?: UserSettings()).copy(theme = theme)
            userManager.updateSettings(u, settings)
                .onFailure { _error.value = it.message }
        }
    }

    fun updateUnit(unit: String) {
        val u = uid ?: return
        viewModelScope.launch {
            val settings = (userProfile.value?.settings ?: UserSettings()).copy(unit = unit)
            userManager.updateSettings(u, settings)
                .onFailure { _error.value = it.message }
        }
    }

    fun exportData() {
        viewModelScope.launch {
            val uri = exportDataUseCase.exportToCsv()
            _exportUri.value = uri
        }
    }

    fun clearExportUri() {
        _exportUri.value = null
    }

    fun openGitHub() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/baak-astro/astronode"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun resetAccount(activity: ComponentActivity?) {
        googleSignInClient.signOut()
        firebaseAuthManager.signOut()
        context.getSharedPreferences("astro_node_profile", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        activity?.recreate()
    }

    fun clearError() {
        _error.value = null
    }
}
