package app.ocmobile.platform

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

actual fun nowStamp(): String {
    val z = ZonedDateTime.now()
    val fmt = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH)
    return "Now: " + z.format(fmt) + " (" + z.zone.id + ")"
}