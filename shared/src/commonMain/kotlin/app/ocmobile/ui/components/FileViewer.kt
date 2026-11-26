package app.ocmobile.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.api.str
import app.ocmobile.platform.PlatformMedia
import app.ocmobile.platform.rememberMotionEnabled
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A file to show in the app: inside a message, or on the connected computer.
 * Android also had `Local` for the phone's own environment; the client has none (the agent's files
 * live on the computer), so `Local` keeps the Android shape and [OutFiles.host] never builds one.
 */
sealed interface ViewSrc {
    val name: String

    data class Local(val path: String) : ViewSrc { override val name get() = path.trimEnd('/').substringAfterLast('/') }
    data class Data(val url: String, override val name: String) : ViewSrc
    /** On the computer the phone drives (Remote Control): read through the server, only inside the project. */
    data class Remote(val path: String, val directory: String?) : ViewSrc { override val name get() = path.trimEnd('/').substringAfterLast('/') }
}

/**
 * Everything you and the agent send opens right in the app, like in Claude: pictures zoom, Markdown
 * shows rendered (with its source a tap away), tables as tables, any text or code with line numbers.
 *
 * KMP port: the bytes are decoded here instead of in a cache file, and the two views that needed a
 * platform widget — PDF pages (PdfRenderer) and video/audio (VideoView, MediaPlayer) — show a quiet
 * note until a platform player lands in `iosMain`.
 */
object FileView {
    var src by mutableStateOf<ViewSrc?>(null)
    fun open(s: ViewSrc) { src = s }
}

private enum class Kind { Image, Pdf, Video, Audio, Markdown, Html, Csv, Text, Other }

private class Loaded(val name: String, val bytes: ByteArray, val mime: String, val kind: Kind) {
    val size get() = bytes.size.toLong()
}

private const val TEXT_LIMIT = 2_000_000

private fun extOf(name: String) = name.substringAfterLast('.', "").lowercase()

private fun kindOf(name: String, mime: String): Kind {
    val ext = extOf(name)
    return when {
        mime.startsWith("image/") && ext != "svg" -> Kind.Image
        mime == "application/pdf" || ext == "pdf" -> Kind.Pdf
        mime.startsWith("video/") -> Kind.Video
        mime.startsWith("audio/") -> Kind.Audio
        ext in setOf("md", "markdown") -> Kind.Markdown
        ext in setOf("html", "htm", "svg") -> Kind.Html
        ext in setOf("csv", "tsv") -> Kind.Csv
        mime.startsWith("text/") || ext in setOf("txt", "json", "kt", "py", "sh", "log", "xml", "yaml", "yml", "js", "ts", "css", "toml", "ini", "sql") -> Kind.Text
        else -> Kind.Other
    }
}

/** No zero bytes in the first 4 KB and almost all of it valid text: show it as text. */
private fun looksLikeText(bytes: ByteArray): Boolean {
    if (bytes.isEmpty()) return true
    val head = bytes.copyOfRange(0, minOf(bytes.size, 4096))
    if (head.any { it == 0.toByte() }) return false
    val s = head.decodeToString()
    return s.count { it == '\uFFFD' } < s.length / 50 + 1
}

/** Kind of a file we only know by name and bytes (there is no MimeTypeMap in common code). */
private fun kindOfFile(name: String, mime: String, bytes: ByteArray): Kind {
    if (mime == "application/octet-stream" && looksLikeText(bytes)) return Kind.Text
    return kindOf(name, mime)
}

private fun mimeOfName(name: String): String = when (extOf(name)) {
    "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"; "svg" -> "image/svg+xml"
    "pdf" -> "application/pdf"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "m4a" -> "audio/mp4"; "ogg" -> "audio/ogg"
    "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mov" -> "video/quicktime"
    "md" -> "text/markdown"; "html", "htm" -> "text/html"; "csv" -> "text/csv"
    "kt", "py", "sh", "json", "log", "txt", "ts", "js", "css", "xml", "yaml", "yml" -> "text/plain"
    else -> "application/octet-stream"
}

