package app.ocmobile.platform

/** Shared Android copy of the module: the voices are a stub, the app in `:app` has its own list. */
actual object PlatformVoice {
    actual fun voices(): List<PlatformVoiceInfo> = emptyList()

    actual fun chosen(): String = ""

    actual fun choose(id: String) { }
}
