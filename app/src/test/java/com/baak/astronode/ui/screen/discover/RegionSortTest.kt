package com.baak.astronode.ui.screen.discover

import com.baak.astronode.core.model.RegionStat
import org.junit.Assert.assertEquals
import org.junit.Test

class RegionSortTest {
    private fun stat(name: String, count: Int, avg: Double) =
        RegionStat(name, "TR", name, null, 1, count, avg * count, avg * avg * count, avg, null, emptyMap())

    @Test
    fun `az ölçümlü bölge en karanlık listesinde başa geçemez`() {
        val list = listOf(stat("Tek", 1, 22.0), stat("Van", 10, 21.0), stat("İstanbul", 50, 17.0))
        val sorted = DiscoverViewModel.sortRegions(list, RegionSort.DARKEST).map { it.admin1 }
        assertEquals(listOf("Van", "İstanbul", "Tek"), sorted)
    }

    @Test
    fun `en çok ölçülen sıralaması`() {
        val list = listOf(stat("A", 5, 20.0), stat("B", 50, 18.0))
        assertEquals("B", DiscoverViewModel.sortRegions(list, RegionSort.MOST).first().admin1)
    }

    @Test
    fun `aylık ortalamalar sıralı ve standart sapma`() {
        val s = RegionStat("x", "TR", "X", null, 1, 2, 40.0, 802.0, 21.0, null,
            mapOf("2026-11" to (1 to 21.0), "2026-10" to (1 to 19.0)))
        assertEquals(listOf("2026-10" to 19.0, "2026-11" to 21.0), s.monthlyAverages())
        assertEquals(1.0, s.stdDev!!, 1e-9)
    }
}
