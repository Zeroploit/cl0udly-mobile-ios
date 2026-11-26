package app.ocmobile.platform

/** The desktop preview has no installer: name it for what it is. */
actual fun appVersion(): String = "desktop preview"

actual fun deviceLabel(): String = "Desktop, " + System.getProperty("os.name")
