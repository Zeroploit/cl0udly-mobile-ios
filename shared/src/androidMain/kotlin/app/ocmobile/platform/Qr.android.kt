package app.ocmobile.platform

/**
 * Android stub actual. The Android app keeps its own UI in `:app`, where the scanner comes from
 * Play services; the shared code only runs this on iOS, so the stub just reports "cancelled".
 */
actual fun scanQrCode(onResult: (String?) -> Unit) {
    onResult(null)
}
