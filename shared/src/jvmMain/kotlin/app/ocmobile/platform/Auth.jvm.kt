package app.ocmobile.platform

/**
 * Desktop preview stub. The preview exists to look at the shared UI; it has no auth sheet to hand
 * a URL back, so the page opens through [PlatformMedia] and the caller keeps its paste step.
 */
actual fun openAuthSession(url: String, callbackScheme: String, onResult: (String?) -> Unit) {
    PlatformMedia.openUrl(url)
    onResult(null)
}

/** Nothing to dismiss: the stub never opens a sheet. */
actual fun closeAuthSession() { }
