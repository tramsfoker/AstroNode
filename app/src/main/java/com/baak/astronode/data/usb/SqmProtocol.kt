package com.baak.astronode.data.usb

import com.baak.astronode.core.constants.AppConstants

data class SqmReading(
    val mpsas: Double,
    val temperature: Double?,
    val rawResponse: String
)

object SqmProtocol {

    private val sqmRegex = Regex("""r,\s*([0-9.]+)m,""")
    private val tempRegex = Regex("""\s*([0-9.]+)C""")

    fun buildReadCommand(): ByteArray =
        AppConstants.SQM_READ_COMMAND.toByteArray(Charsets.US_ASCII)

    fun parseResponse(raw: String): SqmReading? {
        val mpsasMatch = sqmRegex.find(raw) ?: return null
        val mpsas = mpsasMatch.groupValues[1].toDoubleOrNull() ?: return null

        if (mpsas < AppConstants.MPSAS_MIN || mpsas > AppConstants.MPSAS_MAX) return null

        val temperature = tempRegex.find(raw)?.groupValues?.get(1)?.toDoubleOrNull()

        return SqmReading(
            mpsas = mpsas,
            temperature = temperature,
            rawResponse = raw
        )
    }
}
