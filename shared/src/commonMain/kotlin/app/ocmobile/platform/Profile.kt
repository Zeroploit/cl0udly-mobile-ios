package app.ocmobile.platform

/**
 * Where the profile photo lives, per platform.
 *
 * Android decoded the picked image with `BitmapFactory`, squared and shrank it, and wrote a JPEG
 * into `filesDir`; commonMain cannot do any of that. iOS has no `filesDir` either: the bytes go to
 * the app's Documents folder through the platform layer.
 *
 * [saveAvatar] takes the picked bytes and returns the path (or URL) to hand to Coil;
 * [deleteAvatar] removes a previously saved photo and is a no-op when the path is unknown.
 */
expect object PlatformProfile {
    fun saveAvatar(bytes: ByteArray): String?
    fun deleteAvatar(path: String)
}
