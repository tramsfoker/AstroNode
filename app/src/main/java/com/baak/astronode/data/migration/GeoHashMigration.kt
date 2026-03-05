package com.baak.astronode.data.migration

import com.baak.astronode.core.constants.AppConstants
import com.baak.astronode.core.util.GeoHashUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mevcut ölçümlere GeoHash ekleyen migration.
 * Uygulama açılışında bir kez çalışır, SharedPreferences ile tekrar çalışması engellenir.
 * Firestore'da "geohash yok" sorgusu olmadığı için tüm belgeleri sayfalayarak kontrol eder.
 */
@Singleton
class GeoHashMigration @Inject constructor(
    private val prefs: MigrationPreferences
) {

    suspend fun runIfNeeded() {
        if (prefs.isGeoHashMigrationDone()) return

        runCatching {
            migrateMeasurementsWithoutGeohash()
            prefs.setGeoHashMigrationDone()
        }
    }

    private suspend fun migrateMeasurementsWithoutGeohash() {
        val db = FirebaseFirestore.getInstance()
        val collection = db.collection(AppConstants.FIRESTORE_COLLECTION_MEASUREMENTS)

        var lastDoc: com.google.firebase.firestore.DocumentSnapshot? = null
        var snapshot: com.google.firebase.firestore.QuerySnapshot

        do {
            val query = if (lastDoc == null) {
                collection.orderBy("timestamp", Query.Direction.ASCENDING).limit(100)
            } else {
                collection.orderBy("timestamp", Query.Direction.ASCENDING)
                    .startAfter(lastDoc)
                    .limit(100)
            }
            snapshot = withTimeout(5000L) { query.get().await() }
            if (snapshot.isEmpty) break

            var hasUpdates = false
            val batch = db.batch()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                if (data.containsKey("geohash") && data["geohash"] != null) continue
                val location = data["location"] as? com.google.firebase.firestore.GeoPoint ?: continue
                val geohash = GeoHashUtil.encode(location.latitude, location.longitude)
                batch.update(doc.reference, "geohash", geohash)
                hasUpdates = true
            }
            if (hasUpdates) withTimeout(5000L) { batch.commit().await() }
            lastDoc = snapshot.documents.lastOrNull()
        } while (snapshot.size() == 100)
    }
}
