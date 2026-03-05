package com.baak.astronode.core.util

/**
 * MPSAS → Bortle dönüşüm fonksiyonları — MASTERPLAN Bölüm 3.3
 */
object BortleScale {

    /**
     * MPSAS değerinden Bortle sınıfı (1-9) hesaplar.
     */
    fun toBortleClass(mpsas: Double): Int = when {
        mpsas >= 21.99 -> 1
        mpsas >= 21.89 -> 2
        mpsas >= 21.69 -> 3
        mpsas >= 20.49 -> 4
        mpsas >= 19.50 -> 5
        mpsas >= 18.94 -> 6
        mpsas >= 18.38 -> 7
        mpsas >= 17.80 -> 8
        else -> 9
    }

    /**
     * Bortle sınıfına göre hex renk kodu döndürür (0xAARRGGBB formatında Long).
     */
    fun toBortleColor(bortleClass: Int): Long = when (bortleClass.coerceIn(1, 9)) {
        1 -> 0xFF000033L
        2 -> 0xFF000066L
        3 -> 0xFF003399L
        4 -> 0xFF006633L
        5 -> 0xFF669900L
        6 -> 0xFFCCCC00L
        7 -> 0xFFCC6600L
        8 -> 0xFFCC3300L
        9 -> 0xFFCC0000L
        else -> 0xFF333333L
    }

    /**
     * Bortle sınıfına göre Türkçe açıklama döndürür.
     */
    fun toBortleDescription(bortleClass: Int): String = when (bortleClass.coerceIn(1, 9)) {
        1 -> "Mükemmel karanlık alan"
        2 -> "Tipik karanlık alan"
        3 -> "Kırsal gökyüzü"
        4 -> "Kırsal/banliyö geçişi"
        5 -> "Banliyö"
        6 -> "Parlak banliyö"
        7 -> "Banliyö/şehir geçişi"
        8 -> "Şehir gökyüzü"
        9 -> "Şehir merkezi"
        else -> "Bilinmeyen"
    }
}
