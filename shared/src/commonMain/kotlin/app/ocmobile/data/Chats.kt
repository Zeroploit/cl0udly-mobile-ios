package app.ocmobile.data

import app.ocmobile.api.EventHub
import app.ocmobile.api.OcJson
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.PermissionReq
import app.ocmobile.api.QuestionReq
import app.ocmobile.api.Session
import app.ocmobile.api.obj
import app.ocmobile.api.parsePermission
import app.ocmobile.api.parseQuestion
import app.ocmobile.api.str
import app.ocmobile.platform.nowStamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Why a session is retrying: free-tier limit, provider error and so on. */
data class RetryInfo(
    val message: String,
    val label: String? = null,
    val link: String? = null,
    val title: String? = null,
    val nextAt: Long? = null,
)

/**
 * The client half of OpenCode: session list, statuses, permission requests and
 * open chats, all fed by one SSE connection.
 *
 * This is the reduced form of the Android `ChatHub`. Everything that only makes
 * sense on the phone that *runs* the server is absent on purpose: there is no
 * team config, no memory folder, no sounds, no notifications, no local
 * runtime, because on iOS the server is somebody else's computer. The rest —
 * which is what a chat client needs — is the same code the Android app uses:
 * [Chat] for streaming text, [OpenCodeApi] for requests.
 */
