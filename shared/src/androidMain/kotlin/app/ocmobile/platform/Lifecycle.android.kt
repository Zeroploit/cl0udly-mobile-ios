package app.ocmobile.platform

/** Android build of the shared library: the activity already reports this to `Chats.foreground`. */
actual object PlatformLifecycle {
    actual fun observe(onChange: (foreground: Boolean) -> Unit) = Unit
}
