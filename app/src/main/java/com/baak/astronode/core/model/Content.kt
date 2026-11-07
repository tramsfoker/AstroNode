package com.baak.astronode.core.model

/** Haber akışı öğesi: RSS (otomatik) veya duyuru (admin/organizatör) */
data class NewsItem(
    val id: String,
    val type: String,            // "rss" | "post"
    val title: String,
    val summary: String,
    val link: String?,
    val imageUrl: String?,
    val sourceName: String,
    val publishedAt: Long,
    val pinned: Boolean,
    val eventDate: Long? = null,
    val authorUid: String? = null
) {
    val isPost: Boolean get() = type == "post"
}

/** İl (level 1) veya ilçe (level 2) ışık kirliliği istatistiği — Cloud Functions üretir */
data class RegionStat(
    val id: String,
    val country: String,
    val admin1: String,
    val admin2: String?,
    val level: Int,
    val count: Int,
    val sum: Double,
    val sumSq: Double,
    val best: Double?,
    val lastAt: Long?,
    /** "2026-11" → (adet, toplam) */
    val months: Map<String, Pair<Int, Double>>
) {
    val average: Double? get() = if (count > 0) sum / count else null
    val stdDev: Double?
        get() {
            if (count < 2) return null
            val mean = sum / count
            val variance = (sumSq / count) - mean * mean
            return kotlin.math.sqrt(variance.coerceAtLeast(0.0))
        }
    val name: String get() = admin2 ?: admin1

    /** Aylık ortalamalar (eskiden yeniye) */
    fun monthlyAverages(): List<Pair<String, Double>> =
        months.filter { it.value.first > 0 }.toSortedMap().map { (k, v) -> k to v.second / v.first }
}

/** ~5 km'lik gökyüzü hücresi (geohash 5) */
data class SkyCell(
    val geohash: String,
    val count: Int,
    val average: Double,
    val best: Double?,
    val centerLat: Double,
    val centerLng: Double,
    val admin1: String?
)

data class OrganizationRequest(
    val id: String,
    val orgName: String,
    val status: String,          // pending | approved | rejected
    val reviewNote: String?
)
