package com.baak.astronode.core.model

data class WeatherData(
    val temperature: Double? = null,
    val humidity: Int? = null,
    val cloudCover: Int? = null,
    val windSpeed: Double? = null,
    val visibility: Double? = null
)
