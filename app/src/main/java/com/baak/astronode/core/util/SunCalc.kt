package com.baak.astronode.core.util

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.tan
import java.util.Calendar

data class ObservingTimeStatus(
    val canObserve: Boolean,
    val reason: String,
    val detail: String,
    val emoji: String,
    val sunsetTime: Long
)

object SunCalc {

    fun getSunsetTime(lat: Double, lng: Double, timeMillis: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val dayOfYear = cal.get(Calendar.DAY_OF_YEAR)

        val latRad = Math.toRadians(lat)
        val declination = -23.45 * cos(Math.toRadians(360.0 / 365 * (dayOfYear + 10)))
        val decRad = Math.toRadians(declination)
        val cosHourAngle = (-tan(latRad) * tan(decRad)).let { v -> maxOf(-1.0, minOf(1.0, v)) }
        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val sunsetHour = 12.0 + hourAngle / 15.0

        val tzOffset = cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)
        val lngCorrection = (lng - (tzOffset / 3600000.0) * 15) / 15.0
        val adjustedSunset = sunsetHour - lngCorrection

        cal.set(Calendar.HOUR_OF_DAY, adjustedSunset.toInt().coerceIn(0, 23))
        cal.set(Calendar.MINUTE, ((adjustedSunset % 1).let { if (it < 0) it + 1 else it } * 60).toInt().coerceIn(0, 59))
        return cal.timeInMillis
    }

    fun isGoodTimeForObserving(lat: Double, lng: Double): ObservingTimeStatus {
        val now = System.currentTimeMillis()
        val sunset = getSunsetTime(lat, lng, now)
        val astronomicalDusk = sunset + (90 * 60 * 1000L)

        return when {
            now < sunset -> {
                val minutesUntilSunset = (sunset - now) / 60000
                ObservingTimeStatus(
                    canObserve = false,
                    reason = "Güneş henüz batmadı",
                    detail = "Batışa ${minutesUntilSunset}dk kaldı",
                    emoji = "☀️",
                    sunsetTime = sunset
                )
            }
            now < astronomicalDusk -> {
                val minutesUntilDark = (astronomicalDusk - now) / 60000
                ObservingTimeStatus(
                    canObserve = false,
                    reason = "Alacakaranlık devam ediyor",
                    detail = "Karanlığa ${minutesUntilDark}dk kaldı",
                    emoji = "🌅",
                    sunsetTime = sunset
                )
            }
            else -> {
                ObservingTimeStatus(
                    canObserve = true,
                    reason = "Gözlem için uygun",
                    detail = "Astronomik karanlık",
                    emoji = "🌌",
                    sunsetTime = sunset
                )
            }
        }
    }

    fun formatMinutesUntilSunset(sunsetTime: Long): String {
        val now = System.currentTimeMillis()
        if (now >= sunsetTime) return ""
        val minutes = (sunsetTime - now) / 60000
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours} saat ${mins}dk" else "${mins}dk"
    }

    /** Cache için: Sadece countdown detayını güncelle (günde 1 kez tam hesaplama yeterli) */
    fun getCountdownDetail(sunsetTime: Long): String {
        val now = System.currentTimeMillis()
        val astronomicalDusk = sunsetTime + 90 * 60 * 1000L
        return when {
            now >= astronomicalDusk -> "Astronomik karanlık"
            now >= sunsetTime -> {
                val minutesUntilDark = (astronomicalDusk - now) / 60000
                "Karanlığa ${minutesUntilDark}dk kaldı"
            }
            else -> {
                val minutesUntilSunset = (sunsetTime - now) / 60000
                "Batışa ${minutesUntilSunset}dk kaldı"
            }
        }
    }
}
