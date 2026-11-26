package app.ocmobile.platform

/**
 * Android build of the shared library: the shipping app has its own `LiveNotify` and posts from
 * the activity, so this seam stays silent instead of posting twice.
 */
actual object PlatformNotify {
    actual fun requestPermission() = Unit
    actual fun authorized(): Boolean = false
    actual fun show(id: String, title: String, body: String, route: String?) = Unit
    actual fun cancel(id: String) = Unit
    actual fun cancelAll() = Unit
}
