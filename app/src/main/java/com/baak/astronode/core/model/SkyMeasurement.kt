package com.baak.astronode.core.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import java.util.UUID

/**
 * Ana ölçüm veri sınıfı — MASTERPLAN Bölüm 3.1
 */
data class SkyMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val sqmValue: Double,               // MPSAS (örn: 21.5)
    val bortleClass: Int,               // 1-9 (sqmValue'dan hesaplanır)
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,              // GPS'ten opsiyonel
    val azimuth: Float?,                // 0-360° (kuzeyden saat yönü)
    val pitch: Float?,                  // -90 (aşağı) ile +90 (yukarı)
    val roll: Float?,                   // -180 ile +180
    val orientationEnabled: Boolean,    // Kullanıcı toggle'ı açtı mı?
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,               // Firebase anonymous UID
    val note: String? = null            // Opsiyonel kullanıcı notu
) {
    /**
     * Firestore'a yazmak için Map'e çevirir — MASTERPLAN Bölüm 3.2
     */
    fun toFirestoreMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "sqm_value" to sqmValue,
            "bortle_class" to bortleClass,
            "location" to GeoPoint(latitude, longitude),
            "device_id" to deviceId,
            "timestamp" to FieldValue.serverTimestamp()
        )
        altitude?.let { map["altitude"] = it }
        map["orientation"] = mapOf(
            "azimuth" to (azimuth ?: 0.0),
            "pitch" to (pitch ?: 0.0),
            "roll" to (roll ?: 0.0),
            "enabled" to orientationEnabled
        )
        note?.let { map["note"] = it }
        return map
    }

    companion object {
        /**
         * Firestore belgesinden SkyMeasurement oluşturur — MASTERPLAN Bölüm 3.2
         */
        fun fromFirestoreDoc(doc: DocumentSnapshot): SkyMeasurement? {
            val id = doc.id
            val sqmValue = (doc.get("sqm_value") as? Number)?.toDouble() ?: return null
            val bortleClass = (doc.get("bortle_class") as? Number)?.toInt() ?: return null
            val location = doc.getGeoPoint("location") ?: return null
            val deviceId = doc.getString("device_id") ?: return null

            val altitude = (doc.get("altitude") as? Number)?.toDouble()
            val orientation = doc.get("orientation") as? Map<*, *>
            val orientationEnabled = (orientation?.get("enabled") as? Boolean) ?: false
            val azimuth = if (orientationEnabled) (orientation?.get("azimuth") as? Number)?.toFloat() else null
            val pitch = if (orientationEnabled) (orientation?.get("pitch") as? Number)?.toFloat() else null
            val roll = if (orientationEnabled) (orientation?.get("roll") as? Number)?.toFloat() else null

            val timestamp = when (val ts = doc.get("timestamp")) {
                is Timestamp -> ts.toDate().time
                else -> System.currentTimeMillis()
            }
            val note = doc.getString("note")

            return SkyMeasurement(
                id = id,
                sqmValue = sqmValue,
                bortleClass = bortleClass,
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = altitude,
                azimuth = azimuth,
                pitch = pitch,
                roll = roll,
                orientationEnabled = orientationEnabled,
                timestamp = timestamp,
                deviceId = deviceId,
                note = note
            )
        }
    }
}
