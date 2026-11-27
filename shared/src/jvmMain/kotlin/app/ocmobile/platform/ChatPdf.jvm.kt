package app.ocmobile.platform

/**
 * Desktop preview stubs: the preview exists to look at the shared UI, and PDF rendering is
 * not part of what it previews.
 */
actual fun chatPdf(title: String, lines: List<String>, onResult: (ByteArray?) -> Unit) {
    onResult(null)
}
