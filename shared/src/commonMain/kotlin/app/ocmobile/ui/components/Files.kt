package app.ocmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.str
import app.ocmobile.platform.PlatformMedia
import app.ocmobile.ui.screens.humanSize
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Files the agent hands to the user. The agent writes an absolute path in the reply (usually as a link
 * `[name](/root/outbox/name.mp3)`), the app finds such paths inside the environment and shows cards:
 * audio plays right in the chat, video and images get a preview, the rest can be opened, saved or shared.
 *
 * KMP port: the phone client has no environment of its own — the agent's files live on the computer
 * running OpenCode — so the local half ([find], [host]) only keeps the shape [Messages] refers to and
 * never resolves a file. Every card below is fed by the server through [RemoteFiles], and the layout
 * is the Android one, pixel for pixel.
 */
object OutFiles {
    const val OUTBOX = "/root/outbox"
    private val linkRe = Regex("""\]\((/[^)\s]+)\)""")
    private val tickRe = Regex("""`(/[^`\s]+)`""")
    private val lineRe = Regex("""(?m)^\s*(?:📎\s*)?(/[^\s`*]+)\s*$""")

    /** Absolute paths from the text that point to existing files in the environment. */
    fun find(text: String): List<String> {
        if (Graph.devices.isRemote || '/' !in text) return emptyList()
        val paths = LinkedHashSet<String>()
        listOf(linkRe, tickRe, lineRe).forEach { re -> re.findAll(text).forEach { paths.add(it.groupValues[1].trimEnd('.', ',', ';', ':')) } }
        return paths.mapNotNull { host(it) }.distinct().take(8)
    }

    /**
     * Remote Control: paths in the reply to files on the computer. Only ones with an extension (not folders);
     * whether they exist is checked when the card is shown, through the server.
     */
    fun findRemote(text: String): List<String> {
        if (!Graph.devices.isRemote || '/' !in text) return emptyList()
        val paths = LinkedHashSet<String>()
        listOf(linkRe, tickRe, lineRe).forEach { re -> re.findAll(text).forEach { paths.add(it.groupValues[1].trimEnd('.', ',', ';', ':')) } }
        return paths.filter { p -> p.substringAfterLast('/').let { '.' in it && !it.endsWith('.') } && !p.contains("..") }.take(8)
    }

    /** Kinds of files that are things for the user to look at, not source code the agent edits. */
    val handout = setOf(
        "pdf", "png", "jpg", "jpeg", "gif", "webp", "svg", "mp3", "wav", "ogg", "flac", "m4a", "mp4", "webm", "mov",
        "zip", "tar", "gz", "7z", "apk", "docx", "xlsx", "pptx", "odt", "ods", "csv", "epub", "html", "txt", "md",
    )

    /** Files the agent made with its write tool (handouts only), as `path` lines for [find]. */
    fun written(parts: List<JsonObject>): String = parts.mapNotNull { p ->
        if ((p["tool"] as? JsonPrimitive)?.content != "write") return@mapNotNull null
        val path = ((p["state"] as? JsonObject)?.get("input") as? JsonObject)
            ?.get("filePath")?.let { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null
        path.takeIf { it.startsWith("/") && it.substringAfterLast('.').lowercase() in handout && !it.substringAfterLast('/').startsWith("README", true) }
    }.distinct().joinToString("\n") { "`$it`" }

    /**
     * Guest path → file, only if it's a regular file inside the environment. The environment is the
     * Android app's bundled server; on the phone client there is none, so nothing resolves here.
     */
    fun host(guest: String): String? = null

    /**
     * Type of a file from its name. Android asked `MimeTypeMap`; there is no such map in common code,
     * so the extensions that matter are listed (the server also sends `mimeType`, used first).
     */
    fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"
        "webp" -> "image/webp"; "svg" -> "image/svg+xml"; "bmp" -> "image/bmp"; "heic" -> "image/heic"
        "pdf" -> "application/pdf"
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"; "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"; "m4a" -> "audio/mp4"; "aac" -> "audio/aac"
        "mp4" -> "video/mp4"; "webm" -> "video/webm"; "mov" -> "video/quicktime"; "m4v" -> "video/x-m4v"
        "zip" -> "application/zip"; "tar" -> "application/x-tar"; "gz" -> "application/gzip"
        "7z" -> "application/x-7z-compressed"
        "md" -> "text/markdown"; "html", "htm" -> "text/html"; "csv" -> "text/csv"
        "kt", "py", "sh", "json", "log", "txt", "ts", "js", "css", "xml", "yaml", "yml" -> "text/plain"
        else -> "application/octet-stream"
    }

