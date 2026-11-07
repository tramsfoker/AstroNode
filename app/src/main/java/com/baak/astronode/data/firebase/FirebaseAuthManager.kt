package com.baak.astronode.data.firebase

import android.util.Log
import com.baak.astronode.core.constants.AppConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** İlk açılışta internet yoksa anonim hesap oluşturulamaz. */
class AuthUnavailableException(cause: Throwable? = null) :
    Exception("Hesap oluşturmak için bir kez internete bağlanmanız gerekiyor", cause)

/** Google hesabı zaten başka bir AstroNode hesabına bağlı. */
class GoogleAccountInUseException(cause: Throwable? = null) :
    Exception("Bu Google hesabı başka bir AstroNode hesabına bağlı", cause)

/** Hassas işlem (hesap silme) için yakın zamanda giriş gerekir. */
class RecentLoginRequiredException(cause: Throwable? = null) :
    Exception("Güvenlik için Google ile tekrar giriş yapıp yeniden deneyin", cause)

@Singleton
class FirebaseAuthManager @Inject constructor() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val signInMutex = Mutex()

    private val _uid = MutableStateFlow(auth.currentUser?.uid)
    /** Geçerli kullanıcının UID'si; oturum değişince güncellenir (polling gerekmez). */
    val uid: StateFlow<String?> = _uid.asStateFlow()

    init {
        auth.addAuthStateListener { _uid.value = it.currentUser?.uid }
    }

    val currentUid: String? get() = auth.currentUser?.uid

    val isAnonymous: Boolean get() = auth.currentUser?.isAnonymous != false

    val isLinkedWithGoogle: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == GoogleAuthProvider.PROVIDER_ID } == true

    val currentUserEmail: String? get() = auth.currentUser?.email

    val currentUserDisplayName: String? get() = auth.currentUser?.displayName

    fun signOut() {
        auth.signOut()
    }

    /**
     * Oturum yoksa anonim oturum açar. Aynı anda birden fazla çağrı tek bir hesap üretir.
     * Başarısız olursa [AuthUnavailableException] fırlatır — sahte/geçici UID üretilmez.
     */
    suspend fun ensureAnonymousAuth(): String {
        auth.currentUser?.let { return it.uid }
        return signInMutex.withLock {
            auth.currentUser?.let { return@withLock it.uid }
            try {
                withTimeout(AppConstants.AUTH_TIMEOUT_MS) {
                    auth.signInAnonymously().await().user?.uid
                } ?: throw AuthUnavailableException()
            } catch (e: AuthUnavailableException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Anonim giriş başarısız: ${e.message}")
                throw AuthUnavailableException(e)
            }
        }
    }

    /** UID varsa döndürür, yoksa null (hata fırlatmaz). */
    suspend fun uidOrNull(): String? = try {
        ensureAnonymousAuth()
    } catch (_: Exception) {
        null
    }

    /**
     * Anonim hesabı Google hesabına yükseltir (UID ve tüm ölçümler korunur).
     * Google hesabı başka bir AstroNode hesabına bağlıysa [GoogleAccountInUseException] döner;
     * bu durumda kullanıcıya sorulmadan hesap DEĞİŞTİRİLMEZ (önceki sürümde veri kaybına yol açıyordu).
     */
    suspend fun linkWithGoogle(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val currentUser = auth.currentUser
            val result = when {
                currentUser == null -> auth.signInWithCredential(credential).await()
                currentUser.isAnonymous -> currentUser.linkWithCredential(credential).await()
                else -> return Result.success(currentUser.uid)
            }
            Result.success(result.user?.uid ?: throw IllegalStateException("Kullanıcı bulunamadı"))
        } catch (e: FirebaseAuthUserCollisionException) {
            Result.failure(GoogleAccountInUseException(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Kullanıcı onayıyla mevcut (anonim) hesaptan çıkıp Google hesabına geçer.
     * Anonim hesaptaki veriler bu hesaba taşınmaz — çağıran taraf kullanıcıyı uyarmalıdır.
     */
    suspend fun switchToGoogleAccount(idToken: String): Result<String> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            Result.success(result.user?.uid ?: throw IllegalStateException("Kullanıcı bulunamadı"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Google ile yeniden kimlik doğrular (hesap silme öncesi gerekebilir). */
    suspend fun reauthenticateWithGoogle(idToken: String): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.failure(IllegalStateException("Oturum yok"))
            user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Firebase Auth kullanıcısını kalıcı olarak siler. */
    suspend fun deleteCurrentUser(): Result<Unit> {
        return try {
            val user = auth.currentUser ?: return Result.success(Unit)
            user.delete().await()
            Result.success(Unit)
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            Result.failure(RecentLoginRequiredException(e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val TAG = "AUTH"
    }
}
