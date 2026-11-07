package com.baak.astronode.core.util

import com.baak.astronode.core.util.MeasurementValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Test

/** firebase/firestore.rules ile aynı sınırlar: 0..25 kaydedilir, 10 altı sadece test. */
class MeasurementValidatorTest {

    @Test
    fun `gece aralığı geçerli`() {
        assertEquals(Result.VALID, MeasurementValidator.validate(10.0))
        assertEquals(Result.VALID, MeasurementValidator.validate(21.5))
        assertEquals(Result.VALID, MeasurementValidator.validate(25.0))
    }

    @Test
    fun `gündüz değerleri test olarak işaretlenir`() {
        assertEquals(Result.OUT_OF_NIGHT_RANGE, MeasurementValidator.validate(0.0))
        assertEquals(Result.OUT_OF_NIGHT_RANGE, MeasurementValidator.validate(9.99))
    }

    @Test
    fun `fiziksel olmayan değerler reddedilir`() {
        assertEquals(Result.INVALID, MeasurementValidator.validate(-0.01))
        assertEquals(Result.INVALID, MeasurementValidator.validate(25.01))
        assertEquals(Result.INVALID, MeasurementValidator.validate(Double.NaN))
        assertEquals(Result.INVALID, MeasurementValidator.validate(Double.POSITIVE_INFINITY))
    }
}
