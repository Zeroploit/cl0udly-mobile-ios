package app.ocmobile.data

import app.ocmobile.api.MessageInfo
import app.ocmobile.api.Part
import app.ocmobile.api.Session
import app.ocmobile.api.TimeInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ChatExport.markdown] turns what `Chat.messages` holds into one Markdown
 * document. These tests feed it parts in the same JSON shape the SSE stream
 * delivers (as `ChatTest` does) and pin the reading: user words as a
 * blockquote, tool runs as bullets with their output fenced, no synthetic
 * parts leaking in.
 */
class ChatExportTest {

    private val session = Session(id = "ses_1", title = "Dark mode")

    private fun message(id: String, role: String, created: Long, parts: List<Part>) =
        ChatMessage(id, MessageInfo(id = id, sessionID = "ses_1", role = role, time = TimeInfo(created = created)))
            .also { it.parts.addAll(parts) }

    private fun textPart(text: String, synthetic: Boolean = false) = Part(buildJsonObject {
        put("id", "prt_${text.hashCode()}"); put("messageID", "msg_1"); put("sessionID", "ses_1")
        put("type", "text"); put("text", text)
        if (synthetic) put("synthetic", true)
    })

    private fun toolPart(tool: String, input: JsonObject? = null, status: String = "completed", output: String? = null) =
        Part(buildJsonObject {
            put("id", "prt_${tool}1"); put("messageID", "msg_2"); put("sessionID", "ses_1")
            put("type", "tool"); put("tool", tool)
            put("state", buildJsonObject {
                put("status", status)
                input?.let { put("input", it) }
                output?.let { put("output", it) }
            })
        })

    @Test
    fun `the header carries the title, session id and export date`() {
        val md = ChatExport.markdown("ses_1", session, emptyList(), exportedAt = 1_795_651_200_000)
        assertTrue(md.startsWith("# Dark mode\n"))
        assertTrue(md.contains("<!-- session: ses_1 · exported: 2026-11-26 "))
    }

    @Test
    fun `an untitled session falls back to the session id in the header`() {
        val md = ChatExport.markdown("ses_9", Session(id = "ses_9"), emptyList(), exportedAt = 0)
        assertTrue(md.startsWith("# New chat\n") || md.startsWith("# New session\n"))
        assertTrue(md.contains("session: ses_9"))
    }

