package com.baak.astronode.ui.screen.splash

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val firebaseAuthManager: FirebaseAuthManager,
    private val userManager: UserManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _needsNickname = MutableStateFlow<Boolean?>(null)
    val needsNickname: StateFlow<Boolean?> = _needsNickname.asStateFlow()

    private val prefs = context.getSharedPreferences("astro_node_profile", Context.MODE_PRIVATE)

    fun checkProfileAndNavigate(onReady: (needsNickname: Boolean) -> Unit) {
        viewModelScope.launch {
            val uid = try {
                firebaseAuthManager.ensureAnonymousAuth()
            } catch (_: Exception) {
                onReady(true)
                return@launch
            }
            val profile = userManager.getUserProfile(uid).first()
            val setupDone = prefs.getBoolean(AppConstants.PREF_PROFILE_SETUP_DONE, false)
            val displayNameBlank = profile?.displayName?.isBlank() != false
            val needs = !setupDone && (profile == null || displayNameBlank)
            _needsNickname.value = needs
            onReady(needs)
        }
    }

    suspend fun completeProfileSetup(displayName: String): Result<Unit> {
        val uid = try {
            firebaseAuthManager.ensureAnonymousAuth()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return try {
            userManager.ensureUserProfile(uid, displayName)
            prefs.edit().putBoolean(AppConstants.PREF_PROFILE_SETUP_DONE, true).apply()
            _needsNickname.value = false
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
