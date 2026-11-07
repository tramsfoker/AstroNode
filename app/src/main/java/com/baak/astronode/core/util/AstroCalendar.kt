package com.baak.astronode.core.util

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.floor

/** Gözlemciler için takvim: ay evreleri ve büyük meteor yağmurları (çevrimdışı hesaplanır). */
object AstroCalendar {

    enum class MoonPhaseType { NEW_MOON, FIRST_QUARTER, FULL_MOON, LAST_QUARTER }

    data class MoonEvent(val type: MoonPhaseType, val timeMillis: Long)

    /** Yıllık meteor yağmuru; tepe tarihi ay/gün (UTC), ZHR = saatlik zenit oranı */
    data class MeteorShower(val key: String, val name: String, val peakMonth: Int, val peakDay: Int, val zhr: Int)

    data class ShowerOccurrence(val shower: MeteorShower, val peakMillis: Long)

    private const val SYNODIC_MONTH_DAYS = 29.530588853
    private const val DAY_MS = 86_400_000.0
    /** Referans yeni ay: 2000-01-06 18:14 UTC */
    private const val REF_NEW_MOON_MS = 947_182_440_000.0

    val METEOR_SHOWERS = listOf(
        MeteorShower("quadrantids", "Quadrantidler", 1, 3, 110),
        MeteorShower("lyrids", "Liridler", 4, 22, 18),
        MeteorShower("eta_aquariids", "Eta Akuaridler", 5, 6, 50),
        MeteorShower("delta_aquariids", "Delta Akuaridler", 7, 30, 25),
        MeteorShower("perseids", "Perseidler", 8, 12, 100),
        MeteorShower("draconids", "Drakonidler", 10, 8, 10),
        MeteorShower("orionids", "Orionidler", 10, 21, 20),
        MeteorShower("leonids", "Leonidler", 11, 17, 15),
        MeteorShower("geminids", "Geminidler", 12, 14, 150),
        MeteorShower("ursids", "Ursidler", 12, 22, 10)
    )

    /** [fromMillis] sonrasındaki ilk [count] ay evresi (ortalama sinodik ay; ±~14 saat doğruluk). */
    fun nextMoonPhases(fromMillis: Long, count: Int = 8): List<MoonEvent> {
        val lunations = (fromMillis - REF_NEW_MOON_MS) / (SYNODIC_MONTH_DAYS * DAY_MS)
        var quarter = floor(lunations * 4).toLong()
        val result = mutableListOf<MoonEvent>()
        while (result.size < count) {
            val t = (REF_NEW_MOON_MS + quarter / 4.0 * SYNODIC_MONTH_DAYS * DAY_MS).toLong()
            if (t > fromMillis) {
                val type = MoonPhaseType.entries[Math.floorMod(quarter, 4L).toInt()]
                result += MoonEvent(type, t)
            }
            quarter++
        }
        return result
    }

    /** Önümüzdeki [days] gün içindeki meteor yağmuru tepeleri. */
    fun upcomingShowers(fromMillis: Long, days: Int = 120): List<ShowerOccurrence> {
        val utc = TimeZone.getTimeZone("UTC")
        val from = Calendar.getInstance(utc).apply { timeInMillis = fromMillis }
        val until = fromMillis + days * DAY_MS.toLong()
        val startOfToday = Calendar.getInstance(utc).apply {
            timeInMillis = fromMillis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return listOf(from.get(Calendar.YEAR), from.get(Calendar.YEAR) + 1).flatMap { year ->
            METEOR_SHOWERS.map { s ->
                val c = Calendar.getInstance(utc).apply {
                    clear(); set(year, s.peakMonth - 1, s.peakDay, 0, 0, 0)
                }
                ShowerOccurrence(s, c.timeInMillis)
            }
        }.filter { it.peakMillis in startOfToday..until }.sortedBy { it.peakMillis }
    }

    /** Ay aydınlanması (0..1) — yağmurun izlenebilirliği için */
    fun moonIlluminationAt(millis: Long): Double {
        val age = ((millis - REF_NEW_MOON_MS) / DAY_MS).mod(SYNODIC_MONTH_DAYS)
        return (1 - kotlin.math.cos(2 * Math.PI * age / SYNODIC_MONTH_DAYS)) / 2
    }
}