class Chats(
    private val api: OpenCodeApi,
    private val events: EventHub,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    private val all = MutableStateFlow<Map<String, Session>>(emptyMap())
    val sessions = MutableStateFlow<List<Session>>(emptyList())
    val statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    val permissions = MutableStateFlow<List<PermissionReq>>(emptyList())
    val questions = MutableStateFlow<List<QuestionReq>>(emptyList())
    val retries = MutableStateFlow<Map<String, RetryInfo>>(emptyMap())
    val loaded = MutableStateFlow(false)
    val loadError = MutableStateFlow<String?>(null)
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)

    val connected: StateFlow<Boolean> = events.connected

    /** Text to put in the input when the chat opens. */
    val drafts = mutableMapOf<String, String>()
    fun draft(id: String, text: String) { drafts[id] = text }

    private val chats = HashMap<String, Chat>()

    fun start() {
        scope.launch { events.events.collect(::handle) }
        scope.launch {
            events.generation.collect { g ->
                if (g > 0) {
                    refresh()
                    chats.values.forEach { c -> launch { c.load() } }
                }
            }
        }
    }

    fun session(id: String): Session? = all.value[id]

    fun chat(id: String): Chat = chats.getOrPut(id) {
        Chat(id, api, scope).also { it.directory = session(id)?.directory }
    }

    fun forget(id: String) { chats.remove(id) }

    /** Sessions whose directory may hold more sessions than the root listing shows. */
    suspend fun refresh() {
        try {
            // OpenCode answers per project: without a folder it leaves out the sessions of git repositories
            // (each repo is a project of its own), so every project is also asked by its folder.
            val repos = runCatching { api.projects() }.getOrNull().orEmpty()
                .mapNotNull { (it as? JsonObject)?.str("worktree") }
                .filter { it != "/" }
                .distinct()
                .take(24)
            val list = (api.listSessions(limit = 500) + repos.flatMap { d ->
                runCatching { api.listSessions(directory = d, limit = 500) }.getOrDefault(emptyList())
            }).distinctBy { it.id }
            all.value = list.associateBy { it.id }
            publish()
            loaded.value = true
            loadError.value = null
            // what is waiting for an answer in every project that has sessions
            val dirs = list.map { it.directory }.distinct().take(12)
            val perms = ArrayList<PermissionReq>()
            val qs = ArrayList<QuestionReq>()
            val st = HashMap<String, String>()
            for (d in dirs) {
                runCatching { perms += api.permissions(d) }
                runCatching { qs += api.questions(d) }
                runCatching { api.statuses(d).forEach { (k, v) -> v.str("type")?.takeIf { t -> t != "idle" }?.let { t -> st[k] = t } } }
            }
            val (auto, manual) = perms.partition { autoAllows(it.permission) }
            permissions.value = manual
            auto.forEach { r -> scope.launch { runCatching { api.replyPermission(r.id, session(r.sessionID)?.directory, "once") } } }
            questions.value = qs
            statuses.value = st
        } catch (e: Exception) {
            loadError.value = e.message ?: "The server did not answer."
        } finally {
            loaded.value = true
        }
    }

    private fun publish() {
        sessions.value = all.value.values
            .filter { it.parentID == null && it.time.archived == null }
            .sortedByDescending { it.time.updated }
    }

    private fun upsert(s: Session) {
        all.update { it + (s.id to s) }
        chats[s.id]?.directory = s.directory
        publish()
    }

    private fun handle(e: EventHub.Event) {
        val p = e.props
        when (e.type) {
            "session.created", "session.updated" -> p.obj("info")?.let {
                runCatching { OcJson.decodeFromJsonElement(Session.serializer(), it) }.getOrNull()?.let(::upsert)
            }
            "session.deleted" -> p.obj("info")?.str("id")?.let { id -> all.update { it - id }; publish() }
            "session.status" -> {
                val id = p.str("sessionID") ?: return
                val t = p.obj("status")?.str("type") ?: return
                statuses.update { if (t == "idle") it - id else it + (id to t) }
                val st = p.obj("status")
                if (t == "retry" && st != null) {
                    val a = st.obj("action")
                    retries.update {
                        it + (id to RetryInfo(
                            message = st.str("message") ?: "Retrying…",
                            label = a?.str("label"),
                            link = a?.str("link"),
                            title = a?.str("title"),
                            nextAt = st.str("next")?.toLongOrNull(),
                        ))
                    }
                } else retries.update { it - id }
            }
            "session.idle" -> p.str("sessionID")?.let { id -> statuses.update { it - id }; retries.update { it - id } }
            "permission.asked" -> {
                val r = parsePermission(p)
                if (autoAllows(r.permission)) {
                    val dir = e.directory ?: session(r.sessionID)?.directory
                    scope.launch {
                        runCatching { api.replyPermission(r.id, dir, "once") }
                            .onFailure { permissions.update { l -> l.filterNot { it.id == r.id } + r } }
                    }
                } else permissions.update { l -> l.filterNot { it.id == r.id } + r }
            }
            "permission.replied" -> p.str("requestID")?.let { rid -> permissions.update { l -> l.filterNot { it.id == rid } } }
            "question.asked" -> {
                val q = parseQuestion(p)
                questions.update { l -> l.filterNot { it.id == q.id } + q }
            }
            "question.replied", "question.rejected" -> p.str("requestID")?.let { rid -> questions.update { l -> l.filterNot { it.id == rid } } }
            "session.error" -> {
                val msg = p.obj("error")?.obj("data")?.str("message") ?: p.obj("error")?.str("name")
                if (msg != null) toasts.tryEmit(msg)
            }
            else -> {
                val sid = p.str("sessionID") ?: p.obj("info")?.str("sessionID") ?: p.obj("part")?.str("sessionID")
                if (sid != null) chats[sid]?.onEvent(e.type, p)
            }
        }
    }

    /** What the current mode allows without asking. */
    fun autoAllows(permission: String, mode: String = prefs.permissionMode) = when (mode) {
        "auto" -> true
        "edits" -> permission in setOf("edit", "write", "patch", "multiedit", "read", "list", "glob", "grep", "todowrite", "todoread")
        else -> false
    }

    /**
     * Permission rules by the selected mode. "Auto" is an explicit "allow all":
     * without it OpenCode asks about bash.
     */
    fun permissionRules(mode: String = prefs.permissionMode): JsonArray {
        val base = when (mode) {
            "ask" -> listOf(rule("*", "ask"))
            "edits" -> listOf(rule("*", "allow"), rule("bash", "ask"), rule("webfetch", "ask"))
            else -> listOf(rule("*", "allow"))
        }
        return buildJsonArray { base.forEach { add(it) } }
    }

    private fun rule(permission: String, action: String, pattern: String = "*") = buildJsonObject {
        put("permission", permission); put("pattern", pattern); put("action", action)
    }

    suspend fun createSession(directory: String?, title: String? = null): Session {
        val dir = directory ?: runCatching { api.serverDirectory() }.getOrNull() ?: CHAT_DIR
        val s = api.createSession(dir, title, permissionRules())
        upsert(s)
        return s
    }

    /**
     * Sends a message. Our own text appears at once; the server's copy replaces
     * it when it arrives.
     */
    suspend fun send(sessionId: String, text: String) {
        val dir = session(sessionId)?.directory
        val local = chat(sessionId).addLocalUser(text)
        val parts = buildJsonArray {
            add(buildJsonObject { put("type", "text"); put("text", text) })
            // the date and time, as a hidden part of the message
            add(buildJsonObject { put("type", "text"); put("text", nowStamp()); put("synthetic", true) })
        }
        try {
            api.promptAsync(
                id = sessionId,
                directory = dir,
                parts = parts,
                providerID = prefs.providerId.takeIf { it.isNotBlank() },
                modelID = prefs.modelId.takeIf { it.isNotBlank() },
                agent = prefs.agent.takeIf { it.isNotBlank() },
                variant = prefs.variant.takeIf { it.isNotBlank() },
            )
        } catch (e: Exception) {
            chat(sessionId).dropLocal(local)
            throw e
        }
        // The server answers with events, but if SSE is down the message still
        // has to appear: after a moment, reload once and compare.
        scope.launch {
            delay(600)
            val c = chat(sessionId)
            if (c.messages.none { it.info.isUser && it.id != local }) runCatching { c.load() }
        }
    }

    suspend fun abort(sessionId: String) {
        api.abort(sessionId, session(sessionId)?.directory)
    }

    suspend fun replyPermission(request: PermissionReq, reply: String) {
        api.replyPermission(request.id, session(request.sessionID)?.directory, reply)
        permissions.update { l -> l.filterNot { it.id == request.id } }
    }

    suspend fun replyQuestion(request: QuestionReq, answers: List<List<String>>) {
        api.replyQuestion(request.id, session(request.sessionID)?.directory, answers)
        questions.update { l -> l.filterNot { it.id == request.id } }
    }

    suspend fun rejectQuestion(request: QuestionReq) {
        api.rejectQuestion(request.id, session(request.sessionID)?.directory)
        questions.update { l -> l.filterNot { it.id == request.id } }
    }

    /** Deletes a chat: it disappears at once, the server is told after. */
    suspend fun delete(sessionId: String) {
        val dir = session(sessionId)?.directory
        runCatching { api.deleteSession(sessionId, dir) }
        forget(sessionId)
        all.update { it - sessionId }
        publish()
    }

    fun status(id: String): String? = statuses.value[id]
}