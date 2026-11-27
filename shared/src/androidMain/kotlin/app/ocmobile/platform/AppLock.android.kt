package app.ocmobile.platform

/**
 * Android stub. The shared UI runs on iOS; the Android app keeps its own screens in `:app`.
 * There is nothing to unlock with here, so the lock reports unavailable and every attempt ends
 * as locked, without crashing.
 */
actual val appLockAvailable: Boolean
    get() = false

/** No unlock dialog on this platform: the caller treats false as locked. */
actual fun authenticateAppLock(reason: String, onResult: (Boolean) -> Unit) {
    onResult(false)
}
