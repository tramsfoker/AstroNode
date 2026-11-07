package com.baak.astronode.ui.screen.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.model.UserProfile
import com.baak.astronode.core.model.UserSettings
import com.baak.astronode.core.util.ThemeMode
import com.baak.astronode.core.util.ThemePreference
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.firebase.GoogleAccountInUseException
import com.baak.astronode.data.firebase.GoogleCredentialHelper
import com.baak.astronode.data.firebase.GoogleSignInCancelledException
import com.baak.astronode.data.firebase.RecentLoginRequiredException
import com.baak.astronode.data.firebase.UserManager
import com.baak.astronode.domain.usecase.ExportDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

/** Hesap silme akışının durumu */
sealed interface DeleteAccountState {
    data object Idle : DeleteAccountState
    data object InProgress : DeleteAccountState
    data object Done : DeleteAccountState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userManager: UserManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val firestoreManager: FirestoreManager,
    private val googleCredentialHelper: GoogleCredentialHelper,
    private val contentRepository: com.baak.astronode.data.content.ContentRepository,
    private val exportDataUseCase: ExportDataUseCase,
    private val themePreference: ThemePreference,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val uid: String? get() = firebaseAuthManager.currentUid

    init {
        viewModelScope.launch { firebaseAuthManager.uidOrNull() }
    }

    private val _displayNameOverride = MutableStateFlow<String?>(null)

