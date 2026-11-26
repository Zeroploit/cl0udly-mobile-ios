package app.ocmobile.platform

import platform.Foundation.NSBundle
import platform.UIKit.UIDevice

/** "1.4.2 (312)" from the Info.plist, so bug reports name the build the user actually has. */
actual fun appVersion(): String {
    val info = NSBundle.mainBundle.infoDictionary ?: return ""
    val short = info["CFBundleShortVersionString"] as? String ?: ""
    val build = info["CFBundleVersion"] as? String ?: ""
    return when {
        short.isNotEmpty() && build.isNotEmpty() -> "$short ($build)"
        else -> short
    }
}

/** "iPhone, iOS 26.1": the model and the system version, both plain strings. */
actual fun deviceLabel(): String = UIDevice.currentDevice.model + ", iOS " + UIDevice.currentDevice.systemVersion
