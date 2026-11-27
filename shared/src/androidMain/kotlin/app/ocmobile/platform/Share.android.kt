package app.ocmobile.platform

/**
 * Android stub actual. The shared UI runs on iOS; the Android app keeps its own UI in `:app`,
 * where these came from the SDK. The stubs stay quiet so the module keeps compiling.
 */
actual fun shareFiles(files: List<ShareFile>, text: String, onDone: () -> Unit) {
    onDone()
}
