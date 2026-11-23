package app.ocmobile.shared

actual fun platformInfo() = PlatformInfo(
    name = "jvm",
    isAndroid = false,
    isIos = false,
    canRunLocalServer = false,
)
