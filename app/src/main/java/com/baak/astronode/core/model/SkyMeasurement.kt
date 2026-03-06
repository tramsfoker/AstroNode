package com.baak.astronode.core.model

import java.util.UUID

data class SkyMeasurement(
    val id: String = UUID.randomUUID().toString(),
    val sqmValue: Double,
    val bortleClass: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val azimuth: Float?,
    val pitch: Float?,
    val roll: Float?,
    val orientationEnabled: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val deviceId: String,
    val note: String? = null,
    val sessionId: String? = null,
    val sessionName: String? = null,
    val geohash: String? = null,
    val isTest: Boolean = false,
    val observerUid: String = "",
    val observerName: String = "",
    val weather: WeatherData? = null,
    // Hava durumu (Open-Meteo'dan)
    val temperature: Double? = null,
    val humidity: Int? = null,
    val cloudCover: Int? = null,
    val windSpeed: Double? = null,
    val visibility: Double? = null,
    // Ay bilgisi (lokal hesaplama)
    val moonPhase: String? = null,
    val moonIllumination: Int? = null,
    val moonEmoji: String? = null,
    // Gözlem koşulları
    val observingScore: Int? = null,
    val observingRating: String? = null,
    val isDaytime: Boolean = false,
    val measurementTime: String? = null,
)
