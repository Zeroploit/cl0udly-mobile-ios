package app.ocmobile.platform

/** The desktop preview runs on mains: it never charges and never runs low. */
actual object PlatformBattery {
    actual fun observe(onChange: (plugged: Boolean, low: Boolean) -> Unit) {}
}
