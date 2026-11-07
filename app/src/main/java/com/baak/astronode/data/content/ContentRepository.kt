package com.baak.astronode.data.content

import android.util.Log
import com.baak.astronode.core.model.NewsItem
import com.baak.astronode.core.model.OrganizationRequest
import com.baak.astronode.core.model.RegionStat
import com.baak.astronode.core.model.SkyCell
import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Haberler, bölge istatistikleri, gökyüzü hücreleri, raporlar ve organizasyon başvuruları. */
@Singleton
class ContentRepository @Inject constructor() {

    private val db = FirebaseFirestore.getInstance()

    // ---------------- Haberler ----------------

    fun getNews(limit: Long = NEWS_LIMIT): Flow<List<NewsItem>> = listen(
        db.collection("news").orderBy("published_at", Query.Direction.DESCENDING).limit(limit),
        "news"
    ) { it.toNews() }

    private fun DocumentSnapshot.toNews(): NewsItem? {
        val title = getString("title") ?: return null
        val type = getString("type") ?: "rss"
        return NewsItem(
            id = id,
            type = type,
            title = title,
            summary = getString(if (type == "post") "body" else "summary").orEmpty(),
            link = getString("link")?.takeIf { it.startsWith("http") },
            imageUrl = getString("image_url")?.takeIf { it.startsWith("https") },
            sourceName = getString("org_name") ?: getString("source_name") ?: "AstroNode",
            publishedAt = getTimestamp("published_at")?.toDate()?.time ?: 0L,
            pinned = getBoolean("pinned") == true,
            eventDate = getTimestamp("event_date")?.toDate()?.time,
            authorUid = getString("author_uid")
        )
    }

    // ---------------- Bölge istatistikleri ----------------

    /** Türkiye illeri (81 belge — sıralama istemcide). */
    fun getProvinceStats(country: String = "TR"): Flow<List<RegionStat>> = listen(
        db.collection("region_stats").whereEqualTo("country", country).whereEqualTo("level", 1),
        "province_stats"
    ) { it.toRegionStat() }

    fun getDistrictStats(admin1: String, country: String = "TR"): Flow<List<RegionStat>> = listen(
        db.collection("region_stats")
            .whereEqualTo("country", country)
            .whereEqualTo("admin1", admin1)
            .whereEqualTo("level", 2),
        "district_stats"
    ) { it.toRegionStat() }

    private fun DocumentSnapshot.toRegionStat(): RegionStat? {
        val count = getLong("count")?.toInt() ?: return null
        if (count <= 0) return null
        @Suppress("UNCHECKED_CAST")
        val months = (get("months") as? Map<String, Map<String, Any?>>).orEmpty().mapValues { (_, v) ->
            ((v["count"] as? Number)?.toInt() ?: 0) to ((v["sum"] as? Number)?.toDouble() ?: 0.0)
        }
        return RegionStat(
            id = id,
            country = getString("country") ?: "",
            admin1 = getString("admin1") ?: return null,
            admin2 = getString("admin2"),
            level = getLong("level")?.toInt() ?: 1,
            count = count,
            sum = getDouble("sum") ?: 0.0,
            sumSq = getDouble("sum_sq") ?: 0.0,
            best = getDouble("best"),
            lastAt = getTimestamp("last_at")?.toDate()?.time,
            months = months
        )
    }

    // ---------------- Gökyüzü hücreleri ----------------

    /** Yakındaki ~5 km hücreler (en karanlıktan aydınlığa). */
    suspend fun getNearbyCells(lat: Double, lng: Double, radiusKm: Double = NEARBY_RADIUS_KM): List<SkyCell> {
        val center = GeoLocation(lat, lng)
        val radiusM = radiusKm * 1000
        val cells = mutableMapOf<String, SkyCell>()
        return try {
            for (b in GeoFireUtils.getGeoHashQueryBounds(center, radiusM)) {
                val snap = withTimeout(QUERY_TIMEOUT_MS) {
                    db.collection("sky_cells").orderBy("geohash").startAt(b.startHash).endAt(b.endHash)
                        .limit(CELLS_PER_BOUND).get().await()
                }
                for (doc in snap.documents) {
                    val count = doc.getLong("count")?.toInt() ?: continue
                    if (count <= 0) continue
                    val cLat = (doc.getDouble("sum_lat") ?: continue) / count
                    val cLng = (doc.getDouble("sum_lng") ?: continue) / count
                    if (GeoFireUtils.getDistanceBetween(GeoLocation(cLat, cLng), center) > radiusM) continue
                    cells[doc.id] = SkyCell(
                        geohash = doc.id,
                        count = count,
                        average = (doc.getDouble("sum") ?: 0.0) / count,
                        best = doc.getDouble("best"),
                        centerLat = cLat,
                        centerLng = cLng,
                        admin1 = doc.getString("admin1")
                    )
                }
            }
            cells.values.sortedByDescending { it.average }
        } catch (e: Exception) {
            Log.w(TAG, "Yakın hücreler alınamadı: ${e.message}")
            emptyList()
        }
    }

    // ---------------- Raporlar (Play UGC zorunluluğu) ----------------

    suspend fun submitReport(
        reporterUid: String,
        targetType: String,
        targetId: String,
        reason: String,
        details: String?
    ): Result<Unit> = try {
        db.collection("reports").add(
            mapOf(
                "reporter_uid" to reporterUid,
                "target_type" to targetType,
                "target_id" to targetId,
                "reason" to reason,
                "details" to details?.trim()?.take(500)?.takeIf { it.isNotBlank() },
                "status" to "open",
                "created_at" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    // ---------------- Organizasyon başvurusu ----------------

    suspend fun submitOrganizationRequest(
        uid: String,
        requesterName: String?,
        orgName: String,
        description: String?,
        city: String?,
        website: String?,
        contactEmail: String
    ): Result<Unit> = try {
        db.collection("organization_requests").add(
            mapOf(
                "requester_uid" to uid,
                "requester_name" to requesterName?.take(60),
                "org_name" to orgName.trim().take(100),
                "description" to description?.trim()?.take(1000)?.takeIf { it.isNotBlank() },
                "city" to city?.trim()?.take(60)?.takeIf { it.isNotBlank() },
                "website" to website?.trim()?.take(200)?.takeIf { it.isNotBlank() },
                "contact_email" to contactEmail.trim(),
                "status" to "pending",
                "created_at" to FieldValue.serverTimestamp()
            )
        ).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun getMyOrganizationRequests(uid: String): Flow<List<OrganizationRequest>> = listen(
        db.collection("organization_requests").whereEqualTo("requester_uid", uid)
            .orderBy("created_at", Query.Direction.DESCENDING).limit(5),
        "org_requests"
    ) { d ->
        OrganizationRequest(
            id = d.id,
            orgName = d.getString("org_name") ?: return@listen null,
            status = d.getString("status") ?: "pending",
            reviewNote = d.getString("review_note")
        )
    }

    // ---------------- Ortak ----------------

    private fun <T> listen(query: Query, label: String, map: (DocumentSnapshot) -> T?): Flow<List<T>> = callbackFlow {
        val reg = query.addSnapshotListener { snap, err ->
            if (err != null) {
                Log.w(TAG, "$label: ${err.message}")
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snap?.documents?.mapNotNull(map).orEmpty())
        }
        awaitClose { reg.remove() }
    }

    private companion object {
        const val TAG = "CONTENT"
        const val NEWS_LIMIT = 60L
        const val NEARBY_RADIUS_KM = 75.0
        const val CELLS_PER_BOUND = 200L
        const val QUERY_TIMEOUT_MS = 8_000L
    }
}
