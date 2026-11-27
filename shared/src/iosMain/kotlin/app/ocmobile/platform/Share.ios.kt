@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * The system share sheet for several files plus an optional text, UIActivityViewController.
 *
 * Each file is written to a temp file first, because the sheet only takes URLs and strings.
 * On iPad the sheet is a popover, so it needs a source view: the app's root view. The sheet
 * always presents from [topViewController], so it still opens above an existing sheet.
 */
actual fun shareFiles(files: List<ShareFile>, text: String, onDone: () -> Unit) {
    if (files.isEmpty() && text.isBlank()) {
        onMain { onDone() }
        return
    }
    val items = mutableListOf<Any>()
    for (file in files) {
        val url = writeTemp(file.bytes, file.name)
        if (url != null) items += url
    }
    if (text.isNotBlank()) items += text
    if (items.isEmpty()) {
        onMain { onDone() }
        return
    }

    val host = topViewController()
    if (host == null) {
        onMain { onDone() }
        return
    }
    val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
    // iPad: a popover needs an anchor or it crashes; on iPhone this is simply ignored
    sheet.popoverPresentationController?.sourceView = rootView()
    host.presentViewController(sheet, animated = true, completion = null)
    onMain { onDone() }
}

/** The app's root view: the anchor the share popover points at, even with a sheet already up. */
private fun rootView(): UIView? {
    val app = UIApplication.sharedApplication
    val root = app.keyWindow?.rootViewController ?: rootFromScenes(app)
    return root?.view ?: fallbackView()
}

private fun rootFromScenes(app: UIApplication): UIViewController? {
    for (scene in app.connectedScenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        for (candidate in windowScene.windows) {
            (candidate as? UIWindow)?.rootViewController?.let { return it }
        }
    }
    return null
}

/** Last resort so a missing window can never crash the call: an off-screen anchor. */
private fun fallbackView(): UIView =
    UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
