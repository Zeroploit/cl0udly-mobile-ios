package app.ocmobile.platform

/**
 * Desktop stub actual. The preview window has no camera, so the scan ends as if it were
 * cancelled and the sheet keeps its manual fields.
 */
actual fun scanQrCode(onResult: (String?) -> Unit) {
    onResult(null)
}
