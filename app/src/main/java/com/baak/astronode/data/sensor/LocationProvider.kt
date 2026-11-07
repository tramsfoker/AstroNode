package com.baak.astronode.data.sensor

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.os.Looper
import com.baak.astronode.core.constants.AppConstants
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LocationData(
    val lat: Double,
    val lng: Double,
    val altitude: Double?,
    val accuracy: Float?
)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val _location = MutableStateFlow<LocationData?>(null)
    val location: StateFlow<LocationData?> = _location.asStateFlow()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                _location.value = LocationData(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    altitude = if (loc.hasAltitude()) loc.altitude else null,
                    accuracy = if (loc.hasAccuracy()) loc.accuracy else null
                )
            }
        }
    }

    private val _hasPermission = MutableStateFlow(checkPermission())
    /** Konum izni var mı (yaklaşık konum da yeterli). UI izin isteğini buna göre gösterir. */
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var updatesRequested = false
    private var activeUsers = 0

    fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Konum güncellemelerini başlatır. İzin yoksa sessizce bekler (önceki sürüm izin olmadan
     * çağırıyordu → SecurityException). Birden fazla ekran kullanabilir; sayaçla yönetilir.
     */
    @SuppressLint("MissingPermission") // izin hemen yukarıda kontrol ediliyor
    fun startUpdates() {
        activeUsers++
        requestIfPossible()
    }

    /** İzin verildikten sonra UI çağırır. */
    fun onPermissionResult() {
        _hasPermission.value = checkPermission()
        requestIfPossible()
    }

    @SuppressLint("MissingPermission")
    private fun requestIfPossible() {
        _hasPermission.value = checkPermission()
        if (!_hasPermission.value || updatesRequested || activeUsers <= 0) return
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            AppConstants.LOCATION_INTERVAL_MS
        )
            .setMinUpdateDistanceMeters(AppConstants.LOCATION_MIN_DISTANCE_M)
            .build()
        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            updatesRequested = true
            // İlk konumu beklemeden son bilinen konumu kullan
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && _location.value == null) {
                    _location.value = LocationData(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        altitude = if (loc.hasAltitude()) loc.altitude else null,
                        accuracy = if (loc.hasAccuracy()) loc.accuracy else null
                    )
                }
            }
        } catch (e: SecurityException) {
            _hasPermission.value = false
        }
    }

    fun stopUpdates() {
        activeUsers = (activeUsers - 1).coerceAtLeast(0)
        if (activeUsers == 0 && updatesRequested) {
            fusedClient.removeLocationUpdates(locationCallback)
            updatesRequested = false
        }
    }

    fun getCurrentLocation(): LocationData? = _location.value
}
