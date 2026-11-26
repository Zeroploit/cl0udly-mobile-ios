@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.ocmobile.platform.toNSData
import app.ocmobile.platform.writeTemp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * iOS surfaces for the kinds of file the shared code cannot draw itself: PDFKit pages, an
 * AVPlayerViewController and a WKWebView. Each keeps the frame the viewer reserved; only the
 * content is native.
 */

@Composable
actual fun PlatformPdfView(bytes: ByteArray, name: String, modifier: Modifier) {
    // PDFDocument(data:) is failable; a truncated or password-protected file returns null.
    val document = remember(bytes) { runCatching { PDFDocument(bytes.toNSData()) }.getOrNull() }
    if (document == null) {
        Box(modifier)
        return
    }
    UIKitView(
        factory = {
            PDFView().apply {
                setAutoScales(true)
                setDocument(document)
            }
        },
        modifier = modifier,
        update = { view ->
            if (view.document !== document) view.setDocument(document)
        },
    )
}

@Composable
actual fun PlatformVideoView(bytes: ByteArray, mime: String, modifier: Modifier) {
    val url = remember(bytes, mime) { writeTemp(bytes, "video.${extensionFor(mime)}") }
    if (url == null) {
        Box(modifier)
        return
    }
    val holder = remember(url) { VideoHolder(url) }
    DisposableEffect(holder) { onDispose { holder.release() } }
    UIKitView(
        factory = { holder.container },
        modifier = modifier,
        update = { holder.player.play() },
    )
}

/**
 * A web page or SVG: WKWebView loading the bytes directly. Scripts are on, as in Android's WebView
 * settings for these previews; the content came from the user's own agent.
 */
@Composable
actual fun PlatformHtmlView(bytes: ByteArray, name: String, modifier: Modifier) {
    val data = remember(bytes) { bytes.toNSData() }
    UIKitView(
        factory = {
            WKWebView(frame = CGRectZero, configuration = WKWebViewConfiguration()).apply {
                loadData(data, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier,
    )
}

/** The player and the controller that owns the playback controls, kept alive by the composition. */
private class VideoHolder(url: NSURL) {
    val player = AVPlayer(uRL = url)
    private val controller = AVPlayerViewController().apply {
        this.player = player
        showsPlaybackControls = true
    }
    val container = UIView().apply {
        val child = controller.view
        child.frame = bounds
        child.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        addSubview(child)
    }

    fun release() {
        player.pause()
    }
}

private fun extensionFor(mime: String): String = when (mime.substringBefore(';').trim().lowercase()) {
    "video/quicktime" -> "mov"
    "video/x-m4v", "video/m4v" -> "m4v"
    "video/3gpp" -> "3gp"
    "video/mpeg" -> "mpg"
    else -> "mp4"
}
