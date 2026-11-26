package app.ocmobile.platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification

/**
 * iOS foreground and background, the pair Android read from the activity lifecycle.
 *
 * `UIApplicationWillResignActiveNotification` also fires for a control-centre pull or an incoming
 * call, not only for a real background trip; it is the closest signal without iOS 13 scene
 * phases, and the same holds for `UIApplicationDidBecomeActiveNotification` on the way back.
 *
 * The app starts in the foreground, so [foreground] does too; the two notifications keep it right
 * from there. The centre keeps its observers weakly: without the two fields below they die as soon
 * as [observe] returns and the callbacks stop arriving.
 */
actual object PlatformLifecycle {

    private var foreground = true
    private var resignObserver: Any? = null
    private var activeObserver: Any? = null

    actual fun observe(onChange: (foreground: Boolean) -> Unit) {
        val center = NSNotificationCenter.defaultCenter
        resignObserver?.let { center.removeObserver(it) }
        activeObserver?.let { center.removeObserver(it) }
        resignObserver = center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            foreground = false
            onChange(false)
        }
        activeObserver = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            foreground = true
            onChange(true)
        }
        // Say where the app stands right now, not only on the next change.
        onChange(foreground)
    }
}