    fun human(b: Long) = humanSize(b)
}

/**
 * What the agent sends: pictures, videos and audio as small square tiles in a row (like Claude's
 * app shows a picture), a tap opens it; other files as cards.
 *
 * The paths are the ones [OutFiles.findRemote] resolved, so they are on the computer. The caller has
 * no session directory at hand here, so the server resolves them against its own working directory.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileCards(files: List<String>) {
    val (media, rest) = files.partition {
        OutFiles.mime(it.substringAfterLast('/')).let { m -> m.startsWith("image/") || m.startsWith("video/") || m.startsWith("audio/") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (media.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            media.forEach { MediaTile(it, null) }
        }
        // the rest goes through the same remote path as a reply from Remote Control
        if (rest.isNotEmpty()) RemoteFileCards(rest, null)
    }
}

/** Side of a media tile: the size Claude's app shows a picture at. */
val TILE = 108.dp

/** Extension as Android's `File.extension`: empty when there is no dot in the name. */
private fun extOf(name: String): String {
    val i = name.lastIndexOf('.')
    return if (i <= 0 || i == name.length - 1) "" else name.substring(i + 1)
}

/** Name without its extension, as Android's `File.nameWithoutExtension` (a dotfile keeps its name). */
private fun nameWithoutExtension(name: String): String {
    val e = extOf(name)
    return if (e.isEmpty()) name else name.dropLast(e.length + 1)
}

/** What the server said about one file: name, type, size and (lazily) its bytes. */
private class RemoteInfo(
    val name: String,
    val mime: String,
    val size: Long,
    private val binary: Boolean,
    private val content: String,
    private val thumb: String?,
) {
    private var decoded: ByteArray? = null
    private var thumbDecoded: ByteArray? = null
    private var thumbTried = false

    /** The file as bytes: base64 from the server, or the text itself. Decoded once per card. */
    suspend fun bytes(): ByteArray? {
        decoded?.let { return it }
        val b = withContext(Dispatchers.Default) {
            if (binary) runCatching { decodeBase64(content) }.getOrNull() else content.encodeToByteArray()
        }
        if (b != null) decoded = b
        return b
    }

    /** The preview frame the server sent along with the JSON, if any (`data:` URL or bare base64). */
    suspend fun thumbBytes(): ByteArray? {
        if (thumbTried) return thumbDecoded
        thumbTried = true
        val t = thumb ?: return null
        val b64 = if (t.startsWith("data:")) t.substringAfter(',') else t
        thumbDecoded = withContext(Dispatchers.Default) { runCatching { decodeBase64(b64) }.getOrNull() }
        return thumbDecoded
    }
}

private fun remoteInfo(path: String, o: JsonObject): RemoteInfo {
    val name = path.substringAfterLast('/')
    val content = o.str("content").orEmpty()
    val binary = o.str("type") == "binary" || o.str("encoding") == "base64"
    // Android had the file on disk and asked for its length; the client only has the base64 text,
    // so the size is estimated from it (4 characters per 3 bytes).
    val size = if (binary) content.length * 3L / 4 else content.encodeToByteArray().size.toLong()
    val mime = o.str("mimeType")?.takeIf { it.isNotBlank() } ?: OutFiles.mime(name)
    return RemoteInfo(name, mime, size, binary, content, o.str("thumbnail") ?: o.str("thumb"))
}

