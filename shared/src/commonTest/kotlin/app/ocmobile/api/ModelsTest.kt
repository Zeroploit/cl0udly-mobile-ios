package app.ocmobile.api

import app.ocmobile.data.CHAT_DIR
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The models are the first code moved to commonMain, so they are also the first
 * that has to behave identically on Android and iOS. These tests pin the
 * behaviour the UI depends on rather than the JSON shape: unknown fields are
 * tolerated, defaults survive an absent object, and the "New chat" rule is the
 * one the session list shows.
 */
class ModelsTest {
    @Test
    fun `session survives unknown fields and missing optional ones`() {
        val s = OcJson.decodeFromString(
            Session.serializer(),
            """{"id":"ses_1","title":"Fix the bug","directory":"/root/work",
               "cost":0.42,"time":{"created":10,"updated":20,"completed":30},
               "somethingNew":{"nested":true},"model":{"providerID":"anthropic","modelID":"opus"}}""",
        )
        assertEquals("ses_1", s.id)
        assertEquals("Fix the bug", s.displayTitle)
        assertEquals(0.42, s.cost)
        assertEquals(20L, s.time.updated)
        assertEquals("anthropic", s.model?.providerID)
        assertEquals("opus", s.model?.model)
    }

    @Test
    fun `default titles depend on the directory`() {
        fun session(title: String, directory: String) =
            OcJson.decodeFromString(Session.serializer(), """{"id":"x","title":"$title","directory":"$directory"}""")

        // The server names every session "New session - <date>"; the list shows
        // "New chat" for chats and "New session" for the Code tab. Child sessions
        // are the same shape with "Child session".
        assertEquals("New chat", session("New session - 2026-11-23T20:00:00", CHAT_DIR).displayTitle)
        assertEquals("New session", session("", "/root/work").displayTitle)
        assertEquals("New chat", session("Child session - 2026-11-23T20:00:00", CHAT_DIR).displayTitle)
        assertEquals("My own title", session("My own title", CHAT_DIR).displayTitle)
        assertEquals("New session - but not a date", session("New session - but not a date", CHAT_DIR).displayTitle)
    }

    @Test
    fun `tokens add up when the server omits the total`() {
        val t = OcJson.decodeFromString(
            Tokens.serializer(),
            """{"input":10,"output":5,"reasoning":2,"cache":{"read":3,"write":1}}""",
        )
        assertEquals(21L, t.sum)

        val explicit = OcJson.decodeFromString(Tokens.serializer(), """{"input":10,"output":5,"total":99}""")
        assertEquals(99L, explicit.sum)
    }

    @Test
    fun `messages keep their parts as raw json`() {
        val parsed = parseMessages(
            OcJson.parseToJsonElement(
                """[{"info":{"id":"msg_1","sessionID":"ses_1","role":"user"},
                    "parts":[{"id":"p1","type":"text","text":"hola"},
                             {"id":"p2","type":"someFuturePart","payload":{"a":1}},
                             {"id":"p3"}]}]""",
            ),
        )
        assertEquals(1, parsed.size)
        val m = parsed.first()
        assertTrue(m.info.isUser)
        // Every object is kept, even ones this version does not understand:
        // the server adds part types faster than the app can learn them.
        assertEquals(3, m.parts.size)
        assertEquals("hola", m.parts[0].text)
        assertEquals("someFuturePart", m.parts[1].type)
        assertEquals("", m.parts[2].type)
        assertEquals("", m.parts[2].text)
    }

    @Test
    fun `a malformed message is dropped, not fatal`() {
        val parsed = parseMessages(
            OcJson.parseToJsonElement(
                """[{"parts":[]},
                   {"info":{"id":"msg_2","role":"assistant"},"parts":[{"type":"text","text":"ok"}]}]""",
            ),
        )
        assertEquals(1, parsed.size)
        assertEquals("msg_2", parsed.first().info.id)
    }

    @Test
    fun `message errors expose the server message`() {
        val info = OcJson.decodeFromJsonElement(
            MessageInfo.serializer(),
            OcJson.parseToJsonElement(
                """{"id":"m","role":"assistant","error":{"name":"APIError","data":{"message":"quota exceeded"}}}""",
            ),
        )
        assertEquals("quota exceeded", info.errorMessage)
        assertEquals("APIError", info.errorName)
    }

    @Test
    fun `permissions carry the tool call id`() {
        val req = parsePermission(
            OcJson.parseToJsonElement(
                """{"id":"per_1","sessionID":"ses_1","permission":"bash",
                    "patterns":["rm -rf *","ls"],"metadata":{"path":"/tmp"},"tool":{"callID":"call_9"}}""",
            ).jsonObject,
        )
        assertEquals("per_1", req.id)
        assertEquals("bash", req.permission)
        assertEquals(listOf("rm -rf *", "ls"), req.patterns)
        assertEquals("call_9", req.callID)
        assertEquals("/tmp", req.metadata.str("path"))
    }

    @Test
    fun `questions default to custom allowed and single choice`() {
        val req = parseQuestion(
            OcJson.parseToJsonElement(
                """{"id":"q1","sessionID":"s","questions":[{"question":"Which?","header":"Pick",
                    "options":[{"label":"A","description":"first"},{"label":"B"}]}]}""",
            ).jsonObject,
        )
        val q = req.questions.single()
        assertEquals("Which?", q.question)
        assertEquals("Pick", q.header)
        assertEquals(listOf("A", "B"), q.options.map { it.label })
        assertEquals("", q.options[1].description)
        assertTrue(q.custom)
        assertFalse(q.multiple)
    }

    @Test
    fun `json helpers do not throw on the wrong type`() {
        val o = buildJsonObject {
            put("s", "text")
            put("n", 7)
            put("b", true)
            put("obj", buildJsonObject { put("k", "v") })
            put("nullish", JsonPrimitive(null as String?))
        }
        assertEquals("text", o.str("s"))
        assertEquals(7L, o.long("n"))
        assertEquals(7.0, o.dbl("n"))
        assertEquals(true, o.bool("b"))
        assertEquals("v", o.obj("obj")?.str("k"))
        assertNull(o.str("nullish"))
        assertNull(o.str("missing"))
        assertNull(o.obj("s"))
        assertNull(o.arr("s"))
    }
}
