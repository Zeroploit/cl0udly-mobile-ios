@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicy
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics

/**
 * App lock through LocalAuthentication: Face ID / Touch ID when enrolled, the device passcode
 * otherwise. `canEvaluatePolicy` asks the same question the unlock dialog would, without ever
 * showing UI. NS_ENUM constants use the prefixed member form (`LAPolicy.LAPolicy...`), the form
 * verified to compile in this toolchain (see docs/PORT-BRIEF-2.md).
 */
actual val appLockAvailable: Boolean
    get() {
        val context = LAContext()
        return context.canEvaluatePolicy(LAPolicy.LAPolicyDeviceOwnerAuthenticationWithBiometrics, null) ||
            // passcode-only devices: no biometry enrolled, but the phone can still be unlocked
            context.canEvaluatePolicy(LAPolicy.LAPolicyDeviceOwnerAuthentication, null)
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
        LAPolicy.LAPolicyDeviceOwnerAuthentication,
        localizedReason = reason,
    ) { success, _ ->
        // the error is set on failure and on cancel alike; both end as "locked"
        onMain { onResult(success) }
    }
}
