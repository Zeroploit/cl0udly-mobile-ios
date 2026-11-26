package app.ocmobile.platform

/**
 * Android stub. The shared UI runs on iOS; the Android app keeps its own screens in `:app`, where
 * the console was driven by an injected script. Opening the URL keeps the flow usable, and the
 * caller falls back to its paste step.
 */
actual fun openAuthSession(url: String, callbackScheme: String, onResult: (String?) -> Unit) {
    PlatformMedia.openUrl(url)
    onResult(null)
}

/** Nothing to dismiss: the stub never opens a sheet. */
actual fun closeAuthSession() { }
