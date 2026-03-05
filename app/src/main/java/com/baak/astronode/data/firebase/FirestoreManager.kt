package com.baak.astronode.data.firebase

import android.util.Log
import com.baak.astronode.core.constants.AppConstants
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

    suspend fun createSession(session: Session): Result<String> = try {
        val data = hashMapOf(
            "name" to session.name,
            "description" to session.description,
            "date" to session.date,
            "organizer_name" to session.organizerName,
            "participant_count" to session.participantCount,
            "created_by" to session.createdBy,
            "is_Active" to session.isActive
        )
        sessionsCollection.document(session.id).set(data)
        Log.d("FIRESTORE", "Session oluşturuldu (cache): ${session.id}")
        Result.success(session.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

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
                    } ?: emptyList()
                    trySend(sessions)
                }
        } catch (e: Exception) {
            Log.e("FIRESTORE", "Session query error: ${e.message}")
            trySend(emptyList())
        }
        awaitClose { registration?.remove() }
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

    suspend fun endSession(sessionId: String): Result<Unit> = try {
        sessionsCollection.document(sessionId).update("is_Active", false)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun documentToSession(docId: String, data: Map<String, Any>?): Session? {
        data ?: return null
        return Session(
            id = docId,
            name = data["name"] as? String ?: return null,
            description = data["description"] as? String,
            date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            organizerName = data["organizer_name"] as? String ?: "Baak Bilim Kulübü",
            participantCount = (data["participant_count"] as? Number)?.toInt(),
            createdBy = data["created_by"] as? String ?: "",
            isActive = data["is_Active"] as? Boolean ?: true
        )
    }

    suspend fun saveMeasurement(measurement: SkyMeasurement): Result<String> = try {
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
            "session_name" to measurement.sessionName
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
            deviceId = data["device_id"] as? String ?: "",
            note = data["note"] as? String,
            sessionId = data["session_id"] as? String,
            sessionName = data["session_name"] as? String,
            geohash = data["geohash"] as? String
        )
    }
}
