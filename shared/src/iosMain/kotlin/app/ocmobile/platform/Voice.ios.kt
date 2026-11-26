package app.ocmobile.platform

import app.ocmobile.Graph
import platform.AVFAudio.AVSpeechSynthesisVoice

/**
 * iOS answer for the reading voices: `AVSpeechSynthesisVoice.speechVoices()`.
 *
 * The quality is 1 for the plain voices, 2 for the enhanced ones and 3 for anything above them,
 * which is iOS 16's `.premium`. The numbers are written out because the framework constants are
 * not bound under a stable name; a 14.1 device never reaches 3 anyway.
 * The best voices come first, which is the order the picker shows.
 */
actual object PlatformVoice {

    actual fun voices(): List<PlatformVoiceInfo> {
        val wanted = if (Graph.isPrefsReady()) Graph.prefs.speechLang else ""
        val base = wanted.substringBefore('-').lowercase()
        return AVSpeechSynthesisVoice.speechVoices()
            .filterIsInstance<AVSpeechSynthesisVoice>()
            .mapNotNull { voice ->
                val id = voice.identifier.orEmpty()
                val language = voice.language.orEmpty()
                if (id.isBlank()) null
                else if (base.isNotEmpty() && language.substringBefore('-').lowercase() != base) null
                else PlatformVoiceInfo(id, voice.name.orEmpty(), language, rankOf(voice))
            }
            .sortedByDescending { it.quality }
    }

    actual fun chosen(): String = if (Graph.isPrefsReady()) Graph.prefs.ttsVoice else ""

    actual fun choose(id: String) {
        if (Graph.isPrefsReady()) Graph.prefs.ttsVoice = id
    }

    /** Higher is better: the plain voices, then the enhanced ones, then anything above them. */
    private fun rankOf(voice: AVSpeechSynthesisVoice): Int = when (voice.quality.toInt()) {
        1 -> 1     // AVSpeechSynthesisVoiceQualityDefault
        2 -> 2     // AVSpeechSynthesisVoiceQualityEnhanced
        else -> 3  // AVSpeechSynthesisVoiceQualityPremium, iOS 16+
    }
}
