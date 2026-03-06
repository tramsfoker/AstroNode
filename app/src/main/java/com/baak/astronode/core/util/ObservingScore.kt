package com.baak.astronode.core.util

import com.baak.astronode.core.model.WeatherData

data class ObservingCondition(
    val score: Int,
    val rating: String,
    val color: Long,
    val factors: List<String>
)

object ObservingScore {

    fun calculate(weather: WeatherData?, moon: MoonData): ObservingCondition {
        var score = 100
        val factors = mutableListOf<String>()

        weather?.cloudCover?.let { cloud ->
            score -= (cloud * 0.6).toInt()
            if (cloud > 50) factors.add("☁ %$cloud bulutlu")
        }

        score -= (moon.illumination * 0.25).toInt()
        if (moon.illumination > 40) factors.add("${moon.emoji} %${moon.illumination} ay")

        weather?.humidity?.let { hum ->
            if (hum > 80) {
                score -= 10
                factors.add("💧 %$hum nem")
            }
        }

        weather?.windSpeed?.let { wind ->
            if (wind > 30) {
                score -= 5
                factors.add("💨 ${wind.toInt()} km/s")
            }
        }

        score = score.coerceIn(0, 100)

        val rating = when {
            score >= 80 -> "Mükemmel"
            score >= 60 -> "İyi"
            score >= 40 -> "Orta"
            score >= 20 -> "Kötü"
            else -> "Uygun Değil"
        }

        val color = when {
            score >= 80 -> 0xFF2E7D32L
            score >= 60 -> 0xFF558B2FL
            score >= 40 -> 0xFFF57F17L
            score >= 20 -> 0xFFE65100L
            else -> 0xFFC62828L
        }

        return ObservingCondition(score, rating, color, factors)
    }
}
