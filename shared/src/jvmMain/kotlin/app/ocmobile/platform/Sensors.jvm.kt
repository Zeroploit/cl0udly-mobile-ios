package app.ocmobile.platform

/** The desktop preview has no accelerometer: it stays still. */
actual object PlatformSensors {
    actual fun start(onTilt: (Float) -> Unit, onShake: () -> Unit) {}
    actual fun stop() {}
}
