package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.screens.humanSize
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Files the agent hands to the user. The agent writes an absolute path in the reply (usually as a link
 * `[name](/root/outbox/name.mp3)`), the app finds such paths inside the environment and shows cards:
 * audio plays right in the chat, video and images get a preview, the rest can be opened, saved or shared.
 *
 * KMP port: the phone client has no environment of its own — the agent's files live on the computer
 * running OpenCode — so the local half ([find], [host], [FileCards]) only keeps the shape [Messages]
 * refers to and never resolves a file. The remote half ([findRemote], [RemoteFileCards]) is the one
 * that runs, and it is ported as it was.
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

    fun human(b: Long) = humanSize(b)
}

/**
 * What the agent sends: pictures, videos and audio as small square tiles in a row (like Claude's
 * app shows a picture), a tap opens it; other files as cards.
 *
 * The phone client never gets here: [OutFiles.find] needs a local environment and always comes back
 * empty, so the caller's `isNotEmpty()` guard keeps this quiet while the call site reads like Android.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun FileCards(files: List<String>) {
}

/** Side of a media tile: the size Claude's app shows a picture at. */
val TILE = 108.dp

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

/** Cards for files on the computer (Remote Control): a name and size; a tap opens it in the viewer. */
@Composable
fun RemoteFileCards(paths: List<String>, directory: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        paths.forEach { p ->
            val info by produceState<JsonObject?>(null, p, directory) { value = RemoteFiles.get(p, directory) }
            info?.let { o -> RemoteCard(p, directory, o) }
        }
    }
}

@Composable
private fun RemoteCard(path: String, directory: String?, o: JsonObject) {
    val c = Oc.c
    val name = path.substringAfterLast('/')
    val content = (o["content"] as? JsonPrimitive)?.content.orEmpty()
    val binary = (o["type"] as? JsonPrimitive)?.content == "binary"
    val size = if (binary) content.length * 3L / 4 else content.encodeToByteArray().size.toLong()
    CardFrame {
        Row(
            Modifier.tap(onClick = { FileView.open(ViewSrc.Remote(path, directory)) })
                .padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(Oc.round(10.dp)).background(c.surfaceHigh), contentAlignment = Alignment.Center) {
                Ico(Ic.Laptop, tint = c.textDim, size = 20.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Txt(name, size = 15.sp, weight = FontWeight.Medium, maxLines = 1)
                Txt(name.substringAfterLast('.').uppercase() + " · " + OutFiles.human(size) + " · on the computer", size = 13.sp, color = c.textDim, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun CardFrame(content: @Composable () -> Unit) {
    val c = Oc.c
    Box(Modifier.widthIn(max = 420.dp).fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp))) { content() }
}
