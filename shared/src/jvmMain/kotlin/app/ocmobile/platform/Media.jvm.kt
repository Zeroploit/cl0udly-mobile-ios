package app.ocmobile.platform

/**
 * Desktop preview stubs: the preview exists to look at the shared UI, and the system share
 * sheet, pickers and players are not part of what it previews.
 */
actual object PlatformMedia {
    actual fun shareFile(bytes: ByteArray, name: String, mime: String) { }
    actual fun saveFile(bytes: ByteArray, name: String, mime: String) { }
    actual fun shareText(text: String) { }
    actual fun openUrl(url: String) { }
    actual fun pickImages(multi: Boolean, onPicked: (List<PickedFile>) -> Unit) { onPicked(emptyList()) }
    actual fun takePhoto(onPicked: (PickedFile?) -> Unit) { onPicked(null) }
    actual fun pickFiles(onPicked: (List<PickedFile>) -> Unit) { onPicked(emptyList()) }
    actual fun playPcm(pcm: ShortArray, rate: Int) { }
    actual fun speak(text: String): Boolean = false
    actual fun stopSpeaking() { }
    actual fun speaking(): Boolean = false
}