/**
 * Android played the file from disk with `MediaPlayer`; the client only has the bytes the server sent,
 * so playback goes through [AudioHost] (AVAudioPlayer on iOS). When the server sent nothing decodable,
 * the button opens the file in the viewer instead of doing nothing.
 */
private fun playRemote(path: String, directory: String?) = FileView.open(ViewSrc.Remote(path, directory))

/** Start, pause or resume one track, fetching its bytes first; a new track stops the previous one. */
private suspend fun toggleTrack(f: RemoteInfo, path: String, directory: String?) {
    if (AudioHost.name == path) {
        AudioHost.toggle(path, { ByteArray(0) }, f.mime)
        return
    }
    val b = f.bytes()
    if (b == null) playRemote(path, directory) else AudioHost.toggle(path, { b }, f.mime)
}

@Composable
private fun MediaTile(path: String, directory: String?) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val name = path.substringAfterLast('/')
    var menu by remember { mutableStateOf(false) }
    val shape = Oc.round(18.dp)
    val json by produceState<JsonObject?>(null, path, directory) { value = RemoteFiles.get(path, directory) }
    val info = remember(json) { json?.let { remoteInfo(path, it) } }
    val m = info?.mime ?: OutFiles.mime(name)
    val audio = m.startsWith("audio/")
    val playing = audio && AudioHost.name == path && AudioHost.playing
    if (audio) DisposableEffect(path) { onDispose { if (AudioHost.name == path) AudioHost.stop() } }
    Box(
        Modifier.size(TILE).clip(shape).background(c.surface).border(1.dp, c.border, shape)
            .tap(scale = 0.96f, onLongClick = { menu = true }) {
                if (!audio) FileView.open(ViewSrc.Remote(path, directory))
                else {
                    // nothing back from the server yet: the viewer has the same retry
                    val f = info
                    if (f != null) scope.launch { toggleTrack(f, path, directory) } else playRemote(path, directory)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when {
            m.startsWith("image/") -> {
                val bytes by produceState<ByteArray?>(null, info) { value = info?.bytes() }
                AsyncImage(bytes, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            m.startsWith("video/") -> {
                // Android grabbed a frame with MediaMetadataRetriever; there is no decoder in common
                // code, so the server's thumbnail is used when the JSON carries one.
                val thumb by produceState<ByteArray?>(null, info) { value = info?.thumbBytes() }
                Box(Modifier.fillMaxSize().background(Color.Black))
                if (thumb != null) AsyncImage(thumb, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Ico(Ic.Video, tint = Color.White.copy(alpha = 0.45f), size = 34.dp)
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xAA000000)), contentAlignment = Alignment.Center) { Ico(Ic.Play, tint = Color.White, size = 18.dp) }
            }
            else -> {
                // audio: a few bars like a waveform, the play button over them, the name under
                var progress by remember { mutableFloatStateOf(0f) }
                LaunchedEffect(playing) {
                    while (playing) {
                        val total = AudioHost.durationMs()
                        if (total > 0) progress = AudioHost.positionMs().toFloat() / total
                        delay(120)
                    }
                }
                val bars = remember(name) { List(14) { i -> 0.25f + ((name.hashCode() shr (i % 16)) and 7) / 10f } }
                Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 30.dp)) {
                    val w = size.width / (bars.size * 2 - 1)
                    bars.forEachIndexed { i, h ->
                        val bh = size.height * h.coerceAtMost(1f)
                        val done = playing && i.toFloat() / bars.size < progress
                        drawRoundRect(if (done) c.text else c.textDim.copy(alpha = 0.5f), Offset(i * 2 * w, (size.height - bh) / 2), Size(w, bh), CornerRadius(w / 2))
                    }
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(c.text), contentAlignment = Alignment.Center) { Ico(if (playing) Ic.Pause else Ic.Play, tint = c.bg, size = 18.dp) }
                Txt(nameWithoutExtension(name), size = 11.sp, color = c.textDim, maxLines = 1, modifier = Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 7.dp))
            }
        }
        if (menu) FloatMenu({ menu = false }, listOf(
            FloatItem("Save to Downloads", Ic.Download) { scope.launch { info?.bytes()?.let { PlatformMedia.saveFile(it, name, m) } } },
            FloatItem("Share", Ic.Share) { scope.launch { info?.bytes()?.let { PlatformMedia.shareFile(it, name, m) } } },
        ))
    }
}

