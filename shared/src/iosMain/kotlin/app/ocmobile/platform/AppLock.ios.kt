@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.ocmobile.platform

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/**
 * App lock through LocalAuthentication: Face ID / Touch ID when enrolled, the device passcode
 * otherwise. `canEvaluatePolicy` asks the same question the unlock dialog would, without ever
 * showing UI. The LAPolicy constants come in as top-level values (the `LAPolicy.` member form
 * does not exist in this toolchain's bindings — verified by the CI build of 2026-11-27).
 */
actual val appLockAvailable: Boolean
    get() {
        val context = LAContext()
        return context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthenticationWithBiometrics, null) ||
            // passcode-only devices: no biometry enrolled, but the phone can still be unlocked
            context.canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, null)
    }

/**
 * The system unlock dialog, `LAContext.evaluatePolicy`. `LAPolicyDeviceOwnerAuthentication` covers
 * biometrics and falls back to the passcode sheet by itself, so one call serves every device.
 *
 * The reply arrives on a private queue; [onResult] is jumped to main with `onMain` like every
 * other seam callback, so callers can touch Compose state directly.
 */
actual fun authenticateAppLock(reason: String, onResult: (Boolean) -> Unit) {
    val context = LAContext()
    context.evaluatePolicy(
        LAPolicyDeviceOwnerAuthentication,
        localizedReason = reason,
    ) { success, _ ->
        // the error is set on failure and on cancel alike; both end as "locked"
        onMain { onResult(success) }
    }
}