    @Test
    fun `user words are a blockquote and synthetic parts stay out`() {
        val m = message("msg_1", "user", 0, listOf(
            textPart("Add dark mode"),
            textPart("The user attached 1 file, numbered in the order they put them: 1 = shot.png.", synthetic = true),
            Part(buildJsonObject {
                put("id", "prt_f1"); put("messageID", "msg_1"); put("sessionID", "ses_1")
                put("type", "file"); put("mime", "image/png"); put("filename", "shot.png"); put("url", "data:image/png;base64,xx")
            }),
        ))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("> Add dark mode\n"))
        assertTrue(md.contains("*Attached image: shot.png*"))
        assertTrue(!md.contains("numbered in the order"))
        // attachments are noted, never their data URL
        assertTrue(!md.contains("data:image"))
    }

    @Test
    fun `assistant text is plain and a reasoning part becomes a think block`() {
        val m = message("msg_2", "assistant", 0, listOf(
            Part(buildJsonObject {
                put("id", "prt_r1"); put("messageID", "msg_2"); put("sessionID", "ses_1")
                put("type", "reasoning"); put("text", "Look at the theme files first.")
            }),
            textPart("Done — dark mode is in."),
        ))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("> **Thinking**\n> Look at the theme files first.\n"))
        assertTrue(md.contains("\nDone — dark mode is in.\n"))
    }

    @Test
    fun `a command and a file read show as bullets with the output fenced`() {
        val m = message("msg_2", "assistant", 0, listOf(
            toolPart("bash", input = buildJsonObject { put("command", "gradle test") }, output = "all green\n"),
            toolPart("read", input = buildJsonObject { put("filePath", "/app/Theme.kt") }, output = "fun Theme() {}\n"),
        ))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("- Ran command: `gradle test`\n"))
        assertTrue(md.contains("- Read file `/app/Theme.kt`\n"))
        assertTrue(md.contains("```output\nall green\n```"))
    }

    @Test
    fun `an edit shows its diff and a failed run is marked`() {
        val m = message("msg_2", "assistant", 0, listOf(
            Part(buildJsonObject {
                put("id", "prt_e1"); put("messageID", "msg_2"); put("sessionID", "ses_1")
                put("type", "tool"); put("tool", "edit")
                put("state", buildJsonObject {
                    put("status", "completed"); put("input", buildJsonObject { put("filePath", "/app/Theme.kt") })
                    put("metadata", buildJsonObject { put("diff", "--- a/Theme.kt\n+++ b/Theme.kt") })
                })
            }),
            toolPart("bash", input = buildJsonObject { put("command", "gradle test") }, status = "error", output = "2 tests failed"),
        ))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("- Edited file `/app/Theme.kt`\n"))
        assertTrue(md.contains("```diff\n--- a/Theme.kt\n+++ b/Theme.kt\n```"))
        assertTrue(md.contains("- Ran command: `gradle test` ⚠️\n"))
    }

    @Test
    fun `a todo list reads as a checklist`() {
        val m = message("msg_2", "assistant", 0, listOf(
            toolPart("todowrite", input = buildJsonObject {
                put("todos", kotlinx.serialization.json.buildJsonArray {
                    add(buildJsonObject { put("content", "Pick colors"); put("status", "completed") })
                    add(buildJsonObject { put("content", "Wire the toggle"); put("status", "pending") })
                })
            }),
        ))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("- Used tool `todowrite`\n"))
        assertTrue(md.contains("  - [x] Pick colors\n"))
        assertTrue(md.contains("  - [ ] Wire the toggle\n"))
    }

    @Test
    fun `messages keep their order and carry their timestamps`() {
        val md = ChatExport.markdown("ses_1", session, listOf(
            message("msg_1", "user", 86_400_000, listOf(textPart("hello"))),
            message("msg_2", "assistant", 90_000_000, listOf(textPart("hi!"))),
        ), exportedAt = 0)
        assertTrue(md.indexOf("**You**") < md.indexOf("**Assistant**"))
        assertTrue(md.contains("**You** · 1970-01-02 00:00"))
        assertTrue(md.contains("**Assistant** · 1970-01-02 01:00"))
        assertTrue(md.contains("hi!"))
    }

    @Test
    fun `a message info error is written under the reply`() {
        val m = ChatMessage("msg_2", MessageInfo(
            id = "msg_2", sessionID = "ses_1", role = "assistant", time = TimeInfo(created = 0),
            error = buildJsonObject { put("data", buildJsonObject { put("message", "The model's server is busy right now") }); put("name", "ProviderError") },
        )).also { it.parts.add(textPart("")) }
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("**Error** (ProviderError): The model's server is busy right now"))
    }

    @Test
    fun `code with backticks gets a longer fence`() {
        val m = message("msg_2", "assistant", 0, listOf(
            toolPart("bash", input = buildJsonObject { put("command", "echo ```") }, output = "```"),
        ))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("````output\n```\n````"))
    }

    @Test
    fun `large output is cut and the cut is said`() {
        val big = "x".repeat(7000)
        val m = message("msg_2", "assistant", 0, listOf(toolPart("bash", input = buildJsonObject { put("command", "dump") }, output = big)))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("1000 more chars"))
        assertTrue(!md.contains("x".repeat(7000)))
    }

    @Test
    fun `an empty session exports to just the header`() {
        val md = ChatExport.markdown("ses_1", session, emptyList(), exportedAt = 0)
        assertEquals("""# Dark mode

<!-- session: ses_1 · exported: 1970-01-01 00:00 -->
""", md)
    }

    @Test
    fun `markdown syntax in a user message stays inside the quote`() {
        val m = message("msg_1", "user", 0, listOf(textPart("line one\n\nline two")))
        val md = ChatExport.markdown("ses_1", session, listOf(m), exportedAt = 0)
        assertTrue(md.contains("> line one\n>\n> line two\n"))
    }
}