private suspend fun load(s: ViewSrc): Loaded = withContext(Dispatchers.Default) {
    when (s) {
        // Only the Android app with its own server had files on the phone; a client reads them over the API.
        is ViewSrc.Local -> throw IllegalStateException("This file is on the computer, not on this phone")
        is ViewSrc.Data -> {
            val head = s.url.substringAfter("data:").substringBefore(',')
            val body = s.url.substringAfter(',')
            val bytes = if (head.endsWith(";base64")) decodeBase64(body) else percentDecode(body).encodeToByteArray()
            val mime = head.substringBefore(';').ifBlank { mimeOfName(s.name) }
            Loaded(s.name, bytes, mime, kindOfFile(s.name, mime, bytes))
        }
        is ViewSrc.Remote -> {
            val o = RemoteFiles.get(s.path, s.directory) ?: throw IllegalStateException("The computer sent nothing for ${s.name}")
            val content = o.str("content").orEmpty()
            val bytes = if (o.str("type") == "binary" || o.str("encoding") == "base64") decodeBase64(content) else content.encodeToByteArray()
            val mime = o.str("mimeType") ?: mimeOfName(s.name)
            Loaded(s.name, bytes, mime, kindOfFile(s.name, mime, bytes))
        }
    }
}

/** Put once in the root, over everything: shows [FileView.src] full screen. */
@Composable
fun FileViewerHost() {
    val s = FileView.src ?: return
    val motion = rememberMotionEnabled()
    val shown = remember(s) { Animatable(if (motion) 0f else 1f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(s) { shown.animateTo(1f, tween(150, easing = FastOutSlowInEasing)) }
    fun close() = scope.launch { if (motion) shown.animateTo(0f, tween(110)); FileView.src = null }
    val state by produceState<Result<Loaded>?>(null, s) { value = runCatching { load(s) } }
    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val c = Oc.c
        val dark = state?.getOrNull()?.kind.let { it == Kind.Image || it == Kind.Video }
        Column(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = shown.value; val k = 0.96f + 0.04f * shown.value; scaleX = k; scaleY = k }
                .background(if (dark) Color.Black else c.bg),
        ) {
            val loaded = state?.getOrNull()
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val tint = if (dark) Color.White else c.text
                IconBtn(Ic.Close, { close() }, tint = tint)
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Txt(s.name, size = 16.sp, weight = FontWeight.Medium, color = tint, maxLines = 1)
                    loaded?.let { Txt(typeLabel(it) + "  " + OutFiles.human(it.size), size = 12.sp, color = if (dark) Color.White.copy(alpha = 0.6f) else c.textDim, maxLines = 1) }
                }
                if (loaded != null) {
                    IconBtn(Ic.Share, { PlatformMedia.shareFile(loaded.bytes, loaded.name, loaded.mime) }, tint = tint, iconSize = 21.dp)
                    IconBtn(Ic.Download, { PlatformMedia.saveFile(loaded.bytes, loaded.name, loaded.mime) }, tint = tint, iconSize = 21.dp)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state == null -> Txt("Opening…", Modifier.align(Alignment.Center), color = c.textDim)
                    state!!.isFailure -> Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Txt("Can't open ${s.name}", size = 18.sp)
                        Spacer(Modifier.height(6.dp))
                        Txt(state!!.exceptionOrNull()?.message ?: "", size = 14.sp, color = c.textDim, align = TextAlign.Center)
                    }
                    else -> Body(loaded!!)
                }
            }
        }
    }
}

private fun typeLabel(l: Loaded) = when (l.kind) {
    Kind.Image -> "Image"; Kind.Pdf -> "PDF"; Kind.Video -> "Video"; Kind.Audio -> "Audio"; Kind.Markdown -> "Markdown"
    Kind.Html -> "Web page"; Kind.Csv -> "Table"; Kind.Text -> extOf(l.name).uppercase().ifBlank { "Text" }; Kind.Other -> extOf(l.name).uppercase().ifBlank { "File" }
}

