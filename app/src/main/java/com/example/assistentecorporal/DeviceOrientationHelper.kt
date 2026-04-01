package com.example.assistentecorporal

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.roundToInt

class DeviceOrientationHelper(
    context: Context,
    private val onUpdate: (OrientationResult) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    fun register() {
        val sensor = rotationVectorSensor
        if (sensor == null) {
            onUpdate(
                OrientationResult(
                    available = false,
                    isAcceptable = true,
                    rollDegrees = 0,
                    message = "Sensor Rotation Vector indisponível."
                )
            )
            return
        }
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val rollDegrees = Math.toDegrees(orientationAngles[2].toDouble()).toFloat().roundToInt()
        val acceptable = abs(rollDegrees) <= ACCEPTABLE_ROLL_DEGREES

        onUpdate(
            OrientationResult(
                available = true,
                isAcceptable = acceptable,
                rollDegrees = rollDegrees,
                message = if (acceptable) {
                    "Telemóvel alinhado."
                } else {
                    "Endireita o telemóvel para melhorar a análise."
                }
            )
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val ACCEPTABLE_ROLL_DEGREES = 12
    }
}

data class OrientationResult(
    val available: Boolean,
    val isAcceptable: Boolean,
    val rollDegrees: Int,
    val message: String
)
