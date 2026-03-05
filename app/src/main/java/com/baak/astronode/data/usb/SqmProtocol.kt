package com.baak.astronode.data.usb

/**
 * SQM protokol işlemleri — saf Kotlin, Android bağımsız.
 * Bölüm 5 (SQM Protokol) spesifikasyonuna uygun.
 */
object SqmProtocol {

    private val MPSAS_REGEX = Regex("""r,\s*([0-9.]+)m,""")
    private val TEMPERATURE_REGEX = Regex("""([0-9.]+)C\s*\r?$""")

    /**
     * Ölçüm isteme komutu: "rx\r" (ASCII, CR ile biter)
     */
    fun buildReadCommand(): ByteArray = "rx\r".toByteArray(Charsets.US_ASCII)

    /**
     * SQM cihazından gelen ham cevabı parse eder.
     * @param raw Ham cevap string'i (örn: "r, 21.53m,0000000002Hz,0000000000c,0000025.3s, 027.2C\r")
     * @return Geçerli formatta SqmReading, hatalı formatta null
     */
    fun parseResponse(raw: String): SqmReading? {
        val trimmed = raw.trim()
        val mpsasMatch = MPSAS_REGEX.find(trimmed) ?: return null
        val mpsasValue = mpsasMatch.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return null

        val temperature = TEMPERATURE_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.toDoubleOrNull()

        return SqmReading(
            mpsas = mpsasValue,
            temperature = temperature,
            rawResponse = raw
        )
    }
}

/**
 * SQM ölçüm sonucu.
 * @param mpsas Magnitudes per square arcsecond (Mag/arcsec²)
 * @param temperature Sensör sıcaklığı (°C), opsiyonel
 * @param rawResponse Ham cihaz cevabı
 */
data class SqmReading(
    val mpsas: Double,
    val temperature: Double?,
    val rawResponse: String
)
