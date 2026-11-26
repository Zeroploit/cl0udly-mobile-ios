package app.ocmobile.platform

/**
 * Plugging in and running low, as the mascot needs them.
 *
 * [onChange] gets the current state as soon as it is registered, then every change, on the main
 * thread. Android read the system battery broadcast; iOS uses `UIDevice` in `iosMain`.
 */
expect object PlatformBattery {
    fun observe(onChange: (plugged: Boolean, low: Boolean) -> Unit)
}
