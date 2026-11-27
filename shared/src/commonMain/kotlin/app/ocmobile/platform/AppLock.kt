package app.ocmobile.platform

/**
 * Whether app lock can run at all on this device: the platform has biometrics enrolled, or at
 * least a device passcode to fall back to. False means the lock screen should be skipped.
 */
expect val appLockAvailable: Boolean

/**
 * Runs the system app unlock (Face ID / Touch ID, with the device passcode as fallback).
 * [reason] is the text the system dialog shows under the prompt.
 * [onResult] gets true when the person unlocked, false when it failed or was cancelled.
 */
expect fun authenticateAppLock(reason: String, onResult: (Boolean) -> Unit)
