package com.baak.astronode.data.firebase

import android.util.Log
import com.baak.astronode.BuildConfig
import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.core.model.Session
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.model.WeatherData
import com.baak.astronode.core.util.GeoHashUtil
import com.baak.astronode.core.util.MeasurementValidator
import com.baak.astronode.core.util.NetworkMonitor
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Firestore erişim katmanı. Tüm yetki kontrolleri firebase/firestore.rules içindedir;
 * buradaki kontroller sadece kullanıcıya anlaşılır hata mesajı vermek içindir.
 */
@Singleton
class FirestoreManager @Inject constructor(
    private val networkMonitor: NetworkMonitor
) {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    /** Sunucuya henüz ulaşmamış yazma sayısı (her yazma tamamlanınca azalır). */
    private val pendingWrites = AtomicInteger(0)

    private val measurementsCollection get() =
        db.collection(AppConstants.FIRESTORE_COLLECTION_MEASUREMENTS)

    private val sessionsCollection get() =
        db.collection(AppConstants.FIRESTORE_COLLECTION_SESSIONS)

    private val sessionCodesCollection get() =
        db.collection(AppConstants.FIRESTORE_COLLECTION_SESSION_CODES)

    private val usersCollection get() =
        db.collection(AppConstants.FIRESTORE_COLLECTION_USERS)

    private fun trackPendingWrite(task: com.google.android.gms.tasks.Task<Void>, label: String) {
        networkMonitor.updatePendingCount(pendingWrites.incrementAndGet())
        task.addOnCompleteListener { t ->
            networkMonitor.updatePendingCount(pendingWrites.decrementAndGet().coerceAtLeast(0))
            if (!t.isSuccessful) Log.e(TAG, "$label sunucuda reddedildi: ${t.exception?.message}")
        }
    }

    // ======================================================================
    // Sessions
    // ======================================================================

    fun generateSessionCode(): String =
        (1..AppConstants.SESSION_CODE_LENGTH)
            .map { AppConstants.SESSION_CODE_CHARS[Random.nextInt(AppConstants.SESSION_CODE_CHARS.length)] }
            .joinToString("")

    /** Online ise kodun boş olduğunu doğrular; offline ise (çakışma olasılığı ~1/10^9) kabul eder. */
    private suspend fun pickUnusedCode(): String {
        repeat(AppConstants.SESSION_CODE_MAX_ATTEMPTS) {
            val code = generateSessionCode()
            if (!networkMonitor.isOnline.value) return code
            val taken = withTimeoutOrNull(AppConstants.SESSION_CODE_CHECK_TIMEOUT_MS) {
                runCatching { sessionCodesCollection.document(code).get(Source.SERVER).await().exists() }
                    .getOrDefault(false)
            } ?: false
            if (!taken) return code
        }
        return generateSessionCode()
    }

    /** Etkinlik ve kod belgesi tek batch ile yazılır (kurallar ikisini birlikte ister). Offline çalışır. */
    suspend fun createSession(session: Session): Result<Session> = try {
        val uid = session.createdBy
        require(uid.isNotBlank()) { "Oturum açılmamış" }
        val code = pickUnusedCode()
        val created = session.copy(
            name = session.name.trim().take(AppConstants.SESSION_NAME_MAX),
            description = session.description?.trim()?.take(AppConstants.SESSION_DESCRIPTION_MAX)
                ?.takeIf { it.isNotBlank() },
            organizerName = session.organizerName.take(AppConstants.ORGANIZER_NAME_MAX),
            code = code,
            participantIds = listOf(uid),
            participantCount = 1,
            isActive = true,
            status = "active"
        )
        val data = hashMapOf(
            "name" to created.name,
            "description" to created.description,
            "date" to created.date,
            "organizer_name" to created.organizerName,
            "participant_count" to 1,
            "created_by" to uid,
            "is_Active" to true,
            "status" to "active",
            "type" to created.type,
            "participant_ids" to listOf(uid),
            "code" to code
        )
        val batch = db.batch()
        batch.set(sessionsCollection.document(created.id), data)
        batch.set(sessionCodesCollection.document(code), mapOf("session_id" to created.id, "created_by" to uid))
        trackPendingWrite(batch.commit(), "Etkinlik ${created.id}")
        Log.d(TAG, "Etkinlik oluşturuldu: ${created.id}, kod: $code")
        Result.success(created)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Herkese açık aktif etkinlikler (sorgu kurallarla uyumlu olmalı: type == public). */
    fun getActiveSessions(): Flow<List<Session>> = callbackFlow {
        val registration = sessionsCollection
            .whereEqualTo("type", "public")
            .whereEqualTo("is_Active", true)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(AppConstants.PUBLIC_SESSIONS_LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Public session sorgu hatası: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { documentToSession(it.id, it.data) } ?: emptyList())
            }
        awaitClose { registration.remove() }
    }

    /** Oluşturduğum veya katıldığım aktif etkinlikler (tüm tipler). */
    fun getMyActiveSessions(uid: String): Flow<List<Session>> = callbackFlow {
        val created = mutableMapOf<String, Session>()
        val joined = mutableMapOf<String, Session>()
        fun sendMerged() {
            trySend((created + joined).values.sortedByDescending { it.date })
        }
        val reg1 = sessionsCollection
            .whereEqualTo("created_by", uid)
            .whereEqualTo("is_Active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getMyActiveSessions (created_by): ${error.message}")
                    return@addSnapshotListener
                }
                created.clear()
                snapshot?.documents?.forEach { doc -> documentToSession(doc.id, doc.data)?.let { created[doc.id] = it } }
                sendMerged()
            }
        val reg2 = sessionsCollection
            .whereArrayContains("participant_ids", uid)
            .whereEqualTo("is_Active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "getMyActiveSessions (participant_ids): ${error.message}")
                    return@addSnapshotListener
                }
                joined.clear()
                snapshot?.documents?.forEach { doc -> documentToSession(doc.id, doc.data)?.let { joined[doc.id] = it } }
                sendMerged()
            }
        awaitClose { reg1.remove(); reg2.remove() }
    }

    fun getSessionById(id: String): Flow<Session?> = callbackFlow {
        val registration = sessionsCollection.document(id)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Session belge hatası: ${error.message}")
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.let { documentToSession(it.id, it.data) })
            }
        awaitClose { registration.remove() }
    }

    suspend fun getSessionOnce(id: String): Session? = try {
        val doc = sessionsCollection.document(id).get().await()
        documentToSession(doc.id, doc.data)
    } catch (e: Exception) {
        Log.e(TAG, "getSessionOnce: ${e.message}")
        null
    }

    fun endSession(sessionId: String): Result<Unit> = updateSessionStatus(sessionId, "completed")

    fun cancelSession(sessionId: String): Result<Unit> = updateSessionStatus(sessionId, "cancelled")

    private fun updateSessionStatus(sessionId: String, status: String): Result<Unit> = try {
        trackPendingWrite(
            sessionsCollection.document(sessionId).update(mapOf("is_Active" to false, "status" to status)),
            "Etkinlik durumu $sessionId"
        )
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun getMeasurementCountBySession(sessionId: String): Int = try {
        measurementsCollection
            .whereEqualTo("session_id", sessionId)
            .count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER)
            .await()
            .count
            .toInt()
    } catch (e: Exception) {
        Log.e(TAG, "Ölçüm sayısı alınamadı: ${e.message}")
        -1
    }

    /** Etkinliği ve kod belgesini birlikte siler. */
    suspend fun deleteSession(session: Session): Result<Unit> = try {
        val batch = db.batch()
        batch.delete(sessionsCollection.document(session.id))
        if (session.code.isNotBlank()) batch.delete(sessionCodesCollection.document(session.code))
        batch.commit().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Kod ile etkinlik bulur (session_codes üzerinden; kodlar listelenemez). */
    suspend fun getSessionByCode(code: String): Session? {
        val normalized = code.trim().uppercase()
        if (normalized.length != AppConstants.SESSION_CODE_LENGTH) return null
        return try {
            val codeDoc = sessionCodesCollection.document(normalized).get().await()
            val sessionId = codeDoc.getString("session_id") ?: return null
            getSessionOnce(sessionId)?.takeIf { it.isActive }
        } catch (e: Exception) {
            Log.e(TAG, "getSessionByCode: ${e.message}")
            null
        }
    }

    /** Katılım transaction ile yapılır: eşzamanlı katılımlarda kimse listeden düşmez. Online gerekir. */
    suspend fun joinSessionByCode(code: String, uid: String): Result<Session> {
        if (!networkMonitor.isOnline.value) {
            return Result.failure(Exception("Etkinliğe katılmak için internet bağlantısı gerekir"))
        }
        val session = getSessionByCode(code)
            ?: return Result.failure(Exception("Geçersiz veya süresi dolmuş kod"))
        if (session.type == "invite_only") {
            return Result.failure(Exception("Bu etkinlik sadece davetliler içindir"))
        }
        return try {
            val ref = sessionsCollection.document(session.id)
            val ids = db.runTransaction { tx ->
                val snap = tx.get(ref)
                val current = snap.stringList("participant_ids")
                if (uid in current) return@runTransaction current
                val updated = current + uid
                tx.update(ref, mapOf("participant_ids" to updated, "participant_count" to updated.size))
                updated
            }.await()
            Result.success(session.copy(participantIds = ids, participantCount = ids.size))
        } catch (e: Exception) {
            Result.failure(Exception("Katılım başarısız: ${e.message}", e))
        }
    }

    suspend fun leaveSession(sessionId: String, uid: String): Result<Unit> = try {
        val ref = sessionsCollection.document(sessionId)
        db.runTransaction { tx ->
            val snap = tx.get(ref)
            if (!snap.exists()) throw IllegalStateException("Etkinlik bulunamadı")
            if (snap.getString("created_by") == uid) {
                throw IllegalStateException("Organizatör etkinlikten ayrılamaz; etkinliği bitirebilirsiniz")
            }
            val current = snap.stringList("participant_ids")
            if (uid !in current) return@runTransaction
            val updated = current - uid
            tx.update(ref, mapOf("participant_ids" to updated, "participant_count" to updated.size))
        }.await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception(e.message ?: "Ayrılma başarısız", e))
    }

    // ======================================================================
    // Measurements
    // ======================================================================

    /**
     * Ölçümü kaydeder. await KULLANILMAZ: offline'da cache'e anında yazılır, bağlantı gelince senkronize olur.
     * Zaman damgası ölçüm anıdır (offline birikse bile doğru kalır); created_at sunucu zamanıdır.
     */
    fun saveMeasurement(measurement: SkyMeasurement): Result<String> = try {
        val validation = MeasurementValidator.validate(measurement.sqmValue)
        if (validation == MeasurementValidator.Result.INVALID) {
            throw IllegalArgumentException("Geçersiz SQM değeri: ${measurement.sqmValue}")
        }
        val isTest = measurement.isTest || validation == MeasurementValidator.Result.OUT_OF_NIGHT_RANGE

        val data = hashMapOf<String, Any>(
            "sqm_value" to measurement.sqmValue,
            "bortle_class" to measurement.bortleClass.coerceIn(1, 9),
            "location" to GeoPoint(measurement.latitude, measurement.longitude),
            "geohash" to GeoHashUtil.encode(measurement.latitude, measurement.longitude),
            "orientation" to buildMap {
                measurement.azimuth?.let { put("azimuth", it) }
                measurement.pitch?.let { put("pitch", it) }
                measurement.roll?.let { put("roll", it) }
                put("enabled", measurement.orientationEnabled)
            },
            "timestamp" to Timestamp(Date(measurement.timestamp)),
            "created_at" to FieldValue.serverTimestamp(),
            "device_id" to measurement.observerUid,
            "observer_uid" to measurement.observerUid,
            "observer_name" to measurement.observerName.take(AppConstants.DISPLAY_NAME_MAX),
            "is_test" to isTest,
            "app_version" to BuildConfig.VERSION_NAME
        )
        measurement.altitude?.let { data["altitude"] = it }
        measurement.sqmTemperature?.let { data["sqm_temperature"] = it }
        measurement.note?.takeIf { it.isNotBlank() }?.let { data["note"] = it.take(AppConstants.NOTE_MAX) }
        measurement.sessionId?.let { data["session_id"] = it }
        measurement.sessionName?.let { data["session_name"] = it.take(AppConstants.SESSION_NAME_MAX) }

        val weather = buildMap {
            (measurement.temperature ?: measurement.weather?.temperature)?.let { put("temperature", it) }
            (measurement.humidity ?: measurement.weather?.humidity)?.let { put("humidity", it) }
            (measurement.cloudCover ?: measurement.weather?.cloudCover)?.let { put("cloud_cover", it) }
            (measurement.windSpeed ?: measurement.weather?.windSpeed)?.let { put("wind_speed", it) }
            (measurement.visibility ?: measurement.weather?.visibility)?.let { put("visibility", it) }
        }
        if (weather.isNotEmpty()) data["weather"] = weather

        val moon = buildMap {
            measurement.moonPhase?.let { put("phase", it) }
            measurement.moonIllumination?.let { put("illumination", it) }
            measurement.moonEmoji?.let { put("emoji", it) }
        }
        if (moon.isNotEmpty()) data["moon"] = moon

        data["observing"] = buildMap {
            measurement.observingScore?.let { put("score", it) }
            measurement.observingRating?.let { put("rating", it) }
            put("is_daytime", measurement.isDaytime)
            measurement.measurementTime?.let { put("time", it) }
        }

        val docRef = measurementsCollection.document()
        trackPendingWrite(docRef.set(data), "Ölçüm ${docRef.id}")
        Log.d(TAG, "Ölçüm kaydedildi (cache): ${docRef.id}")

        // Profil sayacı (ayrı yazma: başarısız olsa bile ölçüm etkilenmez)
        incrementUserStats(measurement.observerUid, measurement.sqmValue, countsForBest = !isTest)

        Result.success(docRef.id)
    } catch (e: Exception) {
        Log.e(TAG, "Kayıt hatası: ${e.message}")
        Result.failure(e)
    }

    private fun incrementUserStats(uid: String, mpsas: Double, countsForBest: Boolean) {
        val ref = usersCollection.document(uid)
        // En iyi değer için cache'teki profile bakılır (ağ gerekmez)
        ref.get(Source.CACHE).addOnCompleteListener { task ->
            val doc = task.result?.takeIf { task.isSuccessful && it.exists() }
                ?: return@addOnCompleteListener // Profil yoksa sayaç yazılmaz (kural create'i reddeder)
            val updates = mutableMapOf<String, Any>(
                "total_measurements" to FieldValue.increment(1),
                "last_active_at" to System.currentTimeMillis()
            )
            val best = doc.getDouble("best_mpsas")
            if (countsForBest && (best == null || mpsas > best)) updates["best_mpsas"] = mpsas
            trackPendingWrite(ref.update(updates), "Profil sayacı")
        }
    }

    /** Kendi ölçümlerim (Geçmiş, Analiz, CSV). */
    fun getMyMeasurements(uid: String): Flow<List<SkyMeasurement>> = queryFlow(
        measurementsCollection
            .whereEqualTo("observer_uid", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(AppConstants.FIRESTORE_QUERY_LIMIT),
        "Kendi ölçümlerim"
    )

    fun getMeasurementsBySession(sessionId: String): Flow<List<SkyMeasurement>> = queryFlow(
        measurementsCollection
            .whereEqualTo("session_id", sessionId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(AppConstants.FIRESTORE_QUERY_LIMIT),
        "Etkinlik ölçümleri"
    )

    private fun queryFlow(query: Query, label: String): Flow<List<SkyMeasurement>> = callbackFlow {
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "$label hatası: ${error.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents?.mapNotNull { documentToMeasurement(it.id, it.data) } ?: emptyList())
        }
        awaitClose { registration.remove() }
    }

    /** Harita: geohash aralıklarıyla bölge sorgusu (her aralık ve toplam sonuç sınırlı). */
    fun getMeasurementsInRadius(
        centerLat: Double,
        centerLng: Double,
        radiusKm: Double
    ): Flow<List<SkyMeasurement>> = flow {
        val center = GeoLocation(centerLat, centerLng)
        val radiusInM = radiusKm * 1000.0
        val bounds = GeoFireUtils.getGeoHashQueryBounds(center, radiusInM)

        val results = LinkedHashMap<String, SkyMeasurement>()
        for (bound in bounds) {
            if (results.size >= AppConstants.MAP_MAX_RESULTS) break
            val snapshot = withTimeout(MAP_QUERY_TIMEOUT_MS) {
                measurementsCollection
                    .orderBy("geohash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash)
                    .limit(AppConstants.MAP_QUERY_LIMIT_PER_BOUND)
                    .get()
                    .await()
            }
            for (doc in snapshot.documents) {
                val m = documentToMeasurement(doc.id, doc.data) ?: continue
                val distanceM = GeoFireUtils.getDistanceBetween(GeoLocation(m.latitude, m.longitude), center)
                if (distanceM <= radiusInM) results[m.id] = m
            }
        }
        emit(results.values.toList())
    }

    /** Sadece kendi ölçümünü siler; yetki kuralda da kontrol edilir. */
    suspend fun deleteMeasurement(measurementId: String, currentUid: String): Result<Unit> = try {
        val doc = measurementsCollection.document(measurementId).get().await()
        if (!doc.exists()) throw IllegalStateException("Ölçüm bulunamadı")
        val owner = doc.getString("observer_uid") ?: doc.getString("device_id")
        if (owner != currentUid) throw IllegalStateException("Sadece kendi ölçümünüzü silebilirsiniz")
        doc.reference.delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ======================================================================
    // Hesap silme
    // ======================================================================

    /**
     * Kullanıcının tüm verilerini siler: ölçümler, oluşturduğu etkinlikler (+kodlar),
     * katıldığı etkinliklerden çıkış ve profil belgesi. Online gerekir.
     */
    suspend fun deleteAllUserData(uid: String): Result<Unit> = try {
        // 1) Ölçümler (eski kayıtlarda sadece device_id olabilir)
        for (field in listOf("observer_uid", "device_id")) {
            while (true) {
                val snap = measurementsCollection.whereEqualTo(field, uid)
                    .limit(AppConstants.FIRESTORE_BATCH_LIMIT.toLong()).get(Source.SERVER).await()
                if (snap.isEmpty) break
                val batch = db.batch()
                snap.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }
        }
        // 2) Katıldığım etkinliklerden ayrıl
        sessionsCollection.whereArrayContains("participant_ids", uid).get(Source.SERVER).await()
            .documents.filter { it.getString("created_by") != uid }
            .forEach { leaveSession(it.id, uid) }
        // 3) Oluşturduğum etkinlikler ve kodları
        sessionsCollection.whereEqualTo("created_by", uid).get(Source.SERVER).await()
            .documents.mapNotNull { documentToSession(it.id, it.data) }
            .forEach { deleteSession(it).getOrThrow() }
        // 4) Profil
        usersCollection.document(uid).delete().await()
        Result.success(Unit)
    } catch (e: Exception) {
        Log.e(TAG, "Hesap verisi silinemedi: ${e.message}")
        Result.failure(e)
    }

    // ======================================================================
    // Mapping
    // ======================================================================

    private fun DocumentSnapshot.stringList(field: String): List<String> =
        (get(field) as? List<*>)?.mapNotNull { it as? String } ?: emptyList()

    private fun documentToSession(docId: String, data: Map<String, Any>?): Session? {
        data ?: return null
        val participantIds = (data["participant_ids"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        return Session(
            id = docId,
            name = data["name"] as? String ?: return null,
            description = data["description"] as? String,
            date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            organizerName = data["organizer_name"] as? String ?: "",
            participantCount = (data["participant_count"] as? Number)?.toInt(),
            createdBy = data["created_by"] as? String ?: "",
            isActive = data["is_Active"] as? Boolean ?: true,
            status = data["status"] as? String ?: "active",
            type = data["type"] as? String ?: "public",
            participantIds = participantIds,
            code = data["code"] as? String ?: ""
        )
    }

    private fun documentToMeasurement(docId: String, data: Map<String, Any>?): SkyMeasurement? {
        data ?: return null
        val location = data["location"] as? GeoPoint ?: return null
        val orientation = data["orientation"] as? Map<*, *>
        val ts = data["timestamp"] as? Timestamp

        val deviceId = data["device_id"] as? String ?: ""
        val observerUid = data["observer_uid"] as? String ?: deviceId

        val weatherMap = data["weather"] as? Map<*, *>
        val moonMap = data["moon"] as? Map<*, *>
        val observingMap = data["observing"] as? Map<*, *>

        val temperature = (weatherMap?.get("temperature") as? Number)?.toDouble()
        val humidity = (weatherMap?.get("humidity") as? Number)?.toInt()
        val cloudCover = (weatherMap?.get("cloud_cover") as? Number)?.toInt()
        val windSpeed = (weatherMap?.get("wind_speed") as? Number)?.toDouble()
        val visibility = (weatherMap?.get("visibility") as? Number)?.toDouble()
        val weather = if (listOf(temperature, humidity, cloudCover, windSpeed, visibility).any { it != null }) {
            WeatherData(temperature, humidity, cloudCover, windSpeed, visibility)
        } else null

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
            observerName = data["observer_name"] as? String ?: "",
            weather = weather,
            temperature = temperature,
            humidity = humidity,
            cloudCover = cloudCover,
            windSpeed = windSpeed,
            visibility = visibility,
            moonPhase = (moonMap?.get("phase") as? String) ?: (data["moon_phase"] as? String),
            moonIllumination = (moonMap?.get("illumination") as? Number)?.toInt()
                ?: (data["moon_illumination"] as? Number)?.toInt(),
            moonEmoji = moonMap?.get("emoji") as? String,
            observingScore = (observingMap?.get("score") as? Number)?.toInt(),
            observingRating = observingMap?.get("rating") as? String,
            isDaytime = (observingMap?.get("is_daytime") as? Boolean) ?: (data["is_daytime"] as? Boolean) ?: false,
            measurementTime = (observingMap?.get("time") as? String) ?: (data["measurement_time"] as? String),
            sqmTemperature = (data["sqm_temperature"] as? Number)?.toDouble()
        )
    }

    private companion object {
        const val TAG = "FIRESTORE"
        const val MAP_QUERY_TIMEOUT_MS = 8_000L
    }
}
