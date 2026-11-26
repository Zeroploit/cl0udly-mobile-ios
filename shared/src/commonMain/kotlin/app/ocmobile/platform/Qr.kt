package app.ocmobile.platform

/** Opens the camera to read a QR code; [onResult] gets the text, or null if cancelled. */
expect fun scanQrCode(onResult: (String?) -> Unit)
