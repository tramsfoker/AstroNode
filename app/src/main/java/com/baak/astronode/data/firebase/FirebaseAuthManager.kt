package com.baak.astronode.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

    val isLinkedWithGoogle: Boolean
        get() {
            val user = auth.currentUser ?: return false
            return user.providerData.any { it.providerId == "google.com" }
        }

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    val currentUserDisplayName: String?
        get() = auth.currentUser?.displayName

    fun signOut() {
        auth.signOut()
    }

    suspend fun ensureAnonymousAuth(): String {
        auth.currentUser?.let { return it.uid }

        return try {
            withTimeout(5000L) {
                val result = auth.signInAnonymously().await()
                result.user?.uid ?: "offline_${System.currentTimeMillis()}"
            }
        } catch (e: Exception) {
            Log.w("AUTH", "Auth timeout/hata, geçici ID: ${e.message}")
            "offline_${System.currentTimeMillis()}"
        }
    }

    suspend fun linkWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val currentUser = auth.currentUser

            if (currentUser != null && currentUser.isAnonymous) {
                val result = currentUser.linkWithCredential(credential).await()
                Result.success(result.user?.uid ?: "")
            } else if (currentUser != null) {
                Result.success(currentUser.uid)
            } else {
                val result = auth.signInWithCredential(credential).await()
                Result.success(result.user?.uid ?: "")
            }
        } catch (e: Exception) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                Result.success(result.user?.uid ?: "")
            } catch (e2: Exception) {
                Result.failure(e2)
            }
        }
    }
}
