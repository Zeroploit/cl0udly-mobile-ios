@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTType
import platform.UniformTypeIdentifiers.UTTypeData
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject

/**
 * iOS media shell: the share sheet, the photo/camera/document pickers and the small voices.
 *
 * Every entry point presents from [topViewController], so a picker opened from a sheet still
 * appears above it. UIKit keeps picker delegates weakly, so the active one is also held in
 * [activePickerDelegate] until the user picks or cancels.
 */
actual object PlatformMedia {

    private var soundPlayer: AVAudioPlayer? = null
    private val synthesizer = AVSpeechSynthesizer()

    actual fun shareFile(bytes: ByteArray, name: String, mime: String) {
        val url = writeTemp(bytes, name) ?: return
        presentItems(listOf(url))
    }

    /** iOS has no Downloads folder to write to; "Save to Files" in the share sheet is the target. */
    actual fun saveFile(bytes: ByteArray, name: String, mime: String) = shareFile(bytes, name, mime)

    actual fun shareText(text: String) {
        if (text.isBlank()) return
        presentItems(listOf(text))
    }

    actual fun openUrl(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(target, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    private fun presentItems(items: List<Any>) {
        val host = topViewController() ?: return
        val sheet = UIActivityViewController(activityItems = items, applicationActivities = null)
        host.presentViewController(sheet, animated = true, completion = null)
    }

    actual fun pickImages(multi: Boolean, onPicked: (List<PickedFile>) -> Unit) {
        if (activePickerDelegate != null) return
        val host = topViewController() ?: return onPicked(emptyList())
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = if (multi) 10L else 1L
            filter = PHPickerFilter.imagesFilter
        }
        val picker = PHPickerViewController(configuration = configuration)
        val delegate = PhotoDelegate(onPicked)
        activePickerDelegate = delegate
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }

    actual fun takePhoto(onPicked: (PickedFile?) -> Unit) {
        if (activePickerDelegate != null) return onPicked(null)
        val camera = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        if (!UIImagePickerController.isSourceTypeAvailable(camera)) return onPicked(null)
        val host = topViewController() ?: return onPicked(null)
        val picker = UIImagePickerController()
        val delegate = CameraDelegate(onPicked)
        activePickerDelegate = delegate
        picker.sourceType = camera
        picker.delegate = delegate
        host.presentViewController(picker, animated = true, completion = null)
    }

    actual fun pickFiles(onPicked: (List<PickedFile>) -> Unit) {
        if (activePickerDelegate != null) return onPicked(emptyList())
        val host = topViewController() ?: return onPicked(emptyList())
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeData))
        val delegate = FileDelegate(onPicked)
        activePickerDelegate = delegate
        picker.delegate = delegate
        picker.allowsMultipleSelection = true
        host.presentViewController(picker, animated = true, completion = null)
    }

    actual fun playPcm(pcm: ShortArray, rate: Int) {
        if (pcm.isEmpty()) return
        soundPlayer?.stop()
        val player = runCatching { AVAudioPlayer(data = wav(pcm, rate).toNSData(), error = null) }.getOrNull()
        if (player == null) { soundPlayer = null; return }
        player.prepareToPlay()
        soundPlayer = player
        player.play()
    }

    actual fun speak(text: String): Boolean {
        if (text.isBlank()) return false
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
        if (synthesizer.speaking) synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        // the system voice speaks the device language, as Android's default TextToSpeech did
        synthesizer.speakUtterance(utterance)
        return true
    }

    actual fun stopSpeaking() {
        if (synthesizer.speaking) synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    actual fun speaking(): Boolean = synthesizer.speaking

    /** 16-bit mono PCM wrapped in the 44-byte WAV header AVAudioPlayer needs. */
    private fun wav(pcm: ShortArray, rate: Int): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArray(44 + dataSize)
        fun ascii(offset: Int, text: String) {
            for (i in text.indices) out[offset + i] = text[i].code.toByte()
        }
        fun i32(offset: Int, value: Int) {
            out[offset] = (value and 0xff).toByte()
            out[offset + 1] = ((value shr 8) and 0xff).toByte()
            out[offset + 2] = ((value shr 16) and 0xff).toByte()
            out[offset + 3] = ((value shr 24) and 0xff).toByte()
        }
        fun i16(offset: Int, value: Int) {
            out[offset] = (value and 0xff).toByte()
            out[offset + 1] = ((value shr 8) and 0xff).toByte()
        }
        ascii(0, "RIFF"); i32(4, 36 + dataSize); ascii(8, "WAVE")
        ascii(12, "fmt "); i32(16, 16); i16(20, 1); i16(22, 1)
        i32(24, rate); i32(28, rate * 2); i16(32, 2); i16(34, 16)
        ascii(36, "data"); i32(40, dataSize)
        var at = 44
        for (sample in pcm) {
            val value = sample.toInt()
            out[at] = (value and 0xff).toByte()
            out[at + 1] = ((value shr 8) and 0xff).toByte()
            at += 2
        }
        return out
    }
}

