package com.baak.astronode.data.firebase

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firebase Anonymous Auth yönetimi — MASTERPLAN T4.1
 */
@Singleton
class FirebaseAuthManager @Inject constructor(
    private val auth: FirebaseAuth
) {

    /**
     * Anonim giriş sağlar. Zaten giriş yapılmışsa mevcut UID'yi döndürür.
     * @return Firebase anonymous kullanıcı UID
     */
    suspend fun ensureAnonymousAuth(): String {
        val currentUser = auth.currentUser
        return if (currentUser != null) {
            currentUser.uid
        } else {
            auth.signInAnonymously().await().user?.uid
                ?: throw IllegalStateException("Anonim giriş başarısız")
        }
    }
}
