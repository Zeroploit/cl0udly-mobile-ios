package app.ocmobile.platform

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification
import platform.UIKit.UIDeviceBatteryState
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification

/** The observers of the last [PlatformBattery.observe] call: kept so a later call can remove them. */
private var batteryObservers: List<Any> = emptyList()

/**
 * The battery as the mascot needs it: charging (or full) and running low.
 *
 * Android read the sticky `ACTION_BATTERY_CHANGED` broadcast; iOS needs battery monitoring on and
 * two `UIDevice` notifications. The current state is emitted as soon as it is registered.
 */
actual object PlatformBattery {

    private const val LOW = 0.15f

    actual fun observe(onChange: (plugged: Boolean, low: Boolean) -> Unit) {
        val center = NSNotificationCenter.defaultCenter
        for (observer in batteryObservers) center.removeObserver(observer)
        batteryObservers = emptyList()
        val device = UIDevice.currentDevice
        device.batteryMonitoringEnabled = true
        fun emit() {
            val plugged = device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateCharging ||
                device.batteryState == UIDeviceBatteryState.UIDeviceBatteryStateFull
            val level = device.batteryLevel
            // -1 means "unknown": battery monitoring had not produced a reading yet
            val low = !plugged && level >= 0f && level <= LOW
            onMain { onChange(plugged, low) }
        }
        emit()
        val state = center.addObserverForName(
            name = UIDeviceBatteryStateDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { emit() }
        val level = center.addObserverForName(
            name = UIDeviceBatteryLevelDidChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { emit() }
        batteryObservers = listOfNotNull(state, level)
    }
}