private var activePickerDelegate: Any? = null

private fun finishPick(delegate: Any, onPicked: (List<PickedFile>) -> Unit, files: List<PickedFile>) {
    if (activePickerDelegate === delegate) activePickerDelegate = null
    onPicked(files)
}

private fun photoFile(data: NSData, suggested: String?): PickedFile {
    val bytes = data.toByteArray()
    val png = bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
    val mime = if (png) "image/png" else "image/jpeg"
    val extension = if (png) "png" else "jpg"
    val base = suggested?.substringAfterLast('/')?.trim()?.ifBlank { null } ?: "photo"
    val name = if (base.contains('.')) base else "$base.$extension"
    return PickedFile(bytes, mime, name)
}

private fun mimeOf(name: String): String {
    val extension = name.substringAfterLast('.', "")
    if (extension.isEmpty()) return "application/octet-stream"
    return UTType.typeWithFilenameExtension(extension)?.preferredMIMEType ?: "application/octet-stream"
}

private class PhotoDelegate(
    private val onPicked: (List<PickedFile>) -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val results = didFinishPicking.filterIsInstance<PHPickerResult>()
        val out = mutableListOf<PickedFile>()

        fun loadAt(index: Int) {
            if (index >= results.size) {
                finishPick(this, onPicked, out)
                return
            }
            val provider = results[index].itemProvider
            provider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
                onMain {
                    if (data != null) out += photoFile(data, provider.suggestedName)
                    loadAt(index + 1)
                }
            }
        }
        loadAt(0)
    }
}

private class CameraDelegate(
    private val onPicked: (PickedFile?) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(picker: UIImagePickerController, didFinishPickingMediaWithInfo: Map<Any?, *>) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val data = image?.let { UIImageJPEGRepresentation(it, 0.92) }
        val file = data?.let { PickedFile(it.toByteArray(), "image/jpeg", "photo.jpg") }
        picker.dismissViewControllerAnimated(true) {
            activePickerDelegate = null
            onPicked(file)
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            activePickerDelegate = null
            onPicked(null)
        }
    }
}

private class FileDelegate(
    private val onPicked: (List<PickedFile>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val out = mutableListOf<PickedFile>()
        for (candidate in didPickDocumentsAtURLs) {
            val url = candidate as? NSURL ?: continue
            val access = url.startAccessingSecurityScopedResource()
            try {
                val data = NSData.dataWithContentsOfURL(url) ?: continue
                val name = url.lastPathComponent ?: "file"
                out += PickedFile(data.toByteArray(), mimeOf(name), name)
            } finally {
                if (access) url.stopAccessingSecurityScopedResource()
            }
        }
        activePickerDelegate = null
        onPicked(out)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        activePickerDelegate = null
        onPicked(emptyList())
    }
}
