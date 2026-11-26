package app.ocmobile.platform

/** Desktop preview stub: there is no reading voice to list or pick. */
actual object PlatformVoice {
    actual fun voices(): List<PlatformVoiceInfo> = emptyList()

    actual fun chosen(): String = ""

    actual fun choose(id: String) { }
}
