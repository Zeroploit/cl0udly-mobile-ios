package app.ocmobile.platform

/**
 * Desktop preview stub. The preview exists to look at the shared UI; it has no biometrics or
 * passcode to unlock with, so the lock reports unavailable and every attempt ends as locked.
 */
actual val appLockAvailable: Boolean
    get() = false

/** No unlock dialog on this platform: the caller treats false as locked. */
actual fun authenticateAppLock(reason: String, onResult: (Boolean) -> Unit) {
    onResult(false)
}
