package com.baak.astronode.core.util

import androidx.compose.ui.graphics.Color

object BortleScale {

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

    fun toBortleColor(bortleClass: Int): Color = when (bortleClass) {
        1 -> Color(0xFF000033)
        2 -> Color(0xFF000066)
        3 -> Color(0xFF003399)
        4 -> Color(0xFF006633)
        5 -> Color(0xFF669900)
        6 -> Color(0xFFCCCC00)
        7 -> Color(0xFFCC6600)
        8 -> Color(0xFFCC3300)
        9 -> Color(0xFFCC0000)
        else -> Color(0xFF333333)
    }

    @Deprecated("Use toBortleColor instead", ReplaceWith("toBortleColor(bortleClass)"))
    fun bortleColor(bortleClass: Int): Color = toBortleColor(bortleClass)

    fun bortleLabel(bortleClass: Int): String = when (bortleClass) {
        1 -> "Mükemmel karanlık"
        2 -> "Tipik karanlık"
        3 -> "Kırsal gökyüzü"
        4 -> "Kırsal/banliyö"
        5 -> "Banliyö"
        6 -> "Parlak banliyö"
        7 -> "Banliyö/şehir"
        8 -> "Şehir gökyüzü"
        9 -> "Şehir merkezi"
        else -> "Bilinmiyor"
    }
}
