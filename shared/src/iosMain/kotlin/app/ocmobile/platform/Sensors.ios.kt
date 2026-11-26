@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * CoreMotion accelerometer: the same readings Android's `SensorEventListener` got, at the same
 * 0.1 s beat.
 *
 * CoreMotion reports acceleration in g, Android in m/s²; the samples are scaled so the tilt
 * formula from the Android mascot (a low pass, then `-x / 6`) gives the same -1..1 range.
 */
actual object PlatformSensors {

    private const val GRAVITY = 9.80665
    // a sharp movement: about 2 g for two samples in a row
    private const val SHAKE_G = 2.2f

    private var manager: CMMotionManager? = null

    actual fun start(onTilt: (Float) -> Unit, onShake: () -> Unit) {
        stop()
        val motion = CMMotionManager()
        if (!motion.isAccelerometerAvailable()) return
        motion.accelerometerUpdateInterval = 0.1
        manager = motion
        var smoothedX = 0f
        var lastTilt = 0f
        var hot = 0
        motion.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            val sample = data ?: return@startAccelerometerUpdatesToQueue
            val (gx, gy, gz) = sample.acceleration.useContents { Triple(x, y, z) }
            val ax = (gx * GRAVITY).toFloat()
            // tilt: the Android low pass, then the same -1..1 window
            smoothedX = smoothedX * 0.85f + ax * 0.15f
            val next = (-smoothedX / 6f).coerceIn(-1f, 1f)
            if (abs(next - lastTilt) > 0.08f) {
                lastTilt = next
                onMain { onTilt(next) }
            }
            // shake: two samples in a row well above the resting magnitude; then reset, so a burst
            // that keeps shaking can report again once the dizzy guard in the mascot lets it
            val magnitude = sqrt((gx * gx + gy * gy + gz * gz)).toFloat()
            hot = if (magnitude > SHAKE_G) hot + 1 else 0
            if (hot >= 2) {
                hot = 0
                onMain { onShake() }
            }
        }
    }

    actual fun stop() {
        manager?.stopAccelerometerUpdates()
        manager = null
    }
}