@Composable
private fun CardFrame(content: @Composable () -> Unit) {
    val c = Oc.c
    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp))) { content() }
}

@Composable
private fun Actions(f: RemoteInfo) {
    val scope = rememberCoroutineScope()
    val c = Oc.c
    Row {
        IconBtn(Ic.Download, { scope.launch { runCatching { f.bytes()?.let { PlatformMedia.saveFile(it, f.name, f.mime) } } } }, size = 38.dp, iconSize = 19.dp, tint = c.textDim)
        IconBtn(Ic.Share, { scope.launch { f.bytes()?.let { PlatformMedia.shareFile(it, f.name, f.mime) } } }, size = 38.dp, iconSize = 19.dp, tint = c.textDim)
    }
}

@Composable
private fun TitleBlock(name: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Txt(name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
        Txt(sub, size = 13.sp, color = Oc.c.textDim, maxLines = 1)
    }
}

@Composable
private fun PlainCard(f: RemoteInfo, path: String, directory: String?) {
    val c = Oc.c
    CardFrame {
        Row(
            Modifier.tap(onClick = { FileView.open(ViewSrc.Remote(path, directory)) })
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(Oc.round(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                Ico(if (f.mime.contains("zip") || f.mime.contains("tar") || f.mime.contains("compressed")) Ic.Archive else Ic.File, tint = c.textDim, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            TitleBlock(f.name, extOf(f.name).uppercase().ifBlank { "FILE" } + " · " + OutFiles.human(f.size), Modifier.weight(1f))
            Actions(f)
        }
    }
}

@Composable
private fun AudioCard(f: RemoteInfo, path: String, directory: String?) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val active = AudioHost.name == path
    val playing = active && AudioHost.playing
    var progress by remember { mutableFloatStateOf(0f) }
    var dur by remember(path) { mutableLongStateOf(0L) }
    // Android read the length with MediaMetadataRetriever; here the player reports it once it loaded.
    LaunchedEffect(active) { if (active) dur = AudioHost.durationMs() }
    LaunchedEffect(playing) {
        while (playing) {
            val total = AudioHost.durationMs()
            if (total > 0) progress = AudioHost.positionMs().toFloat() / total
            delay(120)
        }
    }
    DisposableEffect(path) { onDispose { if (AudioHost.name == path) AudioHost.stop() } }
    CardFrame {
        Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.text).tap(onClick = { scope.launch { toggleTrack(f, path, directory) } }), contentAlignment = Alignment.Center) {
                Ico(if (playing) Ic.Pause else Ic.Play, tint = c.bg, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(f.name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                // track: tap to seek
                Canvas(Modifier.fillMaxWidth().height(18.dp).pointerInput(path) {
                    detectTapGestures { o: Offset ->
                        val k = (o.x / size.width).coerceIn(0f, 1f)
                        scope.launch {
                            if (AudioHost.name != path) toggleTrack(f, path, directory)
                            AudioHost.seekTo(k); progress = k
                        }
                    }
                }) {
                    val y = size.height / 2
                    drawRoundRect(c.border, Offset(0f, y - 2.dp.toPx()), Size(size.width, 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                    drawRoundRect(c.text, Offset(0f, y - 2.dp.toPx()), Size(size.width * (if (active) progress else 0f), 4.dp.toPx()), CornerRadius(2.dp.toPx()))
                }
                Txt(
                    (if (active && dur > 0) time((progress * dur).toLong()) + " / " else "") +
                        (if (dur > 0) time(dur) else extOf(f.name).uppercase()) + " · " + OutFiles.human(f.size),
                    size = 12.sp, color = c.textDim,
                )
            }
            Actions(f)
        }
    }
}

private fun time(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun VideoCard(f: RemoteInfo, path: String, directory: String?) {
    // Android showed a frame grabbed with MediaMetadataRetriever, and with it the length; there is no
    // media decoder in common code, so the frame (and the duration) only exist if the server sent them.
    val thumb by produceState<ByteArray?>(null, f) { value = f.thumbBytes() }
    CardFrame {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black).tap(onClick = { FileView.open(ViewSrc.Remote(path, directory)) }), contentAlignment = Alignment.Center) {
                if (thumb != null) {
                    AsyncImage(thumb, f.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
                } else {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 70.dp), verticalAlignment = Alignment.CenterVertically) {
                        Ico(Ic.Video, tint = Color.White.copy(alpha = 0.45f), size = 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Txt(f.name, size = 13.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xCC000000)), contentAlignment = Alignment.Center) { Ico(Ic.Play, tint = Color.White, size = 24.dp) }
            }
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(f.name, "Video · " + OutFiles.human(f.size), Modifier.weight(1f))
                Actions(f)
            }
        }
    }
}