@Composable
private fun Body(l: Loaded) {
    when (l.kind) {
        Kind.Image -> ZoomImage(l.bytes, l.name)
        Kind.Pdf -> PlatformPdfView(l.bytes, l.name, Modifier.fillMaxSize())
        Kind.Video -> PlatformVideoView(l.bytes, l.mime, Modifier.fillMaxSize())
        Kind.Audio -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) { AudioCard(l) }
        Kind.Markdown -> Rendered(l) { text ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) { SelectionContainer { Markdown(text) } }
        }
        Kind.Html -> Rendered(l) { PlatformHtmlView(l.bytes, l.name, Modifier.fillMaxSize()) }
        Kind.Csv -> CsvTable(l.name, readText(l))
        Kind.Text -> TextLines(readText(l))
        Kind.Other -> {
            val c = Oc.c
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(84.dp).clip(Oc.round(22.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Ico(Ic.File, tint = c.textDim, size = 38.dp) }
                Spacer(Modifier.height(16.dp))
                Txt(l.name, size = 18.sp, weight = FontWeight.Medium, align = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Txt("The app can't show this kind of file.", size = 14.sp, color = c.textDim)
            }
        }
    }
}

private fun readText(l: Loaded): String {
    val b = if (l.bytes.size > TEXT_LIMIT) l.bytes.copyOfRange(0, TEXT_LIMIT) else l.bytes
    val s = b.decodeToString()
    return if (l.bytes.size > TEXT_LIMIT) s + "\n\n… (${OutFiles.human(l.size)}: only the first ${OutFiles.human(TEXT_LIMIT.toLong())} are shown)" else s
}

/** Markdown and web pages: shown rendered, with a switch to their source text. */
@Composable
private fun Rendered(l: Loaded, view: @Composable (String) -> Unit) {
    val c = Oc.c
    val text = remember(l.bytes) { readText(l) }
    var source by remember(l.bytes) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            listOf(false to "View", true to "Source").forEach { (v, label) ->
                Txt(label, Modifier.clip(Oc.pill).background(if (source == v) c.surfaceHigh else Color.Transparent).tap { source = v }.padding(horizontal = 14.dp, vertical = 7.dp),
                    size = 14.sp, color = if (source == v) c.text else c.textDim)
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { if (source) TextLines(text) else view(text) }
    }
}

/**
 * The audio card the Android viewer shows: play/pause, a track that seeks on tap, and the
 * elapsed/total time. Playback is [AudioHost], so a new file stops the previous one.
 */
