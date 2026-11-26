@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Small bridges the iOS actuals share: bytes to `NSData` and back, a temp file for the share
 * sheet and the players, the view controller to present from, and the main queue.
 */

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { pinned -> NSData.dataWithBytes(pinned.addressOf(0), size.toULong()) }

internal fun NSData.toByteArray(): ByteArray {
    val size = length
    if (size == 0UL) return ByteArray(0)
    return bytes?.readBytes(size.toInt()) ?: ByteArray(0)
}

/** A safe file name: the last path component, never empty. */
internal fun safeName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
    return base.ifBlank { "file" }
}

/** The temporary file the share sheet and the players read, written atomically. */
internal fun writeTemp(bytes: ByteArray, name: String): NSURL? {
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + safeName(name))
    val path = url.path ?: return null
    return if (bytes.toNSData().writeToFile(path, true)) url else null
}

/** The controller on screen, past any sheet or alert already presented. */
internal fun topViewController(): UIViewController? {
    val app = UIApplication.sharedApplication
    val window = app.keyWindow ?: firstWindow(app)
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

private fun firstWindow(app: UIApplication): UIWindow? {
    for (scene in app.connectedScenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        for (candidate in windowScene.windows) {
            (candidate as? UIWindow)?.let { return it }
        }
    }
    return null
}

/** UIKit work has to run on the main thread; the pickers' callbacks arrive on other queues. */
internal fun onMain(block: () -> Unit) {
    dispatch_async(dispatch_get_main_queue()) { block() }
}
