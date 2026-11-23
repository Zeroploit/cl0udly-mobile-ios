package app.ocmobile.shared

actual fun platformInfo() = PlatformInfo(
    name = "ios",
    isAndroid = false,
    isIos = true,
    // No proot, no exec of unsigned binaries, no foreground service: the
    // server has to run somewhere else.
    canRunLocalServer = false,
)
