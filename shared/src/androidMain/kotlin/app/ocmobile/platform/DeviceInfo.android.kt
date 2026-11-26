package app.ocmobile.platform

import android.os.Build

/**
 * The Android build of the shared library has no `BuildConfig` of its own (the shipping app in
 * `:app` keeps its duplicate sources), so only the device part is known here.
 */
actual fun appVersion(): String = "Android"

actual fun deviceLabel(): String =
    Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE
