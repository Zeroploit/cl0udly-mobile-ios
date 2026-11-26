package app.ocmobile.platform

/** The Android copy of the shared module has no mascot sensors wired: the app module still owns them. */
actual object PlatformSensors {
    actual fun start(onTilt: (Float) -> Unit, onShake: () -> Unit) {}
    actual fun stop() {}
}
