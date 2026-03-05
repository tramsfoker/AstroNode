package com.baak.astronode.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    val isSensorAvailable: Boolean = run {
        when {
            rotationVectorSensor != null -> true
            accelerometerSensor != null && magneticSensor != null -> true
            else -> false
        }
    }

    private val _orientationState = MutableStateFlow<OrientationData?>(null)
    val orientationState: StateFlow<OrientationData?> = _orientationState.asStateFlow()

    private val lowPassAlpha = 0.15f
    private var filteredAzimuth = 0f
    private var filteredPitch = 0f
    private var filteredRoll = 0f
    private var isFirstSample = true

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var lastGravity: FloatArray? = null
    private var lastGeomagnetic: FloatArray? = null

    private val rotationVectorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)

            updateOrientationFromAngles()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val accelMagneticListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> lastGravity = event.values.clone()
                Sensor.TYPE_MAGNETIC_FIELD -> lastGeomagnetic = event.values.clone()
            }
            val gravity = lastGravity ?: return
            val geomagnetic = lastGeomagnetic ?: return

            if (!SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) return

            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            updateOrientationFromAngles()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateOrientationFromAngles() {
        val azimuthRad = orientationAngles[0]
        val pitchRad = orientationAngles[1]
        val rollRad = orientationAngles[2]

        val azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
        val pitchDeg = Math.toDegrees(pitchRad.toDouble()).toFloat()
        val rollDeg = Math.toDegrees(rollRad.toDouble()).toFloat()

        val azimuthNorm = (azimuthDeg + 360) % 360
        val pitchNorm = pitchDeg.coerceIn(-90f, 90f)
        val rollNorm = rollDeg.coerceIn(-180f, 180f)

        if (isFirstSample) {
            filteredAzimuth = azimuthNorm
            filteredPitch = pitchNorm
            filteredRoll = rollNorm
            isFirstSample = false
        } else {
            filteredAzimuth = lowPass(filteredAzimuth, azimuthNorm)
            filteredPitch = lowPass(filteredPitch, pitchNorm)
            filteredRoll = lowPass(filteredRoll, rollNorm)
        }

        _orientationState.value = OrientationData(
            azimuth = filteredAzimuth,
            pitch = filteredPitch,
            roll = filteredRoll
        )
    }

    private fun lowPass(prev: Float, current: Float): Float {
        return prev + lowPassAlpha * (current - prev)
    }

    fun startListening() {
        if (!isSensorAvailable) {
            _orientationState.value = null
            return
        }
        isFirstSample = true
        lastGravity = null
        lastGeomagnetic = null

        when {
            rotationVectorSensor != null -> {
                sensorManager.registerListener(
                    rotationVectorListener,
                    rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
            accelerometerSensor != null && magneticSensor != null -> {
                sensorManager.registerListener(
                    accelMagneticListener,
                    accelerometerSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                sensorManager.registerListener(
                    accelMagneticListener,
                    magneticSensor,
                    SensorManager.SENSOR_DELAY_UI
                )
            }
        }
    }

    fun stopListening() {
        sensorManager.unregisterListener(rotationVectorListener)
        sensorManager.unregisterListener(accelMagneticListener)
        _orientationState.value = null
    }

    fun hasSensor(): Boolean = isSensorAvailable
}
