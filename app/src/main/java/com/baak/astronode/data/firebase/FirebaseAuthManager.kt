package com.baak.astronode.data.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun ensureAnonymousAuth(): String {
        auth.currentUser?.let { return it.uid }

        val result = auth.signInAnonymously().await()
        return result.user?.uid
            ?: throw IllegalStateException("Anonim giriş başarısız: UID alınamadı")
    }
}