@Composable
private fun ImageCard(f: RemoteInfo, path: String, directory: String?) {
    val bytes by produceState<ByteArray?>(null, f) { value = f.bytes() }
    CardFrame {
        Column {
            AsyncImage(bytes, f.name, Modifier.fillMaxWidth().heightInImage().tap(onClick = { FileView.open(ViewSrc.Remote(path, directory)) }), contentScale = ContentScale.Crop)
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TitleBlock(f.name, "Image · " + OutFiles.human(f.size), Modifier.weight(1f))
                Actions(f)
            }
        }
    }
}

private fun Modifier.heightInImage() = this.then(Modifier.heightIn(max = 320.dp))

/** Files on the computer the phone drives, fetched once and kept for the viewer (a few MB at most). */
object RemoteFiles {
    private val mutex = Mutex()
    private val cache = LinkedHashMap<String, JsonObject>()
    private val missing = HashSet<String>()

    suspend fun get(path: String, directory: String?): JsonObject? {
        val k = "$directory|$path"
        mutex.withLock {
            cache[k]?.let { return it }
            if (k in missing) return null
        }
        val o = runCatching { Graph.api.fileContent(path, directory) }.getOrNull()
        mutex.withLock {
            if (o != null && (o["content"]?.toString()?.length ?: 0) < 8_000_000) {
                // the last twelve, like the LinkedHashMap in the Android copy: oldest goes first
                cache[k] = o
                while (cache.size > 12) cache.remove(cache.keys.first())
            } else if (o == null) missing.add(k)
        }
        return o
    }
}

/** Cards for files on the computer (Remote Control): media previews, audio, and a card for the rest. */
@Composable
fun RemoteFileCards(paths: List<String>, directory: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paths.forEach { p ->
            val info by produceState<JsonObject?>(null, p, directory) { value = RemoteFiles.get(p, directory) }
            info?.let { o -> RemoteCard(p, directory, o) }
        }
    }
}

/** One card for a file on the computer, picked by its type like Android's `FileCard` picked by MIME. */
@Composable
private fun RemoteCard(path: String, directory: String?, o: JsonObject) {
    val f = remember(path, o) { remoteInfo(path, o) }
    when {
        f.mime.startsWith("audio/") -> AudioCard(f, path, directory)
        f.mime.startsWith("video/") -> VideoCard(f, path, directory)
        f.mime.startsWith("image/") -> ImageCard(f, path, directory)
        else -> PlainCard(f, path, directory)
    }
}
