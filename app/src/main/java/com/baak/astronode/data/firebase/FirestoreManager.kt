package com.baak.astronode.data.firebase

import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.core.model.SkyMeasurement
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreManager @Inject constructor() {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val collection get() =
        db.collection(AppConstants.FIRESTORE_COLLECTION_MEASUREMENTS)

    suspend fun saveMeasurement(measurement: SkyMeasurement): Result<String> = try {
        val data = hashMapOf(
            "sqm_value" to measurement.sqmValue,
            "bortle_class" to measurement.bortleClass,
            "location" to GeoPoint(measurement.latitude, measurement.longitude),
            "altitude" to measurement.altitude,
            "orientation" to hashMapOf(
                "azimuth" to measurement.azimuth,
                "pitch" to measurement.pitch,
                "roll" to measurement.roll,
                "enabled" to measurement.orientationEnabled
            ),
            "timestamp" to Timestamp.now(),
            "device_id" to measurement.deviceId,
            "note" to measurement.note
        )

        val docRef = collection.add(data).await()
        Result.success(docRef.id)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun getMeasurements(): Flow<List<SkyMeasurement>> = callbackFlow {
        val registration = collection
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(AppConstants.FIRESTORE_QUERY_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
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
            note = data["note"] as? String
        )
    }
}
