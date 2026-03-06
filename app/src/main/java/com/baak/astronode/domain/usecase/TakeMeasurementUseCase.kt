package com.baak.astronode.domain.usecase

import com.baak.astronode.core.model.SkyMeasurement
import com.baak.astronode.core.util.BortleScale
import com.baak.astronode.core.util.MoonCalc
import com.baak.astronode.core.util.ObservingScore
import com.baak.astronode.core.util.SunCalc
import com.baak.astronode.data.api.WeatherService
import com.baak.astronode.data.firebase.FirebaseAuthManager
import com.baak.astronode.data.firebase.FirestoreManager
import com.baak.astronode.data.firebase.UserManager
import com.baak.astronode.data.sensor.LocationProvider
import com.baak.astronode.core.util.NetworkMonitor
import com.baak.astronode.data.sensor.OrientationProvider
import com.baak.astronode.data.usb.SqmUsbManager
import com.baak.astronode.data.usb.UsbConnectionState
import android.util.Log
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
    private val userManager: UserManager,
    private val weatherService: WeatherService,
    private val networkMonitor: NetworkMonitor
) {
    suspend operator fun invoke(
        orientationEnabled: Boolean,
        note: String?,
        sessionId: String? = null,
        sessionName: String? = null,
        isTest: Boolean = false,
        forceDaytimeTest: Boolean = false
    ): Result<SkyMeasurement> { return try {
        Log.d("MEASURE", "UseCase başladı")
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

            // e3) Ay evresi (her zaman offline)
            val moonData = MoonCalc.getMoonPhase()

            // e4) Hava durumu (online ise)
            val weatherData = if (networkMonitor.isOnline.value) {
                try {
                    weatherService.getWeatherData(location.lat, location.lng)
                } catch (_: Exception) { null }
            } else null

            // e5) Gözlem skoru hesapla
            val observingCondition = ObservingScore.calculate(weatherData, moonData)

            // e6) Gözlem zamanı kontrolü
            val timeStatus = SunCalc.isGoodTimeForObserving(location.lat, location.lng)
            val isDaytime = !timeStatus.canObserve
            val effectiveIsTest = isTest || forceDaytimeTest || isDaytime

            // e7) Ölçüm saati (HH:mm)
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() }
            val measurementTime = "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))

            // f) SkyMeasurement oluştur — TÜM verileri birleştir
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
                isTest = effectiveIsTest,
                observerUid = uid,
                observerName = observerName,
                weather = weatherData,
                temperature = weatherData?.temperature,
                humidity = weatherData?.humidity,
                cloudCover = weatherData?.cloudCover,
                windSpeed = weatherData?.windSpeed,
                visibility = weatherData?.visibility,
                moonPhase = moonData.phaseName,
                moonIllumination = moonData.illumination,
                moonEmoji = moonData.emoji,
                observingScore = observingCondition.score,
                observingRating = observingCondition.rating,
                isDaytime = isDaytime,
                measurementTime = measurementTime
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
