package com.baak.astronode.data.firebase

import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.core.model.SkyMeasurement
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Firestore CRUD operasyonları — MASTERPLAN T4.2
 */
@Singleton
class FirestoreManager @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    private val measurementsRef = firestore.collection(AppConstants.FIRESTORE_COLLECTION)

    /**
     * Ölçümü Firestore'a kaydeder.
     * @return Başarılıysa belge ID, hata durumunda Result.failure
     */
    suspend fun saveMeasurement(measurement: SkyMeasurement): Result<String> = runCatching {
        val data = measurement.toFirestoreMap()
        val docRef = measurementsRef.document()
        docRef.set(data).await()
        docRef.id
    }

    /**
     * Son 500 ölçümü gerçek zamanlı dinler (timestamp'e göre azalan sıra).
     */
    fun getMeasurements(): Flow<List<SkyMeasurement>> = callbackFlow {
        val listener = measurementsRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    SkyMeasurement.fromFirestoreDoc(doc)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Verilen sınırlar içindeki ölçümleri gerçek zamanlı dinler.
     * (Firestore geo query sınırlaması nedeniyle son 500'den filtre uygulanır)
     */
    fun getMeasurementsInBounds(
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double
    ): Flow<List<SkyMeasurement>> = callbackFlow {
        val listener = measurementsRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(500)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents
                    ?.mapNotNull { SkyMeasurement.fromFirestoreDoc(it) }
                    ?.filter { m ->
                        m.latitude in swLat..neLat && m.longitude in swLng..neLng
                    } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }
}
