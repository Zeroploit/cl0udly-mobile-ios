package app.ocmobile.platform

/** Desktop preview: there is no notification centre, so every call is a no-op. */
actual object PlatformNotify {
    actual fun requestPermission() = Unit
    actual fun authorized(): Boolean = false
    actual fun show(id: String, title: String, body: String, route: String?) = Unit
    actual fun cancel(id: String) = Unit
    actual fun cancelAll() = Unit
}
