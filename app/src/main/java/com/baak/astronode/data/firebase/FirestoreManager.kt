package com.baak.astronode.data.firebase

import android.util.Log
import com.baak.astronode.core.constants.AppConstants
import kotlin.random.Random
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.GeoHashUtil
import com.baak.astronode.core.util.NetworkMonitor
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreManager @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Bekleyen yazma sayısı — saveMeasurement'ta artırılır, senkron sonrası sıfırlanır */
    private var pendingWriteCount = 0

    init {
        db.addSnapshotsInSyncListener {
            pendingWriteCount = 0
            networkMonitor.updatePendingCount(0)
        }
    }

    private val measurementsCollection get() =
        db.collection(AppConstants.FIRESTORE_COLLECTION_MEASUREMENTS)

    private val sessionsCollection get() =
        db.collection(AppConstants.FIRESTORE_COLLECTION_SESSIONS)

    private val collection get() = measurementsCollection

    suspend fun createSession(session: Session): Result<Session> { return try {
        val participantIds = if (session.participantIds.isEmpty() && session.createdBy.isNotBlank()) {
            listOf(session.createdBy)
        } else session.participantIds
        val codeToUse = session.code.ifBlank { generateSessionCode() }
        val data = hashMapOf(
            "name" to session.name,
            "description" to session.description,
            "date" to session.date,
            "organizer_name" to session.organizerName,
            "participant_count" to (participantIds.size).takeIf { participantIds.isNotEmpty() },
            "created_by" to session.createdBy,
            "is_Active" to session.isActive,
            "status" to session.status,
            "type" to session.type,
            "participant_ids" to participantIds,
            "code" to codeToUse
        )
        sessionsCollection.document(session.id).set(data)
        Log.d("FIRESTORE", "Session oluşturuldu (cache): ${session.id}, code: $codeToUse")
        val createdSession = session.copy(
            code = codeToUse,
            participantIds = participantIds
        )
        Result.success(createdSession)
    } catch (e: Exception) {
        Result.failure(e)
    }
    }

    fun generateSessionCode(): String =
        (1..AppConstants.SESSION_CODE_LENGTH)
            .map { AppConstants.SESSION_CODE_CHARS[Random.nextInt(AppConstants.SESSION_CODE_CHARS.length)] }
            .joinToString("")

    /** Sadece type == "public" olan session'ları döndür (herkese açık liste) */
    fun getActiveSessions(): Flow<List<Session>> = callbackFlow {
        var registration: ListenerRegistration? = null
        try {
            registration = sessionsCollection
                .whereEqualTo("is_Active", true)
                .orderBy("date", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("FIRESTORE", "Session query error: ${error.message}")
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    val sessions = snapshot?.documents?.mapNotNull { doc ->
                        documentToSession(doc.id, doc.data)
                    }?.filter { it.type == "public" || it.type.isEmpty() } ?: emptyList()
                    trySend(sessions)
                }
        } catch (e: Exception) {
            Log.e("FIRESTORE", "Session query error: ${e.message}")
            trySend(emptyList())
        }
        awaitClose { registration?.remove() }
    }

    /** createdBy == uid VEYA participantIds contains uid olan session'lar (tüm tipler) */
    fun getMyActiveSessions(uid: String): Flow<List<Session>> = callbackFlow {
        val sessionsMap = mutableMapOf<String, Session>()
        fun sendMerged() {
            trySend(sessionsMap.values.sortedByDescending { it.date })
        }
        val reg1 = sessionsCollection
            .whereEqualTo("created_by", uid)
            .whereEqualTo("is_Active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE", "getMyActiveSessions (created_by) error: ${error.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    documentToSession(doc.id, doc.data)?.let { sessionsMap[doc.id] = it }
                }
                sendMerged()
            }
        val reg2 = sessionsCollection
            .whereArrayContains("participant_ids", uid)
            .whereEqualTo("is_Active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE", "getMyActiveSessions (participant_ids) error: ${error.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    documentToSession(doc.id, doc.data)?.let { sessionsMap[doc.id] = it }
                }
                sendMerged()
            }
        awaitClose { reg1.remove(); reg2.remove() }
    }

    fun getSessionById(id: String): Flow<Session?> = callbackFlow {
        val registration = sessionsCollection.document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE", "Session doc error: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }
                val session = snapshot?.let { doc ->
                    documentToSession(doc.id, doc.data)
                }
                trySend(session)
            }
        awaitClose { registration.remove() }
    }

    suspend fun endSession(sessionId: String): Result<Unit> { return try {
        sessionsCollection.document(sessionId).update(
            mapOf("is_Active" to false, "status" to "completed")
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
    }

    suspend fun cancelSession(sessionId: String): Result<Unit> { return try {
        sessionsCollection.document(sessionId).update(
            mapOf("is_Active" to false, "status" to "cancelled")
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
    }

    suspend fun getMeasurementCountBySession(sessionId: String): Int =
        measurementsCollection
            .whereEqualTo("session_id", sessionId)
            .get()
            .await()
            .size()

    suspend fun deleteSession(sessionId: String): Result<Unit> { return try {
        sessionsCollection.document(sessionId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
    }

    suspend fun deleteMeasurement(measurementId: String, currentUid: String): Result<Unit> {
        return try {
            val doc = measurementsCollection.document(measurementId).get().await()
            if (!doc.exists()) {
                return Result.failure(Exception("Ölçüm bulunamadı"))
            }
            val data = doc.data ?: emptyMap()
            val ownerUid = data["observer_uid"] as? String ?: data["device_id"] as? String ?: ""
            if (ownerUid != currentUid) {
                return Result.failure(Exception("Sadece kendi ölçümünüzü silebilirsiniz"))
            }
            measurementsCollection.document(measurementId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun documentToSession(docId: String, data: Map<String, Any>?): Session? {
        data ?: return null
        @Suppress("UNCHECKED_CAST")
        val participantIds = (data["participant_ids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return Session(
            id = docId,
            name = data["name"] as? String ?: return null,
            description = data["description"] as? String,
            date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            organizerName = data["organizer_name"] as? String ?: "Baak Bilim Kulübü",
            participantCount = (data["participant_count"] as? Number)?.toInt(),
            createdBy = data["created_by"] as? String ?: "",
            isActive = data["is_Active"] as? Boolean ?: true,
            status = data["status"] as? String ?: "active",
            type = data["type"] as? String ?: "public",
            participantIds = participantIds,
            code = data["code"] as? String ?: ""
        )
    }

    suspend fun getSessionByCode(code: String): Session? {
        val normalized = code.trim().uppercase()
        if (normalized.length != 6) return null
        return try {
            val snapshot = sessionsCollection
                .whereEqualTo("code", normalized)
                .whereEqualTo("is_Active", true)
                .limit(1)
                .get()
                .await()
            snapshot.documents.firstOrNull()?.let { doc ->
                documentToSession(doc.id, doc.data)
            }
        } catch (e: Exception) {
            Log.e("FIRESTORE", "getSessionByCode error: ${e.message}")
            null
        }
    }

    private suspend fun addParticipantToSession(sessionId: String, uid: String): Result<Unit> {
        return try {
            val doc = sessionsCollection.document(sessionId).get().await()
            if (!doc.exists()) return Result.failure(Exception("Etkinlik bulunamadı"))
            val data = doc.data ?: return Result.failure(Exception("Etkinlik verisi bulunamadı"))
            @Suppress("UNCHECKED_CAST")
            val currentIds = (data["participant_ids"] as? List<*>)?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()
            if (uid in currentIds) return Result.success(Unit)
            currentIds.add(uid)
            sessionsCollection.document(sessionId).update(
                mapOf(
                    "participant_ids" to currentIds,
                    "participant_count" to currentIds.size
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinSessionByCode(code: String, uid: String): Result<Session> {
        val session = getSessionByCode(code) ?: return Result.failure(Exception("Geçersiz veya süresi dolmuş kod"))
        return when (session.type) {
            "invite_only" -> Result.failure(Exception("Bu etkinlik sadece davetliler içindir"))
            "public", "private" -> {
                addParticipantToSession(session.id, uid).fold(
                    onSuccess = { Result.success(session) },
                    onFailure = { Result.failure(it) }
                )
            }
            else -> addParticipantToSession(session.id, uid).fold(
                onSuccess = { Result.success(session) },
                onFailure = { Result.failure(it) }
            )
        }
    }

    suspend fun leaveSession(sessionId: String, uid: String): Result<Unit> {
        return try {
            val doc = sessionsCollection.document(sessionId).get().await()
            if (!doc.exists()) return Result.failure(Exception("Etkinlik bulunamadı"))
            val data = doc.data ?: return Result.failure(Exception("Etkinlik verisi bulunamadı"))
            @Suppress("UNCHECKED_CAST")
            val currentIds = (data["participant_ids"] as? List<*>)?.mapNotNull { it as? String }?.toMutableList() ?: mutableListOf()
            currentIds.remove(uid)
            sessionsCollection.document(sessionId).update(
                mapOf(
                    "participant_ids" to currentIds,
                    "participant_count" to currentIds.size
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveMeasurement(measurement: SkyMeasurement): Result<String> { return try {
        val geohash = GeoHashUtil.encode(measurement.latitude, measurement.longitude)
        val data = hashMapOf(
            "sqm_value" to measurement.sqmValue,
            "bortle_class" to measurement.bortleClass,
            "location" to GeoPoint(measurement.latitude, measurement.longitude),
            "geohash" to geohash,
            "altitude" to measurement.altitude,
            "orientation" to hashMapOf(
                "azimuth" to measurement.azimuth,
                "pitch" to measurement.pitch,
                "roll" to measurement.roll,
                "enabled" to measurement.orientationEnabled
            ),
            "timestamp" to Timestamp.now(),
            "device_id" to measurement.deviceId,
            "note" to measurement.note,
            "session_id" to measurement.sessionId,
            "session_name" to measurement.sessionName,
            "is_test" to measurement.isTest,
            "observer_uid" to measurement.observerUid.ifBlank { measurement.deviceId },
            "observer_name" to measurement.observerName
        )

        val docRef = collection.document()
        docRef.set(data)  // KRITIK: await KULLANMA — offline'da asılır, cache'e anında yazar
        pendingWriteCount++
        networkMonitor.updatePendingCount(pendingWriteCount)
        Log.d("FIRESTORE", "Ölçüm kaydedildi (cache): ${docRef.id}")
        Result.success(docRef.id)
    } catch (e: Exception) {
        Log.e("FIRESTORE", "Kayıt hatası: ${e.message}")
        Result.failure(e)
    }
    }

    suspend fun getMeasurementsOnce(): List<SkyMeasurement> =
        getMeasurements().first()

    fun getMeasurements(): Flow<List<SkyMeasurement>> = callbackFlow {
        val registration = collection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(AppConstants.FIRESTORE_QUERY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE", "Listen error: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val source = if (snapshot.metadata.isFromCache) "CACHE" else "SERVER"
                    Log.d("FIRESTORE", "Veri kaynağı: $source, ${snapshot.size()} ölçüm")
                    val measurements = snapshot.documents.mapNotNull { doc ->
                        documentToMeasurement(doc.id, doc.data)
                    }
                    trySend(measurements)
                }
            }
        awaitClose { registration.remove() }
    }

    fun getMeasurementsInRadius(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double
    ): Flow<List<SkyMeasurement>> = flow {
        val center = GeoLocation(centerLat, centerLng)
        val radiusInM = radiusKm * 1000.0
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)

        val allDocs = mutableSetOf<SkyMeasurement>()
        for (bound in bounds) {
            val snapshot = withTimeout(5000L) {
                measurementsCollection
                    .orderBy("geohash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash)
                    .get()
                    .await()
            }

            for (doc in snapshot.documents) {
                val m = documentToMeasurement(doc.id, doc.data)
                if (m != null) {
                    val docLocation = GeoLocation(m.latitude, m.longitude)
                    val distanceM = GeoFireUtils.getDistanceBetween(docLocation, center)
                    if (distanceM <= radiusInM) {
                        allDocs.add(m)
                    }
                }
            }
        }
        emit(allDocs.toList())
    }

    fun getMeasurementsBySession(sessionId: String): Flow<List<SkyMeasurement>> = callbackFlow {
        val registration = measurementsCollection
            .whereEqualTo("session_id", sessionId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(AppConstants.FIRESTORE_QUERY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FIRESTORE", "Session measurements error: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val measurements = snapshot?.documents?.mapNotNull { doc ->
                    documentToMeasurement(doc.id, doc.data)
                } ?: emptyList()
                trySend(measurements)
            }
        awaitClose { registration.remove() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun documentToMeasurement(docId: String, data: Map<String, Any>?): SkyMeasurement? {
        data ?: return null
        val location = data["location"] as? GeoPoint ?: return null
        val orientation = data["orientation"] as? Map<String, Any>
        val ts = data["timestamp"] as? Timestamp

        val deviceId = data["device_id"] as? String ?: ""
        val observerUid = data["observer_uid"] as? String ?: deviceId
        return SkyMeasurement(
            id = docId,
            sqmValue = (data["sqm_value"] as? Number)?.toDouble() ?: return null,
            bortleClass = (data["bortle_class"] as? Number)?.toInt() ?: return null,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = (data["altitude"] as? Number)?.toDouble(),
            azimuth = (orientation?.get("azimuth") as? Number)?.toFloat(),
            pitch = (orientation?.get("pitch") as? Number)?.toFloat(),
            roll = (orientation?.get("roll") as? Number)?.toFloat(),
            orientationEnabled = orientation?.get("enabled") as? Boolean ?: false,
            timestamp = ts?.toDate()?.time ?: System.currentTimeMillis(),
            deviceId = deviceId,
            note = data["note"] as? String,
            sessionId = data["session_id"] as? String,
            sessionName = data["session_name"] as? String,
            geohash = data["geohash"] as? String,
            isTest = data["is_test"] as? Boolean ?: false,
            observerUid = observerUid,
            observerName = data["observer_name"] as? String ?: ""
        )
    }
}
