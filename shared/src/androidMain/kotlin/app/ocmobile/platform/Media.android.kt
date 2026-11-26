package app.ocmobile.platform

/**
 * Android stub actual. The shared UI runs on iOS; the Android app keeps its own UI in `:app`,
 * where these came from the SDK. The stubs stay quiet so the module keeps compiling.
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
