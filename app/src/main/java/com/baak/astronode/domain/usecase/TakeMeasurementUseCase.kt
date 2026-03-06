package com.baak.astronode.domain.usecase

import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.firebase.UserManager
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.data.sensor.OrientationProvider
import com.baak.astronode.data.usb.SqmUsbManager
import com.baak.astronode.data.usb.UsbConnectionState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

class TakeMeasurementUseCase @Inject constructor(
    private val sqmUsbManager: SqmUsbManager,
    private val locationProvider: LocationProvider,
    private val orientationProvider: OrientationProvider,
    private val firestoreManager: FirestoreManager,
    private val firebaseAuthManager: FirebaseAuthManager,
    private val userManager: UserManager
) {
    suspend operator fun invoke(
        orientationEnabled: Boolean,
        note: String?,
        sessionId: String? = null,
        sessionName: String? = null,
        isTest: Boolean = false
    ): Result<SkyMeasurement> { return try {
        if (sqmUsbManager.connectionState.value != UsbConnectionState.CONNECTED) {
            return Result.failure(Exception("SQM bağlı değil. USB cihazı bağlayıp izin verin."))
        }
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

            // e2) Gözlemci adı (UserManager'dan)
            val observerName = userManager.getUserProfile(uid).first()?.displayName?.takeIf { it.isNotBlank() } ?: ""

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
                sessionName = sessionName,
                isTest = isTest,
                observerUid = uid,
                observerName = observerName
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
}
