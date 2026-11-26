@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile

/**
 * The profile photo lives in the app's Documents folder: Coil loads a plain file path, and the
 * old photo is removed so the folder does not grow with every change.
 */
actual object PlatformProfile {
    private fun documents(): String? =
        NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String

    actual fun saveAvatar(bytes: ByteArray): String? {
        val dir = documents() ?: return null
        val stamp = (NSDate().timeIntervalSince1970 * 1000).toLong()
        val path = dir + "/avatar-" + stamp + ".jpg"
        val data = bytes.toNSData()
        return if (data.writeToFile(path, true)) path else null
    }

    actual fun deleteAvatar(path: String) {
        if (path.isNotBlank()) NSFileManager.defaultManager.removeItemAtPath(path, null)
    }
}
