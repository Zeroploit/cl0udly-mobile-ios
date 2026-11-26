package app.ocmobile.ui.components

import app.ocmobile.ui.theme.Oc
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.Graph
import app.ocmobile.platform.PlatformMedia
import app.ocmobile.platform.nowMillis
import app.ocmobile.ui.theme.Ic
import coil3.compose.AsyncImage

/**
 * What to give Coil. On Android, guest Linux files lived inside the rootfs and their
 * `file://` URLs were remapped to host paths (`Graph.paths.host`). A client has no such
 * mapping — the file lives on the computer running OpenCode — so the URL goes to Coil
 * unchanged; Coil loads what it can (http/data), and a `file://` shows nothing.
 */
fun imageModel(url: String): Any = url

/**
 * The bytes of a `data:` URL (an image carried inside the chat). `file://` URLs are the
 * environment's files on the server's computer; a client cannot read them, so only
 * `data:` decodes here.
 */
fun imageBytes(url: String): ByteArray? = runCatching {
    when {
        url.startsWith("data:") -> base64Decode(url.substringAfter("base64,"))
        else -> null
    }
}.getOrNull()

@Composable
fun ImageThumb(url: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AsyncImage(
        model = imageModel(url), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(Oc.round(14.dp)).background(Color(0x22888888)).clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewer(urls: List<String>, start: Int, onDismiss: () -> Unit) {
    val pager = rememberPagerState(initialPage = start.coerceIn(0, (urls.size - 1).coerceAtLeast(0))) { urls.size }
    var chrome by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                val tState = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { chrome = !chrome },
                                onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f },
                            )
                        }
                        .transformable(tState, canPan = { scale > 1f })
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(imageModel(urls[page]), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                }
            }
            if (chrome) {
                Row(Modifier.fillMaxSize().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.Top) {
                    IconBtn(Ic.Close, onDismiss, tint = Color.White, bg = Color(0x66000000))
                    Box(Modifier.weight(1f))
                    IconBtn(Ic.External, { shareImage(urls[pager.currentPage]) }, tint = Color.White, bg = Color(0x66000000))
                    Box(Modifier.padding(start = 8.dp))
                    IconBtn(Ic.Download, { saveImage(urls[pager.currentPage]) }, tint = Color.White, bg = Color(0x66000000))
                }
                if (urls.size > 1) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp)) {
                        Txt("${pager.currentPage + 1} / ${urls.size}", color = Color.White, size = 14.sp)
                    }
                }
            }
        }
    }
}

private fun mimeOf(url: String) = if (url.startsWith("data:")) url.substringAfter("data:").substringBefore(';') else "image/png"

/**
 * Save to the phone: iOS puts the share sheet's "Save to Files" in front of the user; the desktop
 * preview has no sheet and does nothing.
 */
private fun saveImage(url: String) {
    val bytes = imageBytes(url) ?: return
    val mime = mimeOf(url)
    PlatformMedia.saveFile(bytes, "cl0udly-${nowMillis()}.${extOf(mime)}", mime)
}

/**
 * Share the image through the system sheet. A picture that only exists as a server URL is shared
 * as its link: the sheet cannot read bytes the app never downloaded.
 */
private fun shareImage(url: String) {
    val bytes = imageBytes(url) ?: run {
        if (url.startsWith("http")) PlatformMedia.shareText(url)
        return
    }
    val mime = mimeOf(url)
    PlatformMedia.shareFile(bytes, "cl0udly-${nowMillis()}.${extOf(mime)}", mime)
}

private fun extOf(mime: String): String = mime.substringAfter('/').substringBefore(';').replace("jpeg", "jpg").ifBlank { "png" }

// ---------- base64 decoding, hand-rolled like the encoder in api/Base64.kt ----------

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Standard base64 with padding; URL-safe `-_` is accepted too, whitespace and padding are skipped. */
private fun base64Decode(text: String): ByteArray {
    val vals = ArrayList<Int>(text.length)
    for (ch in text) {
        val v = when (ch) {
            in 'A'..'Z' -> ch - 'A'
            in 'a'..'z' -> ch - 'a' + 26
            in '0'..'9' -> ch - '0' + 52
            '+' -> 62
            '/' -> 63
            '-' -> 62
            '_' -> 63
            else -> continue          // '=', '\n', '\r' and anything else non-alphabet
        }
        vals.add(v)
    }
    val out = ByteArray(vals.size / 4 * 3 + when (vals.size % 4) { 2 -> 1; 3 -> 2; else -> 0 })
    var o = 0
    var i = 0
    while (i + 3 < vals.size) {
        val n = (vals[i] shl 18) or (vals[i + 1] shl 12) or (vals[i + 2] shl 6) or vals[i + 3]
        out[o++] = ((n shr 16) and 0xff).toByte()
        out[o++] = ((n shr 8) and 0xff).toByte()
        out[o++] = (n and 0xff).toByte()
        i += 4
    }
    val rem = vals.size - i
    if (rem == 2) {
        val n = (vals[i] shl 18) or (vals[i + 1] shl 12)
        out[o] = ((n shr 16) and 0xff).toByte()
    } else if (rem == 3) {
        val n = (vals[i] shl 18) or (vals[i + 1] shl 12) or (vals[i + 2] shl 6)
        out[o] = ((n shr 16) and 0xff).toByte()
        out[o + 1] = ((n shr 8) and 0xff).toByte()
    }
    return out
}
