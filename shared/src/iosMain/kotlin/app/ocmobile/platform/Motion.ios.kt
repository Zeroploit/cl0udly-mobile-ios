package app.ocmobile.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification

/**
 * iOS asks for less motion in Settings › Accessibility › Motion; honour it the way Android
 * honoured the animator scale and battery saver.
 */
@Composable
actual fun rememberMotionEnabled(): Boolean {
    var enabled by remember { mutableStateOf(!UIAccessibilityIsReduceMotionEnabled()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val token = center.addObserverForName(
            name = UIAccessibilityReduceMotionStatusDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { enabled = !UIAccessibilityIsReduceMotionEnabled() }
        onDispose { center.removeObserver(token) }
    }
    return enabled
}
