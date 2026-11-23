package app.ocmobile.shared

actual fun platformInfo() = PlatformInfo(
    name = "android",
    isAndroid = true,
    isIos = false,
    canRunLocalServer = true,
)
