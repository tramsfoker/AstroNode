package com.baak.astronode.core.util

import com.baak.astronode.core.util.AstroCalendar.MoonPhaseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.math.abs

class AstroCalendarTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()
    private val dayMs = 86_400_000L

    @Test
    fun `ay evreleri gerçek tarihlere bir gün içinde yakın`() {
        // Gerçek değerler (UTC): Yeni Ay 2026-11-09 07:02, Dolunay 2026-11-24 14:53
        val phases = AstroCalendar.nextMoonPhases(ms("2026-11-07T00:00:00Z"), 4)
        val newMoon = phases.first { it.type == MoonPhaseType.NEW_MOON }
        val fullMoon = phases.first { it.type == MoonPhaseType.FULL_MOON }
        assertTrue(abs(newMoon.timeMillis - ms("2026-11-09T07:02:00Z")) < dayMs)
        assertTrue(abs(fullMoon.timeMillis - ms("2026-11-24T14:53:00Z")) < dayMs)
    }

    @Test
    fun `evreler sırayla ve gelecekte`() {
        val from = ms("2026-01-01T00:00:00Z")
        val phases = AstroCalendar.nextMoonPhases(from, 8)
        assertEquals(8, phases.size)
        assertTrue(phases.all { it.timeMillis > from })
        assertTrue(phases.zipWithNext().all { (a, b) -> b.timeMillis > a.timeMillis })
        // Ardışık evreler döngüsel sırada
        phases.zipWithNext().forEach { (a, b) -> assertEquals((a.type.ordinal + 1) % 4, b.type.ordinal) }
    }

    @Test
    fun `aydınlanma yeni ayda düşük dolunayda yüksek`() {
        assertTrue(AstroCalendar.moonIlluminationAt(ms("2026-11-09T07:02:00Z")) < 0.05)
        assertTrue(AstroCalendar.moonIlluminationAt(ms("2026-11-24T14:53:00Z")) > 0.95)
    }

    @Test
    fun `yaklaşan meteor yağmurları yıl geçişini kapsar`() {
        val list = AstroCalendar.upcomingShowers(ms("2026-12-01T00:00:00Z"), 60)
        val keys = list.map { it.shower.key }
        assertEquals(listOf("geminids", "ursids", "quadrantids"), keys)
    }
}
