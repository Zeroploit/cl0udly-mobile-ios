package app.ocmobile.data

import app.ocmobile.Graph
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
import app.ocmobile.platform.nowMillis
import app.ocmobile.platform.nowStamp
import app.ocmobile.ui.components.SLASH_TOKEN
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
 * A file the user attached to a message. Android's `Attachment` wrapped a `Uri`;
 * shared code carries the same facts in the only form every platform has: the
 * mime, the file name and the URL the part is sent with (a `data:` URL, or the
 * file's address on the server's computer).
 *
 * It is the same shape the shared `ComposerState` already carries its
 * attachments in (`Chat.LocalFile`), so an alias keeps both sides one type.
 */
typealias Attachment = LocalFile

/**
 * The client half of OpenCode: session list, statuses, permission requests and
 * open chats, all fed by one SSE connection.
 *
 * Ported from the Android `ChatHub` with its whole public surface. What only
 * makes sense on the phone that *runs* the server is absent: there are no
 * sounds, no vibration, no notifications and no local memory folder, because
 * on iOS the server is somebody else's computer. The rest â€” which is what a
 * chat client needs â€” is the same code the Android app uses.
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

    /** Tasks cut off when the server stopped: the chat offers to continue or cancel. */
    val interrupted = MutableStateFlow(prefs.interruptedIds.split(',').filter { it.isNotBlank() }.toSet())

    private fun saveBusy() { prefs.busyIds = statuses.value.filterValues { it != "idle" }.keys.joinToString(",") }

    fun dismissInterrupted(id: String) {
        interrupted.update { it - id }
        prefs.interruptedIds = interrupted.value.joinToString(",")
    }

    /** Offers to continue a task the server stop cut off (Android also notified; a client has nothing to notify about its own stop). */
    suspend fun resumeInterrupted(id: String) {
        dismissInterrupted(id)
        send(id, "The server was stopped in the middle of your last task. Check what is already done and continue from where you stopped.")
    }

    /** Was busy before the stop and is idle now: if the last reply is unfinished or cut off, it was interrupted. */
    private suspend fun findInterrupted(nowBusy: Set<String>) {
        val was = prefs.busyIds.split(',').filter { it.isNotBlank() }.toSet()
        val found = was.filter { it !in nowBusy && it in all.value && !isIncognito(it) }.filter { id ->
            val last = runCatching { api.messages(id, session(id)?.directory, 4) }.getOrNull()?.lastOrNull { !it.info.isUser }?.info
            last == null || last.time.completed == null || last.error != null
        }
        if (found.isNotEmpty()) {
            interrupted.update { it + found }
            prefs.interruptedIds = interrupted.value.joinToString(",")
        }
    }

    fun start() {
        // Incognito sessions don't survive a restart: leftovers are deleted on start.
        scope.launch {
            delay(4000)
            incognitoSet().forEach { id -> runCatching { dropIncognito(id) } }
        }
        // Device switch: everything tied to the previous server is reset.
        scope.launch {
            Graph.devices.activeId.collect {
                chats.clear(); all.value = emptyMap(); sessions.value = emptyList(); statuses.value = emptyMap()
                permissions.value = emptyList(); questions.value = emptyList(); retries.value = emptyMap(); loaded.value = false
            }
        }
        scope.launch { events.events.collect(::handle) }
        scope.launch {
            events.generation.collect { g ->
                if (g > 0) {
                    refreshSessions()
                    chats.values.forEach { c -> launch { c.load() } }
                }
            }
        }
    }

    fun session(id: String): Session? = all.value[id]

    /** All known sessions, including child ones (team helpers). */
    val allSessions: StateFlow<Map<String, Session>> get() = all

    /** Child sessions of [id]: helpers the main agent started with the task tool. */
    fun children(id: String): List<Session> = all.value.values.filter { it.parentID == id }.sortedBy { it.time.created }

    fun chat(id: String): Chat = chats.getOrPut(id) {
        Chat(id, api, scope).also { it.directory = session(id)?.directory }
    }

    fun forget(id: String) { chats.remove(id) }

    /** Sessions whose directory may hold more sessions than the root listing shows. */
    suspend fun refreshSessions() {
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
            findInterrupted(st.keys)
            statuses.value = st
            saveBusy()
        } catch (e: Exception) {
            loadError.value = e.message ?: "The server did not answer."
        } finally {
            loaded.value = true
        }
    }

    /** The old name, kept for callers written against it. */
    suspend fun refresh() = refreshSessions()

    fun incognitoSet(): Set<String> = prefs.incognitoIds.split(',').filter { it.isNotBlank() }.toSet()
    fun markIncognito(id: String) { prefs.incognitoIds = (incognitoSet() + id).joinToString(",") }
    fun isIncognito(id: String) = id in incognitoSet()

    /** Deletes an incognito session and forgets it. */
    suspend fun dropIncognito(id: String) {
        val dir = session(id)?.directory
        runCatching { api.deleteSession(id, dir) }
        prefs.incognitoIds = (incognitoSet() - id).joinToString(",")
        chats.remove(id)
        all.update { it - id }
        publish()
    }

    /** Something that can be taken back for a few seconds: the bar at the bottom shows [text] and "Undo". */
    class Undo(val text: String, val undo: () -> Unit, val id: Long = nowMillis())
    val undo = MutableStateFlow<Undo?>(null)
    // Touched only on the main dispatcher (events and UI), so a plain set is enough.
    private val leaving = HashSet<String>()

    /**
     * Deletes chats the way a phone app should: they disappear at once, and for four seconds "Undo" brings
     * them back. Only after that are they removed on the server, so nothing is lost by a slip of the finger.
     */
    fun deleteWithUndo(ids: List<String>) {
        if (ids.isEmpty()) return
        val dirs = ids.associateWith { session(it)?.directory }
        leaving += ids; publish()
        var undone = false
        val u = Undo(if (ids.size == 1) "Chat deleted" else "${ids.size} chats deleted", { undone = true; leaving -= ids.toSet(); publish() })
        undo.value = u
        scope.launch {
            delay(4200)
            if (undo.value === u) undo.value = null
            if (undone) return@launch
            ids.forEach { id -> runCatching { api.deleteSession(id, dirs[id]) }; forget(id) }
            leaving -= ids.toSet()
            refreshSessions()
        }
    }

    private fun publish() {
        val hidden = incognitoSet() + leaving
        sessions.value = all.value.values
            .filter { it.parentID == null && it.time.archived == null && it.id !in hidden }
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
                val was = statuses.value[id]
                statuses.update { if (t == "idle") it - id else it + (id to t) }
                saveBusy()
                if (t != "idle" && id in interrupted.value) dismissInterrupted(id)
                if (t == "idle" && was != null) finished(id)
                val st = p.obj("status")
                if (t == "retry" && st != null) {
                    val a = st.obj("action")
                    retries.update {
                        it + (id to RetryInfo(
                            message = st.str("message") ?: "Retryingâ€¦",
                            label = a?.str("label"),
                            link = a?.str("link"),
                            title = a?.str("title"),
                            nextAt = st.str("next")?.toLongOrNull(),
                        ))
                    }
                    if (st.str("message")?.contains("Free usage", ignoreCase = true) == true && prefs.providerId != POLLINATIONS_ID)
                        fallToPollinations(id)
                    // a 429 from Copilot is retried by OpenCode rather than failed
                    if (st.str("message")?.contains(COPILOT_CAPPED) == true && prefs.providerId == COPILOT_ID)
                        fallToPollinations(id, "Copilot's free limit is used up â€” switched to GPT-OSS 20B (Pollinations)")
                } else retries.update { it - id }
            }
            "session.idle" -> p.str("sessionID")?.let { id -> val was = statuses.value[id]; statuses.update { it - id }; saveBusy(); if (was != null) finished(id) }
            "permission.asked" -> {
                val r = parsePermission(p)
                // the mode already allows this: answer ourselves, don't bother the user (old sessions, agent rules)
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
                // Copilot's month is used up: carry on with the model that needs no account
                val sid = p.str("sessionID")
                if (sid != null && msg?.contains(COPILOT_CAPPED) == true && prefs.providerId == COPILOT_ID)
                    fallToPollinations(sid, "Copilot's free limit is used up â€” switched to GPT-OSS 20B (Pollinations)")
            }
            else -> {
                val sid = p.str("sessionID") ?: p.obj("info")?.str("sessionID") ?: p.obj("part")?.str("sessionID")
                if (sid != null) chats[sid]?.onEvent(e.type, p)
            }
        }
    }

    // ---------- sending ----------

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
    fun permissionRules(mode: String = prefs.permissionMode, sessionId: String? = null): JsonArray {
        val base = when (mode) {
            "ask" -> listOf(rule("*", "ask"))
            "edits" -> listOf(rule("*", "allow"), rule("bash", "ask"), rule("webfetch", "ask"))
            else -> listOf(rule("*", "allow"))
        }
        // memory rules (as on Android): the shelf is read or sealed for this chat
        // (on a client the folder lives on the server's computer; the deny rules still hold)
        val access = if (prefs.memoryOn(sessionId)) "allow" else if (prefs.memoryEnabled) "deny" else null
        val mem = if (access != null) listOf("external_directory", "read", "edit").flatMap { perm ->
            listOf("$MEMORY_DIR/*", "$MEMORY_DIR/**").map { rule(perm, access, it) }
        } else emptyList()
        // a helper model is the user's call in every mode, except the ones they always allow
        val team = if (prefs.teamMode != "off") listOf(rule("task", "ask")) + teamAlways.mapNotNull { ref -> helperNameFor(ref)?.let { rule("task", "allow", it) } }
        else emptyList()
        val rules = base + mem + team
        return buildJsonArray { rules.forEach { add(it) } }
    }

    private fun rule(permission: String, action: String, pattern: String = "*") = buildJsonObject {
        put("permission", permission); put("pattern", pattern); put("action", action)
    }

    /** "provider/model" refs the user set to never ask first, as on Android (`Team.always`). */
    private val teamAlways: List<String> get() = prefs.teamAlways.lines().filter { it.isNotBlank() }

    /** The helper agent name that runs on [ref], from the members list (manual) or the model's name (auto). */
    private fun helperNameFor(ref: String): String? = when (prefs.teamMode) {
        "manual" -> teamMembers.firstOrNull { "${it.provider}/${it.model}" == ref }?.let { slug(it.name) }
        "auto" -> Graph.catalog.model(ref.substringBefore('/'), ref.substringAfter('/', "")).let { m ->
            (m?.name?.takeIf { it.isNotBlank() } ?: ref.substringAfter('/', ""))?.let(::slugIf)
        }
        else -> null
    }

    private data class TeamMember(val name: String, val provider: String, val model: String)
    private val teamMembers: List<TeamMember> get() = runCatching {
        OcJson.parseToJsonElement(prefs.teamMembers).let { el ->
            (el as? JsonArray)?.mapNotNull { o ->
                (o as? JsonObject)?.let { TeamMember(it.str("name") ?: "", it.str("provider") ?: "", it.str("model") ?: "") }
            }
        } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun slug(name: String) = "team-" + name.lowercase().replace(Regex("[^a-z0-9Ð°-ÑÑ‘]+"), "-").trim('-').ifBlank { "helper" }
    private fun slugIf(name: String) = name.takeIf { it.isNotBlank() }?.let { slug(it) }

    /** Sessions whose rules are already set for the current mode (when the mode changes, we set them again). */
    private val ruled = HashMap<String, String>()

    /** Memory on or off for one chat; its rules are set again before the next message. */
    fun setChatMemory(sessionId: String, on: Boolean) { prefs.setChatMemory(sessionId, on); ruled.remove(sessionId) }

    /** Session rules are sent again with the next message (after the team or its OKs changed). */
    fun forgetRules() = ruled.clear()

    suspend fun send(sessionId: String, text: String, atts: List<Attachment> = emptyList(), keepParts: List<JsonObject> = emptyList()) {
        val dir = session(sessionId)?.directory
        val ruleKey = prefs.permissionMode + "/" + prefs.memoryOn(sessionId) + "/" + prefs.memoryEnabled + "/" +
            prefs.teamMode + teamAlways.sorted() + prefs.teamPool + prefs.teamMembers.hashCode()
        if (ruled[sessionId] != ruleKey) runCatching {
            api.setSessionPermission(sessionId, dir, permissionRules(sessionId = sessionId))
            ruled[sessionId] = ruleKey
        }
        val localId = if (keepParts.isEmpty()) {
            chat(sessionId).addLocalUser(text, atts.map { LocalFile(it.mime, it.name, it.url) })
        } else null
        val text = expandSlash(text)
        // fresh memory goes into the instructions before the request (the agent may have changed it in the last reply);
        // on a client the shelf lives on the server's computer and is kept there â€” nothing to sync locally
        try {
            // Android recompresses big pictures and reads local files; a client sends the parts as they are
            // (data: URLs arrive ready; local file paths only make sense next to the server).
            val attParts = atts.map { a ->
                buildJsonObject {
                    put("type", "file"); put("mime", a.mime); put("filename", a.name); put("url", a.url)
                }
            }
            val parts = buildJsonArray {
                if (text.isNotBlank() || (keepParts.isEmpty() && attParts.isEmpty())) add(buildJsonObject { put("type", "text"); put("text", text) })
                keepParts.forEach { add(it) }
                // the date and time, as a hidden part of the message (in the system text it would break the prompt cache every minute)
                add(synthetic(nowStamp()))
                // several files: number them in the order the user arranged, so "photo 2" means the second one
                if (attParts.size > 1) {
                    add(synthetic("The user attached ${atts.size} files, numbered in the order they put them: " +
                        atts.mapIndexed { i, a -> "${i + 1} = ${a.name}" }.joinToString(", ") + ". When they say \"the first photo\", \"image 2\" and so on, they mean this numbering."))
                    attParts.forEachIndexed { i, p -> add(synthetic("Attachment ${i + 1}: ${atts[i].name}")); add(p) }
                } else attParts.forEach { add(it) }
            }
            // in the cl0udly look the agent is the cl0udly cloud, unless turned off
            // anonymous chat: tell the agent not to remember anything from it
            val anon = if (isIncognito(sessionId)) "This is an anonymous chat. Do not save anything from it to memory, notes or files outside the task." else null
            val memory = if (prefs.memoryOn(sessionId)) memorySystemBlock() else null
            api.promptAsync(
                id = sessionId,
                directory = dir,
                parts = parts,
                providerID = prefs.providerId.takeIf { it.isNotBlank() },
                modelID = prefs.modelId.takeIf { it.isNotBlank() },
                agent = prefs.agent.takeIf { it.isNotBlank() },
                variant = prefs.variant.takeIf { it.isNotBlank() },
                system = listOfNotNull(personaInstruction(), anon, memory).joinToString("\n\n").ifBlank { null },
            )
        } catch (e: Exception) {
            localId?.let { chat(sessionId).dropLocal(it) }
            throw e
        }
        // The server answers with events, but if SSE is down the message still
        // has to appear: after a moment, reload once and compare.
        scope.launch {
            delay(600)
            val c = chat(sessionId)
            if (c.messages.none { it.info.isUser && it.id != localId }) runCatching { c.load() }
        }
    }

    /** A note about the memory shelf, sent with the message (as Android's `Memory.systemBlock()`). */
    private fun memorySystemBlock(): String? {
        if (!prefs.memoryEnabled) return null
        return "The user keeps lasting facts on a memory shelf in $MEMORY_DIR (markdown files, one topic per file). " +
            "Read it when personal context helps, and write new facts there when the user asks you to remember something."
    }

    /**
     * "/name args": for a skill, ask the agent to load it (the skill tool); for a command, use its template.
     * An unknown "/â€¦" is sent as is.
     */
    fun expandSlash(text: String): String {
        // skills in the middle of the text: ask the agent to load each one, the text stays as is
        if (!text.startsWith("/")) {
            val cmds = Graph.catalog.commands.value
            val skills = SLASH_TOKEN.findAll(text).map { it.groupValues[2] }
                .filter { n -> cmds.any { it.name == n && it.source == "skill" } }.distinct().toList()
            return if (skills.isEmpty()) text else skills.joinToString("\n") { "Use the \"$it\" skill." } + "\n\n" + text
        }
        val name = text.drop(1).substringBefore(' ').substringBefore('\n')
        val args = text.drop(1 + name.length).trim()
        val c = Graph.catalog.commands.value.firstOrNull { it.name == name } ?: return text
        return if (c.source == "skill") "Use the \"$name\" skill." + (if (args.isNotBlank()) "\n\n$args" else "")
        else c.template?.replace("\$ARGUMENTS", args)?.takeIf { it.isNotBlank() } ?: text
    }

    /**
     * Editing a message (and "retry"): revert the session to that message, together with the agent's
     * file changes, and send the new text; the original's attachments are kept.
     */
    suspend fun edit(sessionId: String, m: ChatMessage, text: String, atts: List<Attachment> = emptyList()) {
        val dir = session(sessionId)?.directory
        runCatching { api.abort(sessionId, dir) }
        api.revert(sessionId, dir, m.id)
        chats[sessionId]?.truncateFrom(m.id)
        val keep = m.parts.filter { it.type == "file" }.map { p ->
            buildJsonObject {
                put("type", "file")
                p.raw.str("mime")?.let { put("mime", it) }
                p.raw.str("filename")?.let { put("filename", it) }
                p.raw.str("url")?.let { put("url", it) }
            }
        } + m.parts.filter { it.type == "text" && parseAttached(it.text) != null }.map { p ->
            buildJsonObject { put("type", "text"); put("text", p.text); put("synthetic", true) }
        }
        send(sessionId, text, atts, keep)
    }

    /** Synthetic part with an attachment â†’ (name, path); null if it isn't an attachment (as Android's `Attachments.parseAttached`). */
    private fun parseAttached(text: String): Pair<String, String?>? {
        if (!text.startsWith("<attached-file ")) return null
        val head = text.substringBefore("/>")
        fun attr(k: String) = Regex("$k=\"([^\"]*)\"").find(head)?.groupValues?.get(1)
        return (attr("name") ?: "file") to attr("path")
    }

    /** The app is on screen; the host sets it. */
    @kotlin.concurrent.Volatile var foreground = false
    /** The chat open on the screen right now, if any. */
    @kotlin.concurrent.Volatile var viewing: String? = null
    /** Chats whose task finished while you weren't looking at them: a bright dot until you open them. */
    val doneUnseen = MutableStateFlow<Set<String>>(emptySet())
    fun seen(id: String) { if (id in doneUnseen.value) doneUnseen.update { it - id } }
    /** Open a chat from a notification tap. */
    val openRequest = MutableStateFlow<String?>(null)

    /** The agent finished: mark the chat and, on screen, celebrate; in the background, notify. */
    private fun finished(id: String) {
        // a failed reply also ends with "idle": don't let it turn the sad reaction into a happy one
        val was = app.ocmobile.ui.components.MascotSignals.outcome
        if (!(was != null && !was.ok && was.sessionId == id && nowMillis() - was.at < 3000))
            app.ocmobile.ui.components.MascotSignals.outcome = app.ocmobile.ui.components.Outcome(ok = true, sessionId = id)
        if (isIncognito(id)) return
        if (!(foreground && viewing == id)) doneUnseen.update { it + id }
        Limits.checkSpending()
        // On screen the reply is its own reward: the note plays, the phone ticks, nothing covers the chat.
        if (foreground) {
            Sounds.play(Sounds.S.Done)
            return
        }
        // The app is in the background: a local notification carries the start of the reply.
        if (!Graph.isPrefsReady() || !prefs.liveNotify) return
        scope.launch {
            delay(400)
            val msg = chats[id]?.messages?.lastOrNull { !it.info.isUser }
            val text = msg?.parts?.filter { it.type == "text" }?.joinToString(" ") { it.text }
                ?.replace(Regex("[`*#>_]"), "")?.trim()?.takeIf { it.isNotBlank() }
                ?: msg?.info?.errorMessage ?: "The task is done"
            val title = session(id)?.displayTitle?.ifBlank { null } ?: "OpenCode"
            app.ocmobile.platform.PlatformNotify.show(id, title, text.take(600), id)
        }
    }

    private var switching = false

    /** A free route answered with a limit: switch to Pollinations (no account) and resend the last message. */
    private fun fallToPollinations(id: String, toast: String = "Zen is capped â€” switched to GPT-OSS 20B (Pollinations)") {
        if (switching) return
        switching = true
        scope.launch {
            try {
                val last = chats[id]?.messages?.lastOrNull { it.info.isUser } ?: return@launch
                val text = last.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
                if (text.isBlank()) return@launch
                enablePollinations()
                toasts.tryEmit(toast)
                edit(id, last, text)
            } catch (e: Exception) {
                toasts.tryEmit(e.message ?: "Could not switch the model")
            } finally { switching = false }
        }
    }

    /**
     * Adds the anonymous Pollinations provider through the server's global config
     * (as Android's `FreeModels.enablePollinations()`): no account, GPT-OSS 20B,
     * and selects it, so the next message already goes through it.
     */
    suspend fun enablePollinations() {
        val block = buildJsonObject {
            put("name", "Pollinations (no account)")
            put("npm", "@ai-sdk/openai-compatible")
            put("options", buildJsonObject {
                put("baseURL", "https://text.pollinations.ai/openai")
                put("apiKey", "anonymous")
            })
            put("models", buildJsonObject {
                put(POLLINATIONS_DEFAULT, buildJsonObject {
                    put("name", "GPT-OSS 20B"); put("tool_call", true); put("reasoning", true); put("attachment", false)
                    put("limit", buildJsonObject { put("context", 131072); put("output", 8192) })
                })
            })
        }
        val patch = buildJsonObject { put("provider", buildJsonObject { put(POLLINATIONS_ID, block) }) }
        runCatching { api.patchGlobalConfig(patch) }
        runCatching { api.disposeAll() }
        runCatching { Graph.catalog.refresh() }
        prefs.providerId = POLLINATIONS_ID; prefs.modelId = POLLINATIONS_DEFAULT; prefs.variant = ""
    }

    suspend fun createSession(directory: String?, title: String? = null): Session {
        val dir = directory ?: runCatching { api.serverDirectory() }.getOrNull() ?: CHAT_DIR
        val s = api.createSession(dir, title, permissionRules())
        upsert(s)
        return s
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

    /** Regular chats go to the menu and "Recents"; project ones to the Code tab. A remote device has no such split. */
    fun isChat(s: Session) = s.directory.trimEnd('/') == CHAT_DIR
    fun chatsOnly(l: List<Session>) = if (Graph.devices.isRemote) l else l.filter { isChat(it) }
    /**
     * Sessions for "Remote devices": on a computer, all of them; on this phone, projects and the chats
     * that are really about code. Team helpers and anonymous chats are left out.
     */
    fun codeOnly(l: List<Session>) = if (Graph.devices.isRemote) l
        else l.filter { s -> s.parentID.isNullOrBlank() && !isIncognito(s.id) && (!isChat(s) || ChatKind.of(s) == ChatKind.Code) }

    /** Who the agent is in the cl0udly look (as Android's `Persona`): sent as an instruction with every message. */
    private fun personaInstruction(): String? {
        if (!(prefs.edition == "cloud" && prefs.mascotPersona)) return null
        val model = Graph.catalog.selectedLabel()
        val where = "You run through OpenCode on the user's own computer, which they reach from the cl0udly app on their phone."
        val phone = if (prefs.phoneTools) " You can also act on the phone itself through the phone tools." else ""
        return """
            Your name is cl0udly. You are a small white pixel cloud with two tall eyes â€” the mascot of the cl0udly app â€” and its assistant.
            $where$phone
            Under the hood you are $model, running through OpenCode. If someone asks what model or AI you are, say so honestly: you are cl0udly, powered by $model via OpenCode. Never claim to be a different model.
            Be warm and a little playful, like a friendly cloud, but keep it light: a cloud joke now and then at most, never at the cost of a clear, correct answer. Do the work first. Answer in the user's language.
        """.trimIndent()
    }

    private companion object {
        /** The memory shelf inside the environment (as Android's `Memory.kt`). */
        const val MEMORY_DIR = "/root/memory"
        const val POLLINATIONS_ID = "pollinations"
        const val POLLINATIONS_DEFAULT = "openai-fast"
        const val COPILOT_ID = "copilot-chat"
        /** ChatHub looks for these words to move the chat to Pollinations (as Android's `ToolCallFix`). */
        const val COPILOT_CAPPED = "Copilot's free limit for this month is used up."
    }
}

/** A text part the user never typed: the date line and the attachment index (as Android's `Attachments.synthetic`). */
internal fun synthetic(text: String) = buildJsonObject { put("type", "text"); put("text", text); put("synthetic", true) }
