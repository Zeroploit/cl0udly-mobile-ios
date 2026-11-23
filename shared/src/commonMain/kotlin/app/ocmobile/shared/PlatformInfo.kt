package app.ocmobile.shared

/**
 * What the shared code is running on.
 *
 * iOS cannot start the local OpenCode runtime: the app would have to unpack an
 * Ubuntu rootfs and exec an unsigned Linux binary, which iOS forbids. On iOS the
 * app is a client for an OpenCode server running on a computer, so
 * [canRunLocalServer] is false there and the UI has to hide the local-server
 * screens instead of failing at runtime.
 */
data class PlatformInfo(
    val name: String,
    val isAndroid: Boolean,
    val isIos: Boolean,
    val canRunLocalServer: Boolean,
)

expect fun platformInfo(): PlatformInfo
