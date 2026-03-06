package com.baak.astronode.data.sensor

import android.annotation.SuppressLint
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

    @SuppressLint("MissingPermission")
    fun startUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            AppConstants.LOCATION_INTERVAL_MS
        )
            .setMinUpdateDistanceMeters(AppConstants.LOCATION_MIN_DISTANCE_M)
            .build()

        fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    fun stopUpdates() {
        fusedClient.removeLocationUpdates(locationCallback)
    }

    fun getCurrentLocation(): LocationData? = _location.value
}
