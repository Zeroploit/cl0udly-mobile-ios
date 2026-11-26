package app.ocmobile.platform

/** Desktop preview: the window is always in front, so there is nothing to observe. */
actual object PlatformLifecycle {
    actual fun observe(onChange: (foreground: Boolean) -> Unit) = Unit
}
