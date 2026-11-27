package app.ocmobile.data

import app.ocmobile.api.Part
import app.ocmobile.api.Session
import app.ocmobile.api.arr
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.platform.nowMillis
import kotlinx.serialization.json.JsonObject

/**
 * A chat session as one Markdown document: what the screen shows, in plain text.
 *
 * The parsing mirrors what `Messages.kt` renders — user text, assistant text,
 * think blocks, tool runs with their command/input and output — so an export
 * reads like the chat did. Pure common code: the caller saves or shares the
 * string on its own platform.
 */
object ChatExport {

    /**
     * [messages] is what `Chat.messages` holds (and `api.messages` returns):
     * the session's parts as they arrived. [exportedAt] defaults to now; tests
     * pass a fixed instant to stay deterministic.
     */
    fun markdown(
        sessionId: String,
        session: Session?,
        messages: List<ChatMessage>,
        exportedAt: Long = nowMillis(),
    ): String {
        val out = StringBuilder()
        out.append("# ").append(session?.displayTitle?.ifBlank { null } ?: "Chat").append("\n\n")
        out.append("<!-- session: ").append(sessionId)
            .append(" · exported: ").append(stamp(exportedAt)).append(" -->\n")
        if (messages.isNotEmpty()) out.append("\n---\n")
        messages.forEach { m ->
            out.append("\n")
            if (m.info.isUser) user(out, m) else assistant(out, m)
        }
        return out.toString().trimEnd() + "\n"
    }

    // ---------- messages ----------

    /** The user's words as a blockquote, with attachments noted under them. */
    private fun user(out: StringBuilder, m: ChatMessage) {
        out.append("**You** · ").append(stamp(m.info.time.created)).append("\n\n")
        val text = userText(m.parts)
        if (text.isNotBlank()) {
            text.lines().forEach { line -> out.append(if (line.isBlank()) ">" else "> " + line.trimEnd()).append("\n") }
            out.append("\n")
        }
        attachments(m.parts).forEach { out.append("*").append(it).append("*\n") }
        if (text.isNotBlank() || m.parts.any { it.type == "file" }) out.append("\n")
    }

    /**
     * The assistant's reply: text as plain paragraphs, thoughts as a "Thinking"
     * block, tool runs as bullets with their output fenced, an error at the end.
     */
    private fun assistant(out: StringBuilder, m: ChatMessage) {
        val model = m.info.modelName?.takeIf { it.isNotBlank() }
        out.append("**Assistant** · ").append(stamp(m.info.time.created))
        if (model != null) out.append(" · ").append(model)
        out.append("\n\n")
        m.parts.forEach { p ->
            when (p.type) {
                "text" -> if (p.text.isNotBlank()) {
                    out.append(p.text.trimEnd()).append("\n\n")
                }
                "reasoning" -> if (p.text.isNotBlank()) thinking(out, p.text.trim())
                "tool" -> toolRun(out, p)
                "file" -> if (p.raw.str("url") != null) {
                    val name = p.raw.str("filename") ?: "file"
                    out.append("*").append(if (isImage(p)) "Attached image: " else "Sent file: ").append(name).append("*\n\n")
                }
                "subtask" -> out.append("- Started subtask: ").append(
                    p.raw.str("description") ?: p.raw.str("prompt") ?: "",
                ).append("\n")
            }
        }
        m.info.errorMessage?.let {
            out.append("**Error** (").append(m.info.errorName ?: "error").append("): ").append(it.trimEnd()).append("\n\n")
        }
    }

    /** A think block, indented so it reads as asides between the spoken text. */
    private fun thinking(out: StringBuilder, text: String) {
        out.append("> **Thinking**\n")
        text.lines().forEach { line -> out.append("> ").append(line).append("\n") }
        out.append("\n")
    }

    // ---------- tool runs ----------

    /** The tools that are steps on screen (as `isStep`): questions and todos are rendered apart. */
    private val stepTools = setOf("question", "todowrite", "todoread", "remember")

