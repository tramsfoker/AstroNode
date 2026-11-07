package com.baak.astronode.core.util

import com.baak.astronode.core.util.TonightScore.Rating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonightScoreTest {
    @Test
    fun `açık ve aysız gece mükemmel`() {
        val r = TonightScore.calculate(0, 0.0, 2)
        assertEquals(Rating.EXCELLENT, r.rating)
        assertTrue(r.score >= 90)
    }

    @Test
    fun `kapalı gökyüzü zayıf`() {
        assertEquals(Rating.POOR, TonightScore.calculate(100, 0.0).rating)
    }

    @Test
    fun `dolunay ve şehir skoru düşürür`() {
        val dark = TonightScore.calculate(10, 0.0, 2).score
        val moonCity = TonightScore.calculate(10, 1.0, 9).score
        assertTrue(moonCity < dark)
    }

    @Test
    fun `tahmin yoksa orta değer ve sınırlar korunur`() {
        val r = TonightScore.calculate(null, 0.5)
        assertEquals(null, r.cloudCover)
        assertTrue(r.score in 0..100)
        // Aralık dışı girdiler sınırlanır
        val clamped = TonightScore.calculate(-50, -1.0, 20)
        assertTrue(clamped.score in 0..100)
        assertEquals(0, clamped.cloudCover)
    }
}
