package com.baak.astronode.domain.usecase

import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.data.sensor.OrientationProvider
import com.baak.astronode.data.usb.SqmUsbManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class TakeMeasurementUseCase @Inject constructor(
    private val sqmUsbManager: SqmUsbManager,
    private val locationProvider: LocationProvider,
    private val orientationProvider: OrientationProvider,
    private val firestoreManager: FirestoreManager,
    private val firebaseAuthManager: FirebaseAuthManager
) {
    suspend operator fun invoke(
        orientationEnabled: Boolean,
        note: String?,
        sessionId: String? = null,
        sessionName: String? = null
    ): Result<SkyMeasurement> = try {
        withTimeout(15000L) {
            // a) SQM'den MPSAS oku
            val sqmReading = sqmUsbManager.readMeasurement()
                ?: return@withTimeout Result.failure(Exception("SQM okunamadı"))

            // b) Bortle sınıfı hesapla
            val bortleClass = BortleScale.toBortleClass(sqmReading.mpsas)

            // c) Anlık konum al
            val location = locationProvider.getCurrentLocation()
                ?: return@withTimeout Result.failure(Exception("Konum alınamadı"))

            // d) Oryantasyon verisini al (sensör yoksa null, hata verme)
            val orientation = if (orientationEnabled) {
                orientationProvider.getCurrentOrientation()
            } else null

            // e) Firebase anonim auth → UID
            val uid = try {
                firebaseAuthManager.ensureAnonymousAuth()
            } catch (e: Exception) {
                return@withTimeout Result.failure(Exception("Kimlik doğrulama başarısız: ${e.message}"))
            }

            // f) SkyMeasurement oluştur
            val measurement = SkyMeasurement(
                sqmValue = sqmReading.mpsas,
                bortleClass = bortleClass,
                latitude = location.lat,
                longitude = location.lng,
                altitude = location.altitude,
                azimuth = orientation?.azimuth,
                pitch = orientation?.pitch,
                roll = orientation?.roll,
                orientationEnabled = orientationEnabled,
                deviceId = uid,
                note = note?.takeIf { it.isNotBlank() },
                sessionId = sessionId,
                sessionName = sessionName
            )

            // g) Firestore'a kaydet
            val saveResult = firestoreManager.saveMeasurement(measurement)
            if (saveResult.isFailure) {
                return@withTimeout Result.failure(
                    saveResult.exceptionOrNull() ?: Exception("Firestore kayıt hatası")
                )
            }

            // h) Başarılı sonuç
            Result.success(measurement)
        }
    } catch (e: TimeoutCancellationException) {
        Result.failure(Exception("Ölçüm zaman aşımına uğradı"))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
