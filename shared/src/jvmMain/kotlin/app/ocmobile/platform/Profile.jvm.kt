package app.ocmobile.platform

import java.io.File

/**
 * Desktop preview: the photo is written next to the preview's working directory so Coil can show it.
 * There is no gallery to pick from in the preview, so this path is only exercised by tests.
 */
actual object PlatformProfile {
    actual fun saveAvatar(bytes: ByteArray): String? = runCatching {
        val f = File(System.getProperty("java.io.tmpdir"), "ocmobile-avatar-" + System.nanoTime() + ".jpg")
        f.writeBytes(bytes)
        f.path
    }.getOrNull()

    actual fun deleteAvatar(path: String) {
        runCatching { if (path.isNotBlank()) File(path).delete() }
    }
}
