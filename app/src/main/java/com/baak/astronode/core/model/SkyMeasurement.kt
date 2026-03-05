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
    val geohash: String? = null
)
