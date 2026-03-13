package com.example.suraksha.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * ShakeDetector — triggers SOS when the phone is shaken continuously
 * for approximately 10 seconds.
 *
 * Algorithm:
 *  1. Compute acceleration magnitude minus gravity (~9.81 m/s²).
 *  2. If residual > [SHAKE_THRESHOLD], count as a shake impulse.
 *  3. Maintain a sliding window of [SUSTAINED_DURATION_MS].
 *  4. Fire when >= [MIN_SHAKES] impulses accumulate in the window.
 *  5. 30-second cooldown after each trigger.
 */
class ShakeDetector(private val context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "ShakeDetector"
        private const val SHAKE_THRESHOLD = 11.0f          // m/s² above gravity
        private const val SUSTAINED_DURATION_MS = 10_000L  // 10 seconds
        private const val MIN_SHAKES = 60                  // impulses required
        private const val COOLDOWN_MS = 30_000L            // post-trigger cooldown
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var onShakeDetected: (() -> Unit)? = null
    private var isListening = false
    private val timestamps = mutableListOf<Long>()
    private var lastTriggerTime = 0L

    fun setOnShakeListener(listener: () -> Unit) { onShakeDetected = listener }

    fun startListening() {
        if (isListening) return
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) {
            Log.w(TAG, "No accelerometer available"); return
        }
        sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        isListening = true
        Log.d(TAG, "Shake detection started")
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        timestamps.clear()
        isListening = false
        Log.d(TAG, "Shake detection stopped")
    }

    fun isActive(): Boolean = isListening

    fun reset() { timestamps.clear(); lastTriggerTime = 0L }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val mag = sqrt(
            event.values[0] * event.values[0] +
            event.values[1] * event.values[1] +
            event.values[2] * event.values[2]
        )
        val delta = mag - SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        if (now - lastTriggerTime < COOLDOWN_MS) return

        if (delta >= SHAKE_THRESHOLD) timestamps.add(now)
        timestamps.removeAll { now - it > SUSTAINED_DURATION_MS }

        if (timestamps.size >= MIN_SHAKES) {
            Log.d(TAG, "Sustained shake detected (${timestamps.size} impulses)")
            lastTriggerTime = now
            timestamps.clear()
            onShakeDetected?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
