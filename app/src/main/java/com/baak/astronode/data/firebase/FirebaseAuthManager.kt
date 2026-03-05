package com.baak.astronode.data.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

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
}
