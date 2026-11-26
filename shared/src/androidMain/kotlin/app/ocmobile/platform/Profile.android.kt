package app.ocmobile.platform

import java.io.File

/** The Android build of the shared library keeps the photo in its own cache folder. */
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
