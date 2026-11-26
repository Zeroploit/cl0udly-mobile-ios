@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitViewController
import app.ocmobile.platform.toNSData
import app.ocmobile.platform.writeTemp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.PDFKit.PDFDocument
import platform.PDFKit.PDFView
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration

/**
 * iOS surfaces for the kinds of file the shared code cannot draw itself: PDFKit pages, an
 * AVPlayerViewController and a WKWebView. Each keeps the frame the viewer reserved; only the
 * content is native.
 */

@Composable
actual fun PlatformPdfView(bytes: ByteArray, name: String, modifier: Modifier) {
    // PDFDocument(data:) is typed non-null but PDFKit returns nil for a truncated or locked file,
    // which arrives here as an exception: the frame simply stays empty.
    val document = remember(bytes) { runCatching { PDFDocument(data = bytes.toNSData()) }.getOrNull() }
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
    )
}

@Composable
actual fun PlatformVideoView(bytes: ByteArray, mime: String, modifier: Modifier) {
    val url = remember(bytes, mime) { writeTemp(bytes, "video.${extensionFor(mime)}") }
    if (url == null) {
        Box(modifier)
        return
    }
    val controller = remember(url) {
        AVPlayerViewController(nibName = null, bundle = null).apply {
            player = AVPlayer(uRL = url)
            showsPlaybackControls = true
        }
    }
    DisposableEffect(controller) {
        controller.player?.play()
        onDispose { controller.player?.pause() }
    }
    UIKitViewController(factory = { controller }, modifier = modifier)
}

/** A web page or SVG: WKWebView loading the markup directly, as the Android viewer does. */
@Composable
actual fun PlatformHtmlView(bytes: ByteArray, name: String, modifier: Modifier) {
    val html = remember(bytes) { bytes.decodeToString() }
    UIKitView(
        factory = {
            WKWebView(frame = CGRectZero.readValue(), configuration = WKWebViewConfiguration()).apply {
                loadHTMLString(html, null)
            }
        },
        modifier = modifier,
    )
}

private fun extensionFor(mime: String): String = when (mime.substringBefore(';').trim().lowercase()) {
    "video/quicktime" -> "mov"
    "video/x-m4v", "video/m4v" -> "m4v"
    "video/3gpp" -> "3gp"
    "video/mpeg" -> "mpg"
    else -> "mp4"
}
