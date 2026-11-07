package com.baak.astronode.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.CsvWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Verilen ölçümleri CSV'ye yazar ve paylaşılabilir bir URI döndürür. */
class ExportDataUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun exportToCsv(measurements: List<SkyMeasurement>): Uri? = withContext(Dispatchers.IO) {
        if (measurements.isEmpty()) return@withContext null

        val dir = File(context.cacheDir, EXPORT_DIR).apply { mkdirs() }
        // Eski dışa aktarımları temizle (cache şişmesin)
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "astronode_measurements_${System.currentTimeMillis()}.csv")
        file.writeText(CsvWriter.measurementsToCsv(measurements), Charsets.UTF_8)

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        const val EXPORT_DIR = "exports"
    }
}
