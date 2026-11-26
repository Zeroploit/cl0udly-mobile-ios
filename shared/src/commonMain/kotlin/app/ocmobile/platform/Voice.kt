package app.ocmobile.platform

/** A voice the system can read with. [quality] is higher for the enhanced ones. */
class PlatformVoiceInfo(val id: String, val name: String, val lang: String, val quality: Int)

/**
 * The reading voices the system offers, and the one the user picked.
 *
 * Android listed the TextToSpeech voices installed for a language; iOS answers with
 * `AVSpeechSynthesisVoice.speechVoices()` and reads with `AVSpeechUtterance.voice`.
 * The desktop preview and the Android copy of the shared module keep the defaults.
 */
expect object PlatformVoice {
    /** The voices for the speech language, the best ones first; all of them when it is "auto". */
    fun voices(): List<PlatformVoiceInfo>

    /** The voice chosen in the settings; empty means "the system default". */
    fun chosen(): String

    /** Remember [id] as the reading voice; empty goes back to the system default. */
    fun choose(id: String)
}
