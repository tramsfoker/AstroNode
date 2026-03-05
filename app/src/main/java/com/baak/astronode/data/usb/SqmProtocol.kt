package com.baak.astronode.data.usb

import android.util.Log
import com.baak.astronode.core.constants.AppConstants

data class SqmReading(
    val mpsas: Double,
    val temperature: Double?,
    val rawResponse: String
)

object SqmProtocol {

    private val tempRegex = Regex("""\s*([0-9.]+)C""")

    fun buildReadCommand(): ByteArray =
        AppConstants.SQM_READ_COMMAND.toByteArray(Charsets.US_ASCII)

    fun parseResponse(raw: String): SqmReading? {
        Log.d("SQM_PARSE", "Parse ediliyor: '$raw'")

        val regex1 = Regex("""r,\s*([0-9]+[.,][0-9]+)m,""")
        val regex2 = Regex("""([0-9]+[.,][0-9]+)m""")
        val regex3 = Regex("""([0-9]+,[0-9]+)m""")

        val match = regex1.find(raw) ?: regex2.find(raw) ?: regex3.find(raw)

        if (match != null) {
            val valueStr = match.groupValues[1].replace(',', '.')
            val mpsas = valueStr.toDoubleOrNull()
            if (mpsas != null) {
                Log.d("SQM_PARSE", "Başarılı: $mpsas MPSAS")
                val temperature = tempRegex.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()
                return SqmReading(
                    mpsas = mpsas,
                    temperature = temperature,
                    rawResponse = raw
                )
            }
        }

        Log.e("SQM_PARSE", "Hiçbir pattern eşleşmedi: '$raw'")
        return null
    }
}
