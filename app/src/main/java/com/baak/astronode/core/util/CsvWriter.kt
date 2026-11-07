package com.baak.astronode.core.util

import com.baak.astronode.core.model.SkyMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** RFC 4180 uyumlu CSV üretimi (virgül, tırnak ve satır sonu içeren alanlar doğru kaçışlanır). */
object CsvWriter {

    private val HEADER = listOf(
        "timestamp_utc", "local_time", "sqm_value", "bortle_class", "lat", "lng", "altitude",
        "azimuth", "pitch", "roll", "temperature", "humidity", "cloud_cover", "wind_speed", "visibility",
        "moon_phase", "moon_illumination", "observing_score", "observing_rating", "is_daytime", "is_test",
        "session_name", "note"
    )

    fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
            value.startsWith(" ") || value.endsWith(" ")
        // Excel formül enjeksiyonuna karşı (=, +, -, @ ile başlayan metin)
        val safe = if (value.isNotEmpty() && value[0] in "=+@" ) "'$value" else value
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    fun measurementsToCsv(measurements: List<SkyMeasurement>): String {
        val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append("\r\n")
        for (m in measurements) {
            val row = listOf(
                utc.format(Date(m.timestamp)),
                m.measurementTime.orEmpty(),
                m.sqmValue.toString(),
                m.bortleClass.toString(),
                m.latitude.toString(),
                m.longitude.toString(),
                m.altitude?.toString().orEmpty(),
                m.azimuth?.toString().orEmpty(),
                m.pitch?.toString().orEmpty(),
                m.roll?.toString().orEmpty(),
                (m.temperature ?: m.weather?.temperature)?.toString().orEmpty(),
                (m.humidity ?: m.weather?.humidity)?.toString().orEmpty(),
                (m.cloudCover ?: m.weather?.cloudCover)?.toString().orEmpty(),
                (m.windSpeed ?: m.weather?.windSpeed)?.toString().orEmpty(),
                (m.visibility ?: m.weather?.visibility)?.toString().orEmpty(),
                m.moonPhase.orEmpty(),
                m.moonIllumination?.toString().orEmpty(),
                m.observingScore?.toString().orEmpty(),
                m.observingRating.orEmpty(),
                m.isDaytime.toString(),
                m.isTest.toString(),
                m.sessionName.orEmpty(),
                m.note.orEmpty()
            )
            sb.append(row.joinToString(",") { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }
}
