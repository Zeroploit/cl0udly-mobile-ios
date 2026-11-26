package app.ocmobile.platform

import platform.Foundation.NSBundle
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
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

/** "iPhone17,1, iOS 26.1": the model identifier is what tells an iPhone 17 from a 16. */
actual fun deviceLabel(): String {
    val system = NSProcessInfo.processInfo.operatingSystemVersion
    val os = "iOS " + system.majorVersion + "." + system.minorVersion
    return UIDevice.currentDevice.model + ", " + os
}
