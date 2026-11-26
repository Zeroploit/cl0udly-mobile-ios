package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.api.EventHub
import app.ocmobile.api.obj
import app.ocmobile.api.parsePermission
import app.ocmobile.api.parseQuestion
import app.ocmobile.api.str
import app.ocmobile.platform.PlatformNotify
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * The agent on the lock screen: while the app is in the background and a session runs, a notification
 * shows the current step ("Editing Composer.kt") and a count of steps and files. When the agent asks
 * for permission, a separate notification with Allow / Always / Deny buttons lets you answer right
 * from the lock screen.
 *
 * Ported from the Android `LiveNotify.kt`. The channels, the `Notification.Builder`, the
 * `PendingIntent`s and the broadcast receiver are gone: [PlatformNotify] posts, and the ids carry
 * what the iOS delegate needs to answer a button — `"live|<session>"`,
 * `"ask|<request>|<session>"`, `"q|<request>|<session>"`.
 */
object LiveNotify {
    private val EDITS = setOf("edit", "write", "apply_patch", "patch", "multiedit")

    private class Run(
        var step: String = "Thinking…",
        var steps: Int = 0,
        val files: MutableSet<String> = linkedSetOf(),
    )

    private val runs = HashMap<String, Run>()
    /** Request id to the notification id it was posted under, so a reply can clear it. */
    private val asked = HashMap<String, String>()
    private val questioned = HashMap<String, String>()

    /** Follows the event stream; the host calls it once, after the graph is up. */
    fun start() {
        Graph.scope.launch { Graph.events.events.collect { runCatching { handle(it) } } }
    }

    /** The app is on screen again: progress notifications are no longer needed. */
    fun onForeground() = runs.keys.forEach { PlatformNotify.cancel(liveId(it)) }

    /** The app went to the background: show what's running now. */
    fun onBackground() = runs.keys.toList().forEach(::show)

    private fun liveId(sid: String) = "live|$sid"
    private fun askId(rid: String, sid: String) = "ask|$rid|$sid"
    private fun questionId(rid: String, sid: String) = "q|$rid|$sid"

    private fun handle(e: EventHub.Event) {
        val p = e.props
        when (e.type) {
            "session.status" -> {
                val sid = p.str("sessionID") ?: return
                if (p.obj("status")?.str("type") == "idle") done(sid) else { runs.getOrPut(sid) { Run() }; show(sid) }
            }
            "session.idle" -> p.str("sessionID")?.let(::done)
            "message.part.updated" -> {
                val part = p.obj("part") ?: return
                val sid = part.str("sessionID") ?: return
                val r = runs[sid] ?: return
                when (part.str("type")) {
                    "tool" -> {
                        val tool = part.str("tool") ?: return
                        val st = part.obj("state")
                        val input = st?.obj("input")
                        when (st?.str("status")) {
                            "running", "pending" -> r.step = label(tool, input)
                            "completed" -> {
                                r.steps++
                                if (tool in EDITS) input?.str("filePath")?.let { r.files += it }
                            }
                        }
                    }
                    "reasoning" -> r.step = "Thinking…"
                    "text" -> r.step = "Writing the answer"
                }
                show(sid)
            }
            "permission.asked" -> {
                val req = parsePermission(p)
                if (!Graph.chats.autoAllows(req.permission) && !Graph.chats.foreground && Graph.prefs.liveNotify) {
                    ask(req)
                }
            }
            "permission.replied" -> p.str("requestID")?.let { rid -> asked.remove(rid)?.let(PlatformNotify::cancel) }
            "question.asked" -> if (!Graph.chats.foreground && Graph.prefs.liveNotify) question(p)
            "question.replied", "question.rejected" -> p.str("requestID")?.let { rid ->
                questioned.remove(rid)?.let(PlatformNotify::cancel)
            }
        }
    }

    private fun done(sid: String) {
        runs.remove(sid)
        PlatformNotify.cancel(liveId(sid))
    }

    private fun base(name: String) = name.substringAfterLast('/')

    private fun label(tool: String, input: JsonObject?): String = when {
        tool == "bash" -> input?.str("description")?.let { it.replaceFirstChar { c -> c.uppercase() } } ?: "Running a command"
        tool == "read" -> "Reading " + (input?.str("filePath")?.let(::base) ?: "a file")
        tool in EDITS -> "Editing " + (input?.str("filePath")?.let(::base) ?: "files")
        tool in setOf("glob", "grep", "list") -> "Searching the code"
        tool == "webfetch" -> "Reading a web page"
        tool == "websearch" -> "Searching the web"
        tool == "task" -> "Working with a subagent"
        tool == "todowrite" -> "Planning the steps"
        tool == "skill" -> "Using a skill"
        tool.startsWith(PhoneTools.NAME + "_") -> "Using the phone"
        else -> "Using $tool"
    }

    private fun show(sid: String) {
        val r = runs[sid] ?: return
        if (!Graph.prefs.liveNotify || Graph.chats.foreground || Graph.chats.isIncognito(sid)) {
            PlatformNotify.cancel(liveId(sid))
            return
        }
        val title = Graph.chats.session(sid)?.displayTitle?.ifBlank { null } ?: "OpenCode"
        val counts = listOfNotNull(
            "${r.steps} ${if (r.steps == 1) "step" else "steps"}",
            r.files.size.takeIf { it > 0 }?.let { "$it ${if (it == 1) "file" else "files"}" },
        ).joinToString(" · ")
        // The step is the headline and the chat name the body, as on Android; the counts join the
        // body because iOS notifications have no subtext line.
        PlatformNotify.show(liveId(sid), r.step, "$title\n$counts", sid)
    }

    private fun ask(req: app.ocmobile.api.PermissionReq) {
        val what = when (req.permission) {
            "bash" -> "Run: " + (req.metadata.str("command") ?: req.patterns.joinToString(" "))
            "edit", "write" -> "Edit " + req.patterns.joinToString(", ") { base(it) }
            "webfetch" -> "Open " + req.patterns.joinToString(", ")
            "external_directory" -> "Work outside the project: " + req.patterns.joinToString(", ")
            else -> req.permission.replaceFirstChar { it.uppercase() } + " " + req.patterns.joinToString(", ")
        }.take(300)
        val title = Graph.chats.session(req.sessionID)?.displayTitle?.ifBlank { null } ?: "OpenCode"
        // The delegate resolves the directory from the session in the id.
        val nid = askId(req.id, req.sessionID)
        asked[req.id] = nid
        PlatformNotify.show(nid, "OpenCode needs your OK", "$what\n$title", req.sessionID)
    }

    private fun question(p: JsonObject) {
        val q = parseQuestion(p)
        val text = q.questions.firstOrNull()?.question ?: "OpenCode has a question"
        val nid = questionId(q.id, q.sessionID)
        questioned[q.id] = nid
        PlatformNotify.show(nid, "OpenCode is asking you", text, q.sessionID)
    }
}