    /** One tool call as a bullet: "Read file X", "Ran command Y", with its output fenced below. */
    private fun toolRun(out: StringBuilder, p: Part) {
        val name = p.raw.str("tool") ?: "tool"
        val state = p.raw.obj("state")
        val input = state?.obj("input")
        val status = state?.str("status") ?: "pending"
        val output = state?.str("output") ?: state?.str("error") ?: ""

        // What the card header shows, in words: the tool's own summary first, then the kind of step.
        val summary = state?.str("title")?.takeIf { it.isNotBlank() }
            ?: input?.let { toolSummary(it) }.orEmpty()
        val line = when {
            name == "bash" -> "Ran command" + (input?.firstStr("command")?.let { ": `" + it.singleLine() + "`" } ?: "")
            name == "read" -> "Read file " + fileRef(input)
            name in setOf("edit", "write", "patch", "multiedit", "apply_patch") ->
                if (status == "error") "Tried to edit file " + fileRef(input) else "Edited file " + fileRef(input)
            name in setOf("grep", "glob", "list", "codesearch") ->
                "Searched" + (summary.takeIf { it.isNotBlank() }?.let { " for `$it`" } ?: "")
            name.startsWith("web") -> "Fetched the web" + (summary.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
            name == "task" -> "Ran a helper agent" + (summary.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
            name in stepTools -> "Used tool `$name`"
            else -> (summary.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Used tool `$name`")
        }
        val marks = when (status) {
            "error" -> " ⚠️"
            "running", "pending" -> " …"
            else -> ""
        }
        out.append("- ").append(line).append(marks).append("\n")

        // A todo list reads as a checklist; other tools show their work below the line.
        if (name == "todowrite") {
            input?.arr("todos")?.forEach { t ->
                val o = t as? JsonObject ?: return@forEach
                val done = o.str("status") == "completed"
                out.append("  - ").append(if (done) "[x] " else "[ ] ").append(o.str("content") ?: "").append("\n")
            }
        }
        // The command itself (bash puts it in the input, the card shows it as code).
        if (name == "bash" && input?.firstStr("command") != null && input.firstStr("command")!!.contains('\n')) {
            out.append("\n").append(fence(input.firstStr("command")!!, "shell")).append("\n")
        }
        // A file edit shows its diff; everything else shows the output or error.
        val diff = state?.obj("metadata")?.str("diff")
        when {
            diff != null && diff.isNotBlank() -> out.append("\n").append(fence(diff.trimEnd(), "diff")).append("\n")
            output.isNotBlank() -> out.append("\n").append(
                fence(output.take(6000) + if (output.length > 6000) "\n… (${output.length - 6000} more chars)" else "", if (status == "error") "error" else "output"),
            ).append("\n")
        }
        out.append("\n")
    }

    // ---------- small parsers, as `Messages.kt` reads them ----------

    /** What the user typed: text parts, minus the synthetic ones the app added itself. */
    private fun userText(parts: List<Part>): String =
        parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }

    /** "Attached image: photo.jpg" lines for the files in a message. */
    private fun attachments(parts: List<Part>): List<String> = parts.filter { it.type == "file" }.map { p ->
        val name = p.raw.str("filename") ?: "file"
        if (isImage(p)) "Attached image: $name" else "Attached file: $name"
    }

    private fun isImage(p: Part) = p.type == "file" && (p.raw.str("mime")?.startsWith("image/") == true)

    /** The first non-blank of the keys, as the UI picks a tool's label (as `firstStr`). */
    private fun JsonObject.firstStr(vararg keys: String) = keys.firstNotNullOfOrNull { k -> str(k)?.takeIf { it.isNotBlank() } }

    /** The one-line summary a tool card shows: the file's name, or command/pattern/url (as `toolSummary`). */
    private fun toolSummary(input: JsonObject): String {
        val path = input.firstStr("filePath", "path")?.substringAfterLast('/')
        return (path ?: input.firstStr("command", "pattern", "url", "query", "description", "prompt")).orEmpty()
            .lineSequence().firstOrNull().orEmpty().take(120)
    }

    /** The file a tool touched, in backticks, or nothing when the input has no path. */
    private fun fileRef(input: JsonObject?): String =
        input?.firstStr("filePath", "path")?.let { "`$it`" } ?: ""

    private fun String.singleLine() = lineSequence().firstOrNull().orEmpty().take(200)

    // ---------- text plumbing ----------

    /** A fenced code block whose fence is long enough for the content inside. */
    private fun fence(code: String, language: String): String {
        var ticks = 3
        Regex("(`+)").findAll(code).forEach { ticks = maxOf(ticks, it.groupValues[1].length + 1) }
        val fence = "`".repeat(ticks)
        return fence + language + "\n" + code.trimEnd() + "\n" + fence
    }

    /**
     * "2026-11-24 18:40" from the epoch, in UTC. Common Kotlin has no date API
     * and the export must not depend on the phone's time zone, so it is plain
     * arithmetic (the civil-date formula `Messages.kt` uses for its headings).
     */
    internal fun stamp(ms: Long): String {
        val days = ms.floorDiv(86_400_000L)
        val tod = (ms.mod(86_400_000L)) / 60_000
        // days → civil date (Howard Hinnant's civil_from_days)
        val z = days + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val mth = if (mp < 10) mp + 3 else mp - 9
        // era years begin on March 1: January and February belong to the next year
        val yr = y + if (mth <= 2) 1 else 0
        fun pad2(n: Long) = n.toString().padStart(2, '0')
        return "$yr-" + pad2(mth) + "-" + pad2(d) + " " + pad2(tod / 60) + ":" + pad2(tod % 60)
    }
}
