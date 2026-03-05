package com.baak.astronode.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.baak.astronode.core.constants.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class OrientationData(
    val azimuth: Float,
    val pitch: Float,
    val roll: Float
)

@Singleton
class OrientationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _orientation = MutableStateFlow<OrientationData?>(null)
    val orientation: StateFlow<OrientationData?> = _orientation.asStateFlow()

    val isSensorAvailable: Boolean get() = rotationSensor != null

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var filteredAzimuth = 0f
    private var filteredPitch = 0f
    private var filteredRoll = 0f
    private var initialized = false

    fun startListening() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        _orientation.value = null
        initialized = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat().let {
            if (it < 0) it + 360f else it
        }
        val pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        val alpha = AppConstants.ORIENTATION_LOW_PASS_ALPHA
        if (!initialized) {
            filteredAzimuth = azDeg
            filteredPitch = pitchDeg
            filteredRoll = rollDeg
            initialized = true
        } else {
            filteredAzimuth = lowPass(filteredAzimuth, azDeg, alpha)
            filteredPitch = lowPass(filteredPitch, pitchDeg, alpha)
            filteredRoll = lowPass(filteredRoll, rollDeg, alpha)
        }

        _orientation.value = OrientationData(
            azimuth = filteredAzimuth,
            pitch = filteredPitch,
            roll = filteredRoll
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPass(previous: Float, current: Float, alpha: Float): Float =
        previous + alpha * (current - previous)

    fun getCurrentOrientation(): OrientationData? = _orientation.value
}
