package app.ocmobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.ocmobile.platform.audioEngine

/**
 * Platform views the shared code cannot draw itself: PDF pages and video need PdfKit/AVPlayer on
 * iOS and PdfRenderer/VideoView on Android. The actuals render inside the same frame the Android
 * viewer reserved; the stub actuals (desktop preview) render nothing.
 */
@Composable
expect fun PlatformPdfView(bytes: ByteArray, name: String, modifier: Modifier)

@Composable
expect fun PlatformVideoView(bytes: ByteArray, mime: String, modifier: Modifier)

/** A web page or SVG: a browser widget (WKWebView / WebView); the stubs render nothing. */
@Composable
expect fun PlatformHtmlView(bytes: ByteArray, name: String, modifier: Modifier)

/**
 * The audio behind [AudioCard]: one engine for the whole app, a new track stops the previous one.
 * The card keeps its Android look; only the playback is a platform call.
 */
object AudioHost {
    var name by mutableStateOf<String?>(null)
        private set
    var playing by mutableStateOf(false)
        private set

    private val engine get() = audioEngine()

    fun toggle(track: String, bytes: () -> ByteArray, mime: String) {
        if (name == track) {
            val e = engine ?: return
            if (playing) e.pause() else e.resume()
            playing = e.isPlaying()
            return
        }
        stop()
        val e = engine ?: return
        if (e.start(bytes(), mime)) { name = track; playing = true } else app.ocmobile.Graph.chats.toasts.tryEmit("This audio can't play here")
    }

    fun seekTo(ratio: Float) { engine?.seekTo(ratio) }

    fun durationMs(): Long = engine?.durationMs() ?: 0L
    fun positionMs(): Long = engine?.positionMs() ?: 0L

    fun stop() {
        engine?.release()
        name = null; playing = false
    }
}
