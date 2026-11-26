package app.ocmobile.platform

/**
 * Device motion: [onTilt] is -1..1 (left/right), [onShake] fires on a sharp movement.
 *
 * Android read the accelerometer directly in the mascot; iOS answers with CoreMotion in
 * `iosMain`, and the Android copy of the shared module and the desktop preview stay silent.
 */
expect object PlatformSensors {
    /** Starts the updates; [onTilt] and [onShake] are called on the main thread. */
    fun start(onTilt: (Float) -> Unit, onShake: () -> Unit)

    /** Stops the updates; safe to call when nothing is running. */
    fun stop()
}
