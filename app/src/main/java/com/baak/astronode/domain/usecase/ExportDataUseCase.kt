package com.baak.astronode.domain.usecase

import android.content.Context
import android.net.Uri
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.data.firebase.FirestoreManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ExportDataUseCase @Inject constructor(
    private val firestoreManager: FirestoreManager,
    @ApplicationContext private val context: Context
) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    suspend fun exportToCsv(measurements: List<SkyMeasurement>? = null): Uri? = withContext(Dispatchers.IO) {
        val list = measurements ?: firestoreManager.getMeasurementsOnce()
        if (list.isEmpty()) return@withContext null

        val fileName = "measurements_${System.currentTimeMillis()}.csv"
        val file = File(context.cacheDir, fileName)

        val header = "timestamp,time,sqm_value,bortle_class,lat,lng,altitude,azimuth,pitch,roll,temperature,humidity,cloud_cover,wind_speed,visibility,moon_phase,moon_illumination,observing_score,observing_rating,is_daytime,is_test,session_name,note"
        val lines = list.map { m ->
            val ts = dateFormat.format(Date(m.timestamp))
            val time = m.measurementTime ?: ""
            val lat = m.latitude.toString()
            val lng = m.longitude.toString()
            val alt = m.altitude?.toString() ?: ""
            val az = m.azimuth?.toString() ?: ""
            val pitch = m.pitch?.toString() ?: ""
            val roll = m.roll?.toString() ?: ""
            val temp = (m.temperature ?: m.weather?.temperature)?.toString() ?: ""
            val hum = (m.humidity ?: m.weather?.humidity)?.toString() ?: ""
            val cloud = (m.cloudCover ?: m.weather?.cloudCover)?.toString() ?: ""
            val wind = (m.windSpeed ?: m.weather?.windSpeed)?.toString() ?: ""
            val vis = (m.visibility ?: m.weather?.visibility)?.toString() ?: ""
            val moonPhase = (m.moonPhase ?: "").replace(",", ";")
            val moonIll = m.moonIllumination?.toString() ?: ""
            val obsScore = m.observingScore?.toString() ?: ""
            val obsRating = (m.observingRating ?: "").replace(",", ";")
            val isDay = m.isDaytime
            val isTest = m.isTest
            val sname = (m.sessionName ?: "").replace(",", ";").replace("\n", " ")
            val note = (m.note ?: "").replace(",", ";").replace("\n", " ")
            "$ts,$time,${m.sqmValue},${m.bortleClass},$lat,$lng,$alt,$az,$pitch,$roll,$temp,$hum,$cloud,$wind,$vis,$moonPhase,$moonIll,$obsScore,$obsRating,$isDay,$isTest,$sname,$note"
        }

        file.writeText("$header\n${lines.joinToString("\n")}")

        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
