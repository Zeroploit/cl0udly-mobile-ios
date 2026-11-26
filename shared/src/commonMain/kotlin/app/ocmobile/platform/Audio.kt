package app.ocmobile.platform

/**
 * One player for audio in the file viewer: a new track stops the previous one, like the Android
 * MediaPlayer singleton behind `Files.kt`'s Player.
 */
interface AudioEngine {
    /** Load bytes and start playing; false if the format can't be played. */
    fun start(bytes: ByteArray, mime: String): Boolean

    /** Resume after pause. */
    fun resume()

    fun pause()

    /** Jump to [ratio] of the track (0..1). */
    fun seekTo(ratio: Float)

    fun durationMs(): Long
    fun positionMs(): Long
    fun isPlaying(): Boolean

    /** Stop and free; called when the card leaves the screen. */
    fun release()
}

/** The platform's audio player, or null where playback is not implemented. */
expect fun audioEngine(): AudioEngine?
