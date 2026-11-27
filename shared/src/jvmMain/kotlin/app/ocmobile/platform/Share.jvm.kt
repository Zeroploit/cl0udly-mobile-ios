package app.ocmobile.platform

/**
 * Desktop preview stubs: the preview exists to look at the shared UI, and the system share
 * sheet and PDF rendering are not part of what it previews.
 */
actual fun shareFiles(files: List<ShareFile>, text: String, onDone: () -> Unit) {
    onDone()
}
