package com.baak.astronode.data.firebase

import android.util.Log
import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.UserProfile
import com.baak.astronode.core.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserManager @Inject constructor(
    private val firestoreManager: FirestoreManager
) {

    private val db = FirebaseFirestore.getInstance()
    private val usersCollection = db.collection(AppConstants.FIRESTORE_COLLECTION_USERS)
    private val measurementsCollection = db.collection(AppConstants.FIRESTORE_COLLECTION_MEASUREMENTS)

    /**
     * Profil yoksa oluşturur, varsa sadece günceller. Okuma hatasında (ör. offline + cache boş)
     * mevcut profilin üzerine YAZMAZ — önceki sürüm bu durumda sayaçları sıfırlıyordu.
     */
    suspend fun ensureUserProfile(uid: String, displayName: String?): UserProfile {
        val cleanName = displayName?.trim()?.take(AppConstants.DISPLAY_NAME_MAX).orEmpty()
        val doc = try {
            usersCollection.document(uid).get().await()
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "Profil okunamadı, oluşturma ertelendi: ${e.message}")
            return UserProfile(uid = uid, displayName = cleanName)
        }

        // Offline'da cache "yok" diyorsa sunucuda var olabilir: üzerine yazma riskine girme
        if (!doc.exists() && doc.metadata.isFromCache) {
            return UserProfile(uid = uid, displayName = cleanName)
        }

        if (doc.exists()) {
            val profile = mapToUserProfile(uid, doc.data ?: emptyMap())
            val updates = mutableMapOf<String, Any>("last_active_at" to System.currentTimeMillis())
            if (cleanName.isNotBlank() && profile.displayName.isBlank()) updates["display_name"] = cleanName
            usersCollection.document(uid).set(updates, SetOptions.merge())
            return if (updates.containsKey("display_name")) profile.copy(displayName = cleanName) else profile
        }

        val now = System.currentTimeMillis()
        val profile = UserProfile(uid = uid, displayName = cleanName, createdAt = now, lastActiveAt = now)
        usersCollection.document(uid).set(
            mapOf(
                "display_name" to profile.displayName,
                "role" to "observer",
                "organization_id" to null,
                "organization_name" to null,
                "total_measurements" to 0,
                "total_sessions" to 0,
                "best_mpsas" to null,
                "created_at" to now,
                "last_active_at" to now,
                "settings" to mapOf("theme" to profile.settings.theme, "unit" to profile.settings.unit)
            )
        )
        return profile
    }

    fun getUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val listener = usersCollection.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.takeIf { it.exists() }?.let { mapToUserProfile(uid, it.data ?: emptyMap()) })
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateDisplayName(uid: String, name: String): Result<Unit> =
        merge(uid, mapOf("display_name" to name.trim().take(AppConstants.DISPLAY_NAME_MAX)))

    suspend fun updateEmail(uid: String, email: String): Result<Unit> =
        merge(uid, mapOf("email" to email.take(254)))

    suspend fun updateSettings(uid: String, settings: UserSettings): Result<Unit> =
        merge(uid, mapOf("settings" to mapOf("theme" to settings.theme, "unit" to settings.unit)))

    private suspend fun merge(uid: String, fields: Map<String, Any>): Result<Unit> = try {
        usersCollection.document(uid)
            .set(fields + ("last_active_at" to System.currentTimeMillis()), SetOptions.merge())
            .await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Ölçüm yaptığım etkinlikler (profil ekranı). */
    fun getUserSessions(uid: String): Flow<List<Session>> = getSessionIdsByUser(uid)
        .distinctUntilChanged()
        .flatMapLatest { sessionIds ->
            flow {
                val sessions = sessionIds.mapNotNull { firestoreManager.getSessionOnce(it) }
                emit(sessions.sortedByDescending { it.date })
            }
        }

    private fun getSessionIdsByUser(uid: String): Flow<List<String>> = callbackFlow {
        val listener = measurementsCollection
            .whereEqualTo("observer_uid", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(AppConstants.FIRESTORE_QUERY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents
                        ?.mapNotNull { it.getString("session_id") }
                        ?.filter { it.isNotBlank() }
                        ?.distinct()
                        ?: emptyList()
                )
            }
        awaitClose { listener.remove() }
    }

    private fun mapToUserProfile(uid: String, data: Map<String, Any?>): UserProfile {
        val settingsMap = data["settings"] as? Map<*, *>
        return UserProfile(
            uid = uid,
            displayName = data["display_name"] as? String ?: "",
            role = data["role"] as? String ?: "observer",
            organizationId = data["organization_id"] as? String,
            organizationName = data["organization_name"] as? String,
            totalMeasurements = (data["total_measurements"] as? Number)?.toInt() ?: 0,
            totalSessions = (data["total_sessions"] as? Number)?.toInt() ?: 0,
            bestMpsas = (data["best_mpsas"] as? Number)?.toDouble(),
            createdAt = (data["created_at"] as? Number)?.toLong() ?: 0L,
            lastActiveAt = (data["last_active_at"] as? Number)?.toLong() ?: 0L,
            settings = UserSettings(
                theme = settingsMap?.get("theme") as? String ?: "auto",
                unit = settingsMap?.get("unit") as? String ?: "mpsas"
            )
        )
    }

    private companion object {
        const val TAG = "USER"
    }
}
