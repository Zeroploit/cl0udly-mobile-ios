package app.ocmobile.platform

/**
 * Android stub actual. The shared UI runs on iOS; PDF rendering lives over there. The stub
 * stays quiet so the module keeps compiling.
 */
actual fun chatPdf(title: String, lines: List<String>, onResult: (ByteArray?) -> Unit) {
    onResult(null)
}
