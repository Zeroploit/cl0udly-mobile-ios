package app.ocmobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.api.MessageInfo
import app.ocmobile.api.OcJson
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.Part
import app.ocmobile.api.TimeInfo
import app.ocmobile.api.arr
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.platform.nowMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A file the user attached, in the only form shared code needs: what it is and
 * where the server can read it. Android's own `Attachment` wraps a `Uri`, which
 * exists nowhere else, so the app converts at the one call site that sends.
 */
data class LocalFile(val mime: String, val name: String, val url: String) {
    val isImage get() = mime.startsWith("image/")
}

class ChatMessage(val id: String, info: MessageInfo) {
    var info by mutableStateOf(info)
    val parts = mutableStateListOf<Part>()

    fun upsertPart(p: Part) {
        val i = parts.indexOfFirst { it.id == p.id }
        if (i >= 0) parts[i] = p else parts.add(p)
    }
}

data class Todo(val content: String, val status: String, val priority: String)

/**
 * State of an open chat. All changes come from the main thread (SSE events and
 * loading).
 *
 * [scope] exists for one reason: tokens arrive dozens per second, so text
 * deltas are buffered and applied at most once every ~24 ms. Android used
 * `Handler`/`Looper` for that; a coroutine on the UI dispatcher does the same
 * job on every platform.
 */
class Chat(val id: String, private val api: OpenCodeApi, private val scope: CoroutineScope) {
    var directory: String? = null
    val messages = mutableStateListOf<ChatMessage>()
    var loading by mutableStateOf(true)
    var loadError by mutableStateOf<String?>(null)
    var todos by mutableStateOf<List<Todo>>(emptyList())
    /** Messages that came with the initial load: they need no appear animation. */
    val initialIds = HashSet<String>()
    var live = false

    /** Our message shown at once, before the server has it; with its pictures, so text and images arrive together. */
    fun addLocalUser(text: String, files: List<LocalFile> = emptyList()): String {
        val id = "zzzz-local-" + nowMillis() + "-" + (localSeq++)
        val raw = buildJsonObject {
            put("id", JsonPrimitive("$id-p")); put("messageID", JsonPrimitive(id))
            put("type", JsonPrimitive("text")); put("text", JsonPrimitive(text))
        }
        val m = ChatMessage(id, MessageInfo(id = id, sessionID = this.id, role = "user", time = TimeInfo(created = nowMillis())))
        m.parts.add(Part(raw))
        files.forEachIndexed { i, f ->
            m.parts.add(Part(buildJsonObject {
                put("id", JsonPrimitive("$id-f$i")); put("messageID", JsonPrimitive(id))
                put("type", JsonPrimitive("file")); put("mime", JsonPrimitive(f.mime))
                put("filename", JsonPrimitive(f.name)); put("url", JsonPrimitive(f.url))
            }))
        }
        messages.add(m)
        return id
    }

    fun dropLocal(id: String) { messages.removeAll { it.id == id } }

    fun truncateFrom(messageId: String) { messages.removeAll { it.id >= messageId } }

    suspend fun load() {
        try {
            val list = api.messages(id, directory, 400)
            val fresh = list.map { m -> ChatMessage(m.info.id, m.info).also { it.parts.addAll(m.parts) } }
            val locals = messages.filter { it.id.startsWith("zzzz-local-") }
            messages.clear()
            messages.addAll(fresh)
            // a message we showed right away and the server hasn't returned yet must not blink and vanish
            locals.forEach { l ->
                val dup = fresh.any { f ->
                    f.info.isUser &&
                        f.parts.firstOrNull()?.text == l.parts.firstOrNull()?.text &&
                        f.info.time.created >= l.info.time.created - 5000
                }
                if (!dup) messages.add(l)
            }
            initialIds.clear(); initialIds.addAll(fresh.map { it.id })
            live = true
            loadError = null
            runCatching { api.todos(id, directory) }.getOrNull()?.let { todos = parseTodos(it) }
        } catch (e: Exception) {
            loadError = e.message ?: "Could not load the chat"
        } finally {
            loading = false
        }
    }

    private val pending = LinkedHashMap<Pair<String, String>, StringBuilder>()
    private var flushQueued = false

    private fun flushDeltas() {
        if (pending.isEmpty()) return
        for ((key, sb) in pending) {
            val m = find(key.first) ?: continue
            val i = m.parts.indexOfFirst { it.id == key.second }
            if (i >= 0) m.parts[i] = m.parts[i].withText(m.parts[i].text + sb)
        }
        pending.clear()
    }

    fun onEvent(type: String, props: JsonObject) {
        // any other part event must see the text accumulated so far
        if (type != "message.part.delta") flushDeltas()
        when (type) {
            "message.updated" -> {
                val info = props.obj("info")?.let { runCatching { OcJson.decodeFromJsonElement(MessageInfo.serializer(), it) }.getOrNull() } ?: return
                val m = find(info.id)
                if (m != null) m.info = info else {
                    // the real user message replaces the "local" one (the one we showed right away)
                    if (info.role == "user") messages.removeAll { it.id.startsWith("zzzz-local-") }
                    insert(ChatMessage(info.id, info))
                }
            }
            "message.removed" -> props.str("messageID")?.let { mid -> messages.removeAll { it.id == mid } }
            "message.part.updated" -> {
                val part = props.obj("part")?.let(::Part) ?: return
                val m = find(part.messageID) ?: insert(ChatMessage(part.messageID, MessageInfo(id = part.messageID, sessionID = id, role = "assistant")))
                m.upsertPart(part)
            }
            "message.part.delta" -> {
                if (props.str("field") != "text") return
                val mid = props.str("messageID") ?: return
                val pid = props.str("partID") ?: return
                // Tokens arrive dozens per second: buffer them and apply at most once per frame.
                pending.getOrPut(mid to pid) { StringBuilder() }.append(props.str("delta") ?: "")
                if (!flushQueued) {
                    flushQueued = true
                    scope.launch { delay(24); flushQueued = false; flushDeltas() }
                }
            }
            "message.part.removed" -> {
                val m = find(props.str("messageID") ?: return) ?: return
                val pid = props.str("partID")
                m.parts.removeAll { it.id == pid }
            }
            "todo.updated" -> props.arr("todos")?.let { todos = parseTodos(it) }
        }
    }

    private fun find(mid: String) = messages.firstOrNull { it.id == mid }

    /** Message ids grow over time, so the order is lexicographic. */
    private fun insert(m: ChatMessage): ChatMessage {
        var i = messages.size
        while (i > 0 && messages[i - 1].id > m.id) i--
        messages.add(i, m)
        return m
    }

    private fun parseTodos(a: JsonArray) = a.mapNotNull {
        val o = it as? JsonObject ?: return@mapNotNull null
        Todo(o.str("content") ?: "", o.str("status") ?: "pending", o.str("priority") ?: "medium")
    }

    private companion object {
        /** Local ids issued in the same millisecond would collide. */
        private var localSeq = 0
    }
}
