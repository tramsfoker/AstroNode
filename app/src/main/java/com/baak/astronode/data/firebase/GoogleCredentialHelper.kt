package com.baak.astronode.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.baak.astronode.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import javax.inject.Inject
import javax.inject.Singleton

/** Kullanıcı Google seçim ekranını kapattı — hata mesajı gösterilmez. */
class GoogleSignInCancelledException : Exception("Google ile giriş iptal edildi")

/**
 * Credential Manager ile Google ID token alır (eski GoogleSignIn API'si kaldırıldı).
 * [activityContext] bir Activity olmalıdır (seçim ekranı onun üzerinde açılır).
 */
@Singleton
class GoogleCredentialHelper @Inject constructor() {

    suspend fun getIdToken(activityContext: Context): Result<String> {
        val option = GetSignInWithGoogleOption.Builder(
            activityContext.getString(R.string.default_web_client_id)
        ).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val response = CredentialManager.create(activityContext).getCredential(activityContext, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                Result.success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.failure(IllegalStateException("Beklenmeyen kimlik türü"))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(GoogleSignInCancelledException())
        } catch (e: NoCredentialException) {
            Result.failure(Exception("Cihazda Google hesabı bulunamadı. Ayarlar'dan bir Google hesabı ekleyin.", e))
        } catch (e: GetCredentialException) {
            Log.w(TAG, "Google kimliği alınamadı: ${e.type} ${e.message}")
            Result.failure(Exception("Google ile giriş yapılamadı", e))
        } catch (e: GoogleIdTokenParsingException) {
            Result.failure(Exception("Google yanıtı okunamadı", e))
        }
    }

    /** Çıkış/hesap silme sonrası kayıtlı Google oturum durumunu temizler. */
    suspend fun clear(context: Context) {
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    private companion object {
        const val TAG = "GOOGLE_AUTH"
    }
}
