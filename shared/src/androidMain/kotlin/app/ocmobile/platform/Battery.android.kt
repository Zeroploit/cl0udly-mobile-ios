package app.ocmobile.platform

/** The Android copy of the shared module has no battery feed wired: the app module still owns it. */
actual object PlatformBattery {
    actual fun observe(onChange: (plugged: Boolean, low: Boolean) -> Unit) {}
}
