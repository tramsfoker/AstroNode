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

        val header = "timestamp,sqm_value,bortle_class,latitude,longitude,altitude,azimuth,pitch,roll,note,session_id,session_name"
        val lines = list.map { m ->
            val ts = dateFormat.format(Date(m.timestamp))
            val lat = m.latitude.toString()
            val lng = m.longitude.toString()
            val alt = m.altitude?.toString() ?: ""
            val az = m.azimuth?.toString() ?: ""
            val pitch = m.pitch?.toString() ?: ""
            val roll = m.roll?.toString() ?: ""
            val note = (m.note ?: "").replace(",", ";").replace("\n", " ")
            val sid = m.sessionId ?: ""
            val sname = (m.sessionName ?: "").replace(",", ";").replace("\n", " ")
            "$ts,${m.sqmValue},${m.bortleClass},$lat,$lng,$alt,$az,$pitch,$roll,$note,$sid,$sname"
        }

        file.writeText("$header\n${lines.joinToString("\n")}")

        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