    val userProfile: StateFlow<UserProfile?> = combine(
        firebaseAuthManager.uid.flatMapLatest { u ->
            if (u != null) userManager.getUserProfile(u) else flowOf(null)
        },
        _displayNameOverride
    ) { profile, override ->
        if (profile != null && override != null) profile.copy(displayName = override) else profile
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val userSessions: StateFlow<List<Session>> = firebaseAuthManager.uid
        .flatMapLatest { u -> if (u != null) userManager.getUserSessions(u) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val myMeasurements: StateFlow<List<SkyMeasurement>> = firebaseAuthManager.uid
        .flatMapLatest { u -> if (u != null) firestoreManager.getMyMeasurements(u) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val organizationRequests: StateFlow<List<com.baak.astronode.core.model.OrganizationRequest>> = firebaseAuthManager.uid
        .flatMapLatest { u -> if (u != null) contentRepository.getMyOrganizationRequests(u) else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Organizatör başvurusu. Başarılıysa true döner (diyalog kapanır). */
    suspend fun submitOrganizationRequest(
        orgName: String, city: String, website: String, email: String, description: String
    ): Boolean {
        val u = firebaseAuthManager.uidOrNull() ?: return false
        val emailOk = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
        if (orgName.trim().length < 3 || !emailOk) {
            _errorRes.value = com.baak.astronode.R.string.org_invalid
            return false
        }
        return contentRepository.submitOrganizationRequest(
            uid = u,
            requesterName = userProfile.value?.displayName,
            orgName = orgName, description = description, city = city, website = website, contactEmail = email
        ).fold(
            onSuccess = { _errorRes.value = com.baak.astronode.R.string.org_submitted; true },
            onFailure = { _error.value = it.message; false }
        )
    }

    /** Kaynak kimliği ile mesaj (çok dilli) */
    private val _errorRes = MutableStateFlow<Int?>(null)
    val errorRes: StateFlow<Int?> = _errorRes.asStateFlow()
    fun clearErrorRes() { _errorRes.value = null }

    private val _exportUri = MutableStateFlow<Uri?>(null)
    val exportUri: StateFlow<Uri?> = _exportUri.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Google hesabı başka bir AstroNode hesabına bağlıysa, geçiş onayı için bekletilen token. */
    private val _pendingAccountSwitchToken = MutableStateFlow<String?>(null)
    val pendingAccountSwitch: StateFlow<String?> = _pendingAccountSwitchToken.asStateFlow()

    private val _deleteState = MutableStateFlow<DeleteAccountState>(DeleteAccountState.Idle)
    val deleteState: StateFlow<DeleteAccountState> = _deleteState.asStateFlow()

    private val _authStateVersion = MutableStateFlow(0)
    val authStateVersion: StateFlow<Int> = _authStateVersion.asStateFlow()

    val isLinkedWithGoogle: Boolean get() = firebaseAuthManager.isLinkedWithGoogle
    val currentUserEmail: String? get() = firebaseAuthManager.currentUserEmail
    val currentUserDisplayNameFromAuth: String? get() = firebaseAuthManager.currentUserDisplayName

    /** Anonim hesabı Google'a bağlar. [activity] seçim ekranı için gereklidir. */
    fun linkWithGoogle(activity: Activity) {
        viewModelScope.launch {
            val token = googleCredentialHelper.getIdToken(activity).getOrElse { e ->
                if (e !is GoogleSignInCancelledException) _error.value = e.message
                return@launch
            }
            firebaseAuthManager.linkWithGoogle(token)
                .onSuccess { linkedUid -> onGoogleSignedIn(linkedUid) }
                .onFailure { e ->
                    if (e is GoogleAccountInUseException) {
                        _pendingAccountSwitchToken.value = token
                    } else {
                        _error.value = "Google bağlama başarısız: ${e.message}"
                    }
                }
        }
    }

    /** Kullanıcı uyarıyı onayladı: mevcut anonim hesaptan Google hesabına geç. */
    fun confirmAccountSwitch() {
        val token = _pendingAccountSwitchToken.value ?: return
        _pendingAccountSwitchToken.value = null
        viewModelScope.launch {
            firebaseAuthManager.switchToGoogleAccount(token)
                .onSuccess { newUid -> onGoogleSignedIn(newUid) }
                .onFailure { _error.value = "Hesap değiştirilemedi: ${it.message}" }
        }
    }

    fun dismissAccountSwitch() {
        _pendingAccountSwitchToken.value = null
    }

    private suspend fun onGoogleSignedIn(newUid: String) {
        val profile = userManager.ensureUserProfile(newUid, firebaseAuthManager.currentUserDisplayName)
        firebaseAuthManager.currentUserEmail?.let { userManager.updateEmail(newUid, it) }
        if (profile.displayName.isBlank()) {
            firebaseAuthManager.currentUserDisplayName?.takeIf { it.isNotBlank() }
                ?.let { userManager.updateDisplayName(newUid, it) }
        }
        _authStateVersion.value++
    }

    fun updateDisplayName(name: String) {
        val u = uid ?: return
        viewModelScope.launch {
            userManager.updateDisplayName(u, name)
                .onSuccess { _displayNameOverride.value = name }
                .onFailure { _error.value = it.message }
        }
    }

    fun updateTheme(theme: String) {
        val mode = when (theme) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.AUTO
        }
        themePreference.setThemeMode(mode)
        val u = uid ?: return
        viewModelScope.launch {
            val settings = (userProfile.value?.settings ?: UserSettings()).copy(theme = theme)
            userManager.updateSettings(u, settings).onFailure { _error.value = it.message }
        }
    }

    fun updateUnit(unit: String) {
        val u = uid ?: return
        viewModelScope.launch {
            val settings = (userProfile.value?.settings ?: UserSettings()).copy(unit = unit)
            userManager.updateSettings(u, settings).onFailure { _error.value = it.message }
        }
    }

    /** Sadece kendi ölçümlerimi dışa aktarır. */
    fun exportData() {
        viewModelScope.launch {
            val list = myMeasurements.value
            if (list.isEmpty()) {
                _error.value = "Dışa aktarılacak ölçüm yok"
                return@launch
            }
            _exportUri.value = exportDataUseCase.exportToCsv(list)
        }
    }

    fun clearExportUri() {
        _exportUri.value = null
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { _error.value = "Bağlantı açılamadı" }
    }

    fun openPrivacyPolicy() = openUrl(AppConstants.PRIVACY_POLICY_URL)

    /** Google hesabından çıkış: veriler Google hesabında kalır, cihaz yeni anonim hesapla devam eder. */
    fun signOut(activity: Activity) {
        viewModelScope.launch {
            googleCredentialHelper.clear(activity)
            firebaseAuthManager.signOut()
            clearLocalProfilePrefs()
            activity.recreate()
        }
    }

    /**
     * Hesabı ve tüm verileri kalıcı olarak siler (Google Play zorunluluğu).
     * Google hesaplarında Firebase yakın zamanda giriş isteyebilir; bu durumda yeniden doğrulama yapılır.
     */
    fun deleteAccount(activity: Activity) {
        val u = uid ?: return
        if (_deleteState.value == DeleteAccountState.InProgress) return
        _deleteState.value = DeleteAccountState.InProgress
        viewModelScope.launch {
            val dataResult = firestoreManager.deleteAllUserData(u)
            if (dataResult.isFailure) {
                _deleteState.value = DeleteAccountState.Idle
                _error.value = "Veriler silinemedi. İnternet bağlantınızı kontrol edip tekrar deneyin."
                return@launch
            }
            var authResult = firebaseAuthManager.deleteCurrentUser()
            if (authResult.exceptionOrNull() is RecentLoginRequiredException && firebaseAuthManager.isLinkedWithGoogle) {
                val token = googleCredentialHelper.getIdToken(activity).getOrNull()
                if (token != null && firebaseAuthManager.reauthenticateWithGoogle(token).isSuccess) {
                    authResult = firebaseAuthManager.deleteCurrentUser()
                }
            }
            authResult.onFailure {
                _deleteState.value = DeleteAccountState.Idle
                _error.value = it.message ?: "Hesap silinemedi"
                return@launch
            }
            googleCredentialHelper.clear(activity)
            firebaseAuthManager.signOut()
            clearLocalProfilePrefs()
            _deleteState.value = DeleteAccountState.Done
            activity.recreate()
        }
    }

    private fun clearLocalProfilePrefs() {
        context.getSharedPreferences(AppConstants.PREFS_PROFILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun clearError() {
        _error.value = null
    }
}
