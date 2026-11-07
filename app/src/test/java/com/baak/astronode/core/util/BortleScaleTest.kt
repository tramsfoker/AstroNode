package com.baak.astronode.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BortleScaleTest {

    @Test
    fun `sınır değerleri doğru sınıfa düşer`() {
        assertEquals(1, BortleScale.toBortleClass(22.0))
        assertEquals(1, BortleScale.toBortleClass(21.99))
        assertEquals(2, BortleScale.toBortleClass(21.98))
        assertEquals(3, BortleScale.toBortleClass(21.69))
        assertEquals(4, BortleScale.toBortleClass(21.0))
        assertEquals(5, BortleScale.toBortleClass(19.50))
        assertEquals(6, BortleScale.toBortleClass(19.0))
        assertEquals(7, BortleScale.toBortleClass(18.5))
        assertEquals(8, BortleScale.toBortleClass(17.80))
        assertEquals(9, BortleScale.toBortleClass(17.79))
        assertEquals(9, BortleScale.toBortleClass(0.0))
    }

    @Test
    fun `her sınıfın etiketi var`() {
        (1..9).forEach { assert(BortleScale.bortleLabel(it) != "Bilinmiyor") }
    }
}