@Composable
private fun AudioCard(l: Loaded) {
    val c = Oc.c
    val track = l.name
    val active = AudioHost.name == track
    val playing = active && AudioHost.playing
    var progress by remember { mutableFloatStateOf(0f) }
    var dur by remember(track) { mutableLongStateOf(0L) }
    LaunchedEffect(active) { if (active) dur = AudioHost.durationMs() }
    LaunchedEffect(playing) {
        while (playing) {
            val total = AudioHost.durationMs()
            if (total > 0) progress = AudioHost.positionMs().toFloat() / total
            delay(120)
        }
    }
    DisposableEffect(track) { onDispose { if (AudioHost.name == track) AudioHost.stop() } }
    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp))) {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.text).tap(onClick = { AudioHost.toggle(track, { l.bytes }, l.mime) }), contentAlignment = Alignment.Center) {
                Ico(if (playing) Ic.Pause else Ic.Play, tint = c.bg, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(l.name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Canvas(Modifier.fillMaxWidth().height(18.dp).pointerInput(track) {
                    detectTapGestures { o: Offset ->
                        val k = (o.x / size.width).coerceIn(0f, 1f)
                        if (AudioHost.name != track) AudioHost.toggle(track, { l.bytes }, l.mime)
                        AudioHost.seekTo(k); progress = k
                    }
                }) {
                    val y = size.height / 2
                    drawRoundRect(c.border, Offset(0f, y - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    drawRoundRect(c.text, Offset(0f, y - 2.dp.toPx()), Size(size.width * (if (active) progress else 0f), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
                Txt(
                    (if (active && dur > 0) time((progress * dur).toLong()) + " / " else "") +
                        (if (dur > 0) time(dur) else extOf(l.name).uppercase()) + " · " + OutFiles.human(l.size),
                    size = 12.sp, color = c.textDim,
                )
            }
            IconBtn(Ic.Download, { PlatformMedia.saveFile(l.bytes, l.name, l.mime) }, size = 38.dp, iconSize = 19.dp, tint = c.textDim)
            IconBtn(Ic.Share, { PlatformMedia.shareFile(l.bytes, l.name, l.mime) }, size = 38.dp, iconSize = 19.dp, tint = c.textDim)
        }
    }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

/** Any text or code: numbered lines, wrapped, selectable; a button copies all of it. */
@Composable
private fun TextLines(text: String) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    val lines = remember(text) { text.split('\n') }
    val gutter = remember(lines.size) { lines.size.toString().length }
    Box(Modifier.fillMaxSize()) {
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 6.dp, bottom = 80.dp)) {
                itemsIndexed(lines) { i, line ->
                    Row(Modifier.fillMaxWidth().padding(end = 14.dp)) {
                        Txt((i + 1).toString().padStart(gutter), Modifier.padding(start = 12.dp, end = 12.dp), size = 12.sp, color = c.textDim.copy(alpha = 0.6f), family = Oc.mono, lineHeight = 19.sp)
                        Txt(line.ifEmpty { " " }, Modifier.weight(1f), size = 13.sp, family = Oc.mono, lineHeight = 19.sp)
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).clip(Oc.pill).background(c.surfaceHigh).border(1.dp, c.border, Oc.pill)
                .tap { clip.setText(AnnotatedString(text)); copiedNote() }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { Ico(Ic.Copy, tint = c.text, size = 18.dp); Spacer(Modifier.width(8.dp)); Txt("Copy all", size = 14.sp) }
    }
}

/** CSV/TSV as a table: the first row is the header; columns get the width of their longest cell (up to a limit). */
@Composable
private fun CsvTable(name: String, text: String) {
    val c = Oc.c
    val rows = remember(text, name) { parseCsv(text, if (extOf(name) == "tsv") '\t' else ',').take(2000) }
    if (rows.isEmpty()) return TextLines("")
    val cols = rows.maxOf { it.size }
    val widths = remember(rows) { (0 until cols).map { i -> (rows.take(200).maxOf { it.getOrNull(i)?.length ?: 0 }.coerceIn(3, 32) * 8 + 24).dp } }
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(widths.fold(0.dp) { a, w -> a + w }).fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            itemsIndexed(rows) { r, row ->
                Row(Modifier.background(if (r == 0) c.surfaceHigh else if (r % 2 == 0) c.surface else Color.Transparent)) {
                    (0 until cols).forEach { i ->
                        Txt(row.getOrNull(i).orEmpty(), Modifier.width(widths[i]).padding(horizontal = 12.dp, vertical = 8.dp), size = 13.sp,
                            weight = if (r == 0) FontWeight.SemiBold else null, maxLines = 2)
                    }
                }
            }
        }
    }
}

/** A plain CSV reader: quoted fields, doubled quotes, line breaks inside quotes. */
internal fun parseCsv(s: String, sep: Char): List<List<String>> {
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        when {
            quoted && ch == '"' && s.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            ch == '"' -> quoted = !quoted
            !quoted && ch == sep -> { row.add(cell.toString()); cell.clear() }
            !quoted && (ch == '\n' || ch == '\r') -> {
                if (ch == '\r' && s.getOrNull(i + 1) == '\n') i++
                row.add(cell.toString()); cell.clear(); rows.add(row); row = ArrayList()
            }
            else -> cell.append(ch)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
    return rows.filterNot { it.size == 1 && it[0].isEmpty() }
}

@Composable
private fun ZoomImage(bytes: ByteArray, name: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val t = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 8f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
            .transformable(t, canPan = { scale > 1f })
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentAlignment = Alignment.Center,
    ) { AsyncImage(bytes, name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
}

// ---------- encodings the platform used to give us (android.util.Base64, android.net.Uri) ----------

/** Standard base64 with padding; URL-safe `-_` is accepted too, whitespace and padding are skipped. */
internal fun decodeBase64(text: String): ByteArray {
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
            else -> continue
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

/** `Uri.decode`: `%XX` runs as UTF-8, plus signs left alone. */
private fun percentDecode(s: String): String {
    if ('%' !in s) return s
    val out = StringBuilder(s.length)
    val bytes = ArrayList<Byte>()
    fun flush() {
        if (bytes.isEmpty()) return
        out.append(bytes.toByteArray().decodeToString()); bytes.clear()
    }
    var i = 0
    while (i < s.length) {
        val ch = s[i]
        val hex = if (ch == '%' && i + 2 < s.length) s.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes.add(hex.toByte()); i += 3
        } else {
            flush(); out.append(ch); i++
        }
    }
    flush()
    return out.toString()
}
