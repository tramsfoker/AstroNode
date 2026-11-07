package com.baak.astronode.core.util

/** Bu gece gözlem uygunluğu: bulut örtüsü (tahmin) + Ay aydınlanması (+ varsa bölgenin Bortle sınıfı). */
object TonightScore {

    enum class Rating { EXCELLENT, GOOD, FAIR, POOR }

    data class Result(val score: Int, val rating: Rating, val cloudCover: Int?, val moonIllumination: Int)

    /**
     * @param cloudCoverPct gece saatleri ortalama bulut (0..100), bilinmiyorsa null
     * @param moonIllumination 0..1
     * @param bortle bölgenin tipik Bortle sınıfı (1..9), bilinmiyorsa null
     */
    fun calculate(cloudCoverPct: Int?, moonIllumination: Double, bortle: Int? = null): Result {
        val cloud = cloudCoverPct?.coerceIn(0, 100)
        val cloudFactor = cloud?.let { 1.0 - it / 100.0 } ?: 0.6
        val moonFactor = 1.0 - 0.6 * moonIllumination.coerceIn(0.0, 1.0)
        val skyFactor = bortle?.let { 1.0 - (it.coerceIn(1, 9) - 1) * 0.05 } ?: 1.0
        val score = (100 * cloudFactor * moonFactor * skyFactor).toInt().coerceIn(0, 100)
        val rating = when {
            score >= 75 -> Rating.EXCELLENT
            score >= 55 -> Rating.GOOD
            score >= 35 -> Rating.FAIR
            else -> Rating.POOR
        }
        return Result(score, rating, cloud, (moonIllumination * 100).toInt())
    }
}
