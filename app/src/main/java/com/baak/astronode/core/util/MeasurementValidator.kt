package com.baak.astronode.core.util

import com.baak.astronode.core.constants.AppConstants

/**
 * SQM değer doğrulaması — firebase/firestore.rules içindeki kuralla birebir aynı olmalı:
 * 0..MPSAS_MAX arası kaydedilebilir; MPSAS_MIN altı (gündüz/alacakaranlık) sadece test olarak.
 */
object MeasurementValidator {

    enum class Result { VALID, OUT_OF_NIGHT_RANGE, INVALID }

    fun validate(mpsas: Double): Result = when {
        mpsas.isNaN() || mpsas.isInfinite() -> Result.INVALID
        mpsas < 0.0 || mpsas > AppConstants.MPSAS_MAX -> Result.INVALID
        mpsas < AppConstants.MPSAS_MIN -> Result.OUT_OF_NIGHT_RANGE
        else -> Result.VALID
    }
}
