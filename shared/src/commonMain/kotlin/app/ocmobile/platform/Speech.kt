package app.ocmobile.platform

/**
 * Live speech recognition: speech becomes text while the user talks, with the loudness for the wave
 * and the partial text so it appears as spoken.
 *
 * Android's copy used SpeechRecognizer with the RECORD_AUDIO flow; iOS answers with
 * SFSpeechRecognizer over the AVAudioEngine tap. Platforms without a recognizer return null and
 * the caller keeps its quiet no-op.
 */
interface SpeechEngine {
    /**
     * Begin listening in [langTag] ("en-US"). [onPartial] fires with the growing text, [onFinal]
     * when a chunk settles (the caller concatenates), [onLevel] with the loudness 0..1, [onError]
     * with a short user-readable reason the listener stopped (permission, no speech, failure).
     */
    fun start(langTag: String, onPartial: (String) -> Unit, onFinal: (String) -> Unit, onLevel: (Float) -> Unit, onError: (String) -> Unit)

    /** Stop listening; the last [onFinal] has already been delivered. */
    fun stop()
}

/** The platform's recognizer, or null where listening is not implemented. */
expect fun speechEngine(): SpeechEngine?
