package app.ocmobile.platform

/**
 * A file the platform picker handed over: bytes in memory, its name and MIME type.
 */
class PickedFile(val bytes: ByteArray, val mime: String, val name: String)

/**
 * The platform's media shell: share/save sheets, photo/camera/file pickers and a one-shot sound.
 *
 * Android had all of these through the SDK (MediaStore, Intent choosers, MediaPlayer). iOS answers
 * with UIActivityViewController, PHPicker/UIImagePicker/UIDocumentPicker and AVAudioPlayer in
 * `iosMain`; the desktop preview and the Android copy of the shared module answer with stubs.
 */
expect object PlatformMedia {
    /** The system share sheet for a file. */
    fun shareFile(bytes: ByteArray, name: String, mime: String)

    /** Save to the user's Files/Downloads; on iOS the share sheet is used as the save target too. */
    fun saveFile(bytes: ByteArray, name: String, mime: String)

    /** The system share sheet for a text (a link, a copied answer). */
    fun shareText(text: String)

    /** The photo library picker; [multi] allows more than one photo. Returns empty on cancel. */
    fun pickImages(multi: Boolean, onPicked: (List<PickedFile>) -> Unit)

    /** The camera; null if cancelled or unavailable. */
    fun takePhoto(onPicked: (PickedFile?) -> Unit)

    /** The document picker for any file type. Returns empty on cancel. */
    fun pickFiles(onPicked: (List<PickedFile>) -> Unit)

    /** Play a synthesised 16-bit mono PCM note. */
    fun playPcm(pcm: ShortArray, rate: Int)

    /** Speak a text with the platform voice; false when the platform has none. */
    fun speak(text: String): Boolean

    /** Stop speaking. */
    fun stopSpeaking()

    /** Whether the platform voice is currently speaking. */
    fun speaking(): Boolean
}
