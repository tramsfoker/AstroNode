package com.baak.astronode.core.util

import com.baak.astronode.core.model.SkyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvWriterTest {

    @Test
    fun `virgül tırnak ve satır sonu kaçışlanır`() {
        assertEquals("plain", CsvWriter.escape("plain"))
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""))
        assertEquals("\"line1\nline2\"", CsvWriter.escape("line1\nline2"))
    }

    @Test
    fun `formül enjeksiyonu etkisizleştirilir`() {
        assertEquals("'=HYPERLINK(1)", CsvWriter.escape("=HYPERLINK(1)"))
        assertEquals("'@cmd", CsvWriter.escape("@cmd"))
    }

    @Test
    fun `başlık ve satır sayısı doğru, sütun sayısı tutarlı`() {
        val m = SkyMeasurement(
            sqmValue = 19.5, bortleClass = 5, latitude = 40.0, longitude = 29.0, altitude = null,
            azimuth = null, pitch = null, roll = null, orientationEnabled = false, timestamp = 0L,
            deviceId = "u", note = "bulutlu, rüzgarlı", sessionName = "Uludağ"
        )
        val lines = CsvWriter.measurementsToCsv(listOf(m, m)).trimEnd().split("\r\n")
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("timestamp_utc,"))
        assertTrue(lines[1].startsWith("1970-01-01T00:00:00Z,"))
        assertTrue(lines[1].endsWith("\"bulutlu, rüzgarlı\""))
    }
}
