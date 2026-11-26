@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive

/**
 * The file viewer's audio player, backed by AVAudioPlayer.
 *
 * The bytes are decoded in memory: the viewer already has them from the server, so writing a
 * temporary file would only add a copy. AVAudioPlayer throws for a format Core Audio cannot read;
 * that becomes the viewer's "can't play" note through the false return.
 */
private var sharedEngine: AudioEngine? = null

actual fun audioEngine(): AudioEngine? = sharedEngine ?: IosAudioEngine().also { sharedEngine = it }

private class IosAudioEngine : AudioEngine {

    private var player: AVAudioPlayer? = null

    override fun start(bytes: ByteArray, mime: String): Boolean {
        release()
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
        val clip = runCatching { AVAudioPlayer(data = bytes.toNSData(), error = null) }.getOrNull() ?: return false
        clip.prepareToPlay()
        player = clip
        return clip.play()
    }

    override fun resume() {
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun seekTo(ratio: Float) {
        val clip = player ?: return
        clip.setCurrentTime(clip.duration * ratio.coerceIn(0f, 1f).toDouble())
    }

    override fun durationMs(): Long = ((player?.duration ?: 0.0) * 1000.0).toLong()

    override fun positionMs(): Long = ((player?.currentTime ?: 0.0) * 1000.0).toLong()

    override fun isPlaying(): Boolean = player?.isPlaying() == true

    override fun release() {
        player?.stop()
        player = null
    }
}
