package app.ocmobile.platform

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

actual fun nowStamp(): String {
    val f = NSDateFormatter()
    // English names, always: the agent reads this, and the app's own language is not its business
    f.locale = NSLocale("en_US_POSIX")
    f.dateFormat = "EEEE, d MMMM yyyy, HH:mm"
    val zone = NSTimeZone.localTimeZone.name
    return "Now: " + f.stringFromDate(NSDate()) + " (" + zone + ")"
}