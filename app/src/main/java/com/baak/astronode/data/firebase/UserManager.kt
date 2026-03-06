package com.baak.astronode.data.firebase

import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.UserProfile
import com.baak.astronode.core.model.UserSettings
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
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
    private val usersCollection = db.collection("users")
    private val measurementsCollection = db.collection(AppConstants.FIRESTORE_COLLECTION_MEASUREMENTS)

    suspend fun ensureUserProfile(uid: String, displayName: String?): UserProfile {
        try {
            val doc = usersCollection.document(uid).get().await()
            if (doc.exists()) {
                val data = doc.data ?: emptyMap()
                val profile = mapToUserProfile(uid, data)
                usersCollection.document(uid).update(
                    mapOf("last_active_at" to System.currentTimeMillis())
                ).await()
                return profile
            }
        } catch (_: Exception) { }

        val profile = UserProfile(
            uid = uid,
            displayName = displayName ?: "",
            role = "observer",
            createdAt = System.currentTimeMillis(),
            lastActiveAt = System.currentTimeMillis()
        )
        val data = mapOf(
            "display_name" to profile.displayName,
            "role" to profile.role,
            "organization_id" to profile.organizationId,
            "organization_name" to profile.organizationName,
            "total_measurements" to profile.totalMeasurements,
            "total_sessions" to profile.totalSessions,
            "best_mpsas" to profile.bestMpsas,
            "created_at" to profile.createdAt,
            "last_active_at" to profile.lastActiveAt,
            "settings" to mapOf(
                "theme" to profile.settings.theme,
                "unit" to profile.settings.unit
            )
        )
        usersCollection.document(uid).set(data).await()
        return profile
    }

    fun getUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        val listener = usersCollection.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val data = snapshot.data ?: emptyMap()
                    trySend(mapToUserProfile(uid, data))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun updateDisplayName(uid: String, name: String): Result<Unit> {
        return try {
            usersCollection.document(uid).update(
                mapOf(
                    "display_name" to name,
                    "last_active_at" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateEmail(uid: String, email: String): Result<Unit> {
        return try {
            usersCollection.document(uid).update(
                mapOf(
                    "email" to email,
                    "last_active_at" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateSettings(uid: String, settings: UserSettings): Result<Unit> {
        return try {
            usersCollection.document(uid).update(
                mapOf(
                    "settings" to mapOf(
                        "theme" to settings.theme,
                        "unit" to settings.unit
                    ),
                    "last_active_at" to System.currentTimeMillis()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun incrementMeasurementCount(uid: String, mpsas: Double): Result<Unit> {
        return try {
            val doc = usersCollection.document(uid).get().await()
            val currentCount = (doc.getLong("total_measurements") ?: 0) + 1
            val currentBest = doc.getDouble("best_mpsas")

            val updates = mutableMapOf<String, Any?>(
                "total_measurements" to currentCount,
                "last_active_at" to System.currentTimeMillis()
            )

            if (currentBest == null || mpsas > currentBest) {
                updates["best_mpsas"] = mpsas
            }

            usersCollection.document(uid).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getUserSessions(uid: String): Flow<List<Session>> = getSessionIdsByUser(uid)
        .flatMapLatest { sessionIds ->
            flow {
                val sessions = sessionIds.mapNotNull { sid ->
                    firestoreManager.getSessionById(sid).first()
                }
                emit(sessions.sortedByDescending { it.date })
            }
        }

    private fun getSessionIdsByUser(uid: String): Flow<List<String>> = callbackFlow {
        val listener = measurementsCollection
            .whereEqualTo("device_id", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(AppConstants.FIRESTORE_QUERY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val sessionIds = snapshot?.documents
                    ?.mapNotNull { doc -> doc.getString("session_id") }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    ?: emptyList()
                trySend(sessionIds)
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
            bestMpsas = data["best_mpsas"] as? Double,
            createdAt = (data["created_at"] as? Number)?.toLong() ?: 0L,
            lastActiveAt = (data["last_active_at"] as? Number)?.toLong() ?: 0L,
            settings = UserSettings(
                theme = settingsMap?.get("theme") as? String ?: "auto",
                unit = settingsMap?.get("unit") as? String ?: "mpsas"
            )
        )
    }
}
