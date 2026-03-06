package com.baak.astronode.core.util

import kotlin.math.cos
import kotlin.math.PI

data class MoonData(
    val phase: Double,           // 0.0-1.0
    val phaseName: String,       // "Dolunay"
    val emoji: String,           // "🌕"
    val illumination: Int,       // 0-100%
    val observingImpact: String  // "Mükemmel — Ay etkisi yok"
)

object MoonCalc {

    private var cachedMoon: MoonData? = null
    private var lastMoonCalc: Long = 0
    private val MOON_CACHE_MS = 60 * 60 * 1000L // 1 saat

    fun getMoonPhase(timeMillis: Long = System.currentTimeMillis()): MoonData {
        val now = System.currentTimeMillis()
        cachedMoon?.let { cached ->
            if ((now - lastMoonCalc) < MOON_CACHE_MS) return cached
        }
        val result = calculateMoonPhase(now)
        cachedMoon = result
        lastMoonCalc = now
        return result
    }

    private fun calculateMoonPhase(timeMillis: Long): MoonData {
        val daysSinceNew = daysSinceKnownNewMoon(timeMillis)
        val synodicMonth = 29.53058867
        val phase = (daysSinceNew % synodicMonth) / synodicMonth
        val illumination = ((1 - cos(phase * 2 * PI)) / 2 * 100).toInt().coerceIn(0, 100)

        val phaseName = when {
            phase < 0.0625 -> "Yeni Ay"
            phase < 0.1875 -> "Hilal (Büyüyen)"
            phase < 0.3125 -> "İlk Dördün"
            phase < 0.4375 -> "Şişkin Ay (Büyüyen)"
            phase < 0.5625 -> "Dolunay"
            phase < 0.6875 -> "Şişkin Ay (Küçülen)"
            phase < 0.8125 -> "Son Dördün"
            phase < 0.9375 -> "Hilal (Küçülen)"
            else -> "Yeni Ay"
        }

        val emoji = when {
            phase < 0.0625 -> "🌑"
            phase < 0.1875 -> "🌒"
            phase < 0.3125 -> "🌓"
            phase < 0.4375 -> "🌔"
            phase < 0.5625 -> "🌕"
            phase < 0.6875 -> "🌖"
            phase < 0.8125 -> "🌗"
            phase < 0.9375 -> "🌘"
            else -> "🌑"
        }

        val observingImpact = when {
            illumination < 10 -> "Mükemmel — Ay etkisi yok"
            illumination < 30 -> "İyi — Az ay ışığı"
            illumination < 60 -> "Orta — Ay etkili"
            illumination < 80 -> "Kötü — Çok ay ışığı"
            else -> "Çok Kötü — Dolunay"
        }

        return MoonData(phase, phaseName, emoji, illumination, observingImpact)
    }

    private fun daysSinceKnownNewMoon(timeMillis: Long): Double {
        val knownNewMoonMillis = 947182440000L
        return (timeMillis - knownNewMoonMillis) / 86400000.0
    }
}
