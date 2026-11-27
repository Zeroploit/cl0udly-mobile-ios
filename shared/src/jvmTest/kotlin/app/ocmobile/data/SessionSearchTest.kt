package app.ocmobile.data

import app.ocmobile.api.OpenCodeApi
import app.ocmobile.net.ServerEndpoint
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [SessionSearch.sessions] ranks what the user gets when they type into the
 * global search bar: sessions whose title matches come first, then sessions
 * whose messages match, newest first. These tests answer the API with
 * [MockEngine] (as [app.ocmobile.api.OpenCodeApiTest] does) and pin the
 * ranking, the snippet, the case-insensitivity and the caps.
 */
class SessionSearchTest {

    private class MockFactory(private val engine: MockEngine) : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = engine
    }

    private fun MockRequestHandleScope.json(text: String) = respond(
        content = text,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    /** The fake server plus the list of message endpoints it was asked for. */
    class Fake(
        val engine: MockEngine,
        val requested: MutableList<String> = mutableListOf(),
    )

    /** A fake server: the session list, the narrowed search list, and per-session messages. */
    private fun engineFor(
        sessions: String = "[]",
        searchResult: String? = null,
        searchFails: Boolean = false,
        messages: Map<String, String> = emptyMap(),
    ): Fake {
        val requested = mutableListOf<String>()
        val engine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/session" && request.url.parameters["search"] != null ->
                    if (searchFails) respond("old server", HttpStatusCode.InternalServerError)
                    else json(searchResult ?: "[]")
                path == "/session" -> json(sessions)
                path.startsWith("/session/") && path.endsWith("/message") -> {
                    requested += path
                    val id = path.removePrefix("/session/").removeSuffix("/message")
                    json(messages[id] ?: "[]")
                }
                else -> respond("", HttpStatusCode.OK)
            }
        }
        return Fake(engine, requested)
    }

    /** Runs [SessionSearch.sessions] against a fake server, restoring the seam afterwards. */
    private suspend fun search(
        query: String,
        limit: Int = SessionSearch.DEFAULT_LIMIT,
        fake: Fake = engineFor(),
    ): List<SessionSearch.SearchHit> {
        SessionSearch.apiOverride = OpenCodeApi({ ServerEndpoint("http://127.0.0.1:4096", "pw") }, MockFactory(fake.engine))
        try {
            return SessionSearch.sessions(query, limit)
        } finally {
            SessionSearch.apiOverride = null
        }
    }

    private fun sessionsJson(vararg triples: Triple<String, String, Long>) = buildString {
        append("[")
        triples.joinToString(",") { (id, title, updated) ->
            """{"id":"$id","title":"$title","time":{"created":$updated,"updated":$updated}}"""
        }.let(::append)
        append("]")
    }

    private fun messagesJson(vararg texts: String, role: String = "user", created: Long = 1_000) = buildString {
        append("[")
        texts.forEachIndexed { i, text ->
            append("""{"info":{"id":"msg_$i","sessionID":"ses","role":"$role","time":{"created":$created}},""")
            append(""""parts":[{"id":"prt_$i","messageID":"msg_$i","sessionID":"ses","type":"text","text":${kotlinx.serialization.json.JsonPrimitive(text)}}]}""")
        }
        append("]")
    }

    // ---------- guards ----------

    @Test
    fun `an empty or blank query returns nothing and never calls the server`() = runTest {
        val fake = engineFor(sessions = sessionsJson(Triple("s1", "Dark mode", 10)))
        assertTrue(search("", fake = fake).isEmpty())
        assertTrue(search("   ", fake = fake).isEmpty())
        assertTrue(fake.requested.isEmpty())
    }

    @Test
    fun `a single character waits for more typing`() = runTest {
        val fake = engineFor(sessions = sessionsJson(Triple("s1", "Dark mode", 10)))
        assertTrue(search("d", fake = fake).isEmpty())
        assertTrue(fake.requested.isEmpty())
    }

    @Test
    fun `a dead server is an empty list, not an exception`() = runTest {
        val engine = MockEngine { respond("down", HttpStatusCode.ServiceUnavailable) }
        assertTrue(search("anything", fake = Fake(engine)).isEmpty())
    }

    // ---------- titles ----------

    @Test
    fun `a title match is found case-insensitively and counts every occurrence`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(
                Triple("s1", "Fix dark mode bug", 100),
                Triple("s2", "Notes about DARK MODE and more dark mode", 200),
            ),
        )
        val hits = search("dark MODE", fake = fake)
        // title hits, newest first
        assertEquals(listOf("s2", "s1"), hits.map { it.sessionId })
        val top = hits.first()
        assertEquals("Notes about DARK MODE and more dark mode", top.title)
        // the snippet of a title hit is the title itself
        assertEquals(top.title, top.snippet)
        assertEquals(2, top.matchCount)
        assertEquals(200, top.timestamp)
    }

    // ---------- messages ----------

    @Test
    fun `message matches come after title matches, newest first`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(
                Triple("title", "The login bug", 100),
                Triple("old", "Something else", 50),
                Triple("new", "Something else", 900),
            ),
            messages = mapOf(
                "old" to messagesJson("please fix the login bug"),
                "new" to messagesJson("about the login bug again"),
            ),
        )
        val hits = search("login bug", fake = fake)
        assertEquals(listOf("title", "new", "old"), hits.map { it.sessionId })
        assertTrue(hits[1].timestamp > hits[2].timestamp)
    }

    @Test
    fun `the snippet sits around the first match, on one line, with ellipses at the cuts`() = runTest {
        val filler = ("word ".repeat(60)).trim() + "\n"
        val text = filler + "the needle is here and the needle is also here" + " tail ".repeat(60)
        val fake = engineFor(
            sessions = sessionsJson(Triple("s1", "Plain chat", 10)),
            messages = mapOf("s1" to messagesJson(text)),
        )
        val hit = search("needle", fake = fake).single()
        assertTrue(hit.snippet.startsWith("…"))
        assertTrue(hit.snippet.endsWith("…"))
        assertTrue(!hit.snippet.contains("\n"))
        assertTrue(hit.snippet.length <= 140)
        assertTrue(hit.snippet.contains("needle is here"))
        // the match count counts both occurrences in the message
        assertEquals(2, hit.matchCount)
    }

    @Test
    fun `a match near the start of the text has no leading ellipsis`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(Triple("s1", "Plain chat", 10)),
            messages = mapOf("s1" to messagesJson("needle at the very start" + " tail ".repeat(40))),
        )
        val hit = search("needle", fake = fake).single()
        assertTrue(!hit.snippet.startsWith("…"))
        assertTrue(hit.snippet.endsWith("…"))
    }

    @Test
    fun `tool parts and empty text parts are skipped`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(Triple("s1", "Plain chat", 10)),
            messages = mapOf("s1" to """[
                {"info":{"id":"m1","sessionID":"s1","role":"assistant","time":{}},"parts":[
                    {"id":"p1","messageID":"m1","sessionID":"s1","type":"tool"},
                    {"id":"p2","messageID":"m1","sessionID":"s1","type":"text","text":""},
                    {"id":"p3","messageID":"m1","sessionID":"s1","type":"text","text":"a real match here"}
                ]}
            ]"""),
        )
        val hit = search("real match", fake = fake).single()
        assertEquals("a real match here", hit.snippet)
        assertEquals(1, hit.matchCount)
    }

    // ---------- server search vs fallback ----------

    @Test
    fun `the server's search endpoint is preferred and only its sessions are read back`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(Triple("s1", "Other", 10), Triple("s2", "Other", 20)),
            searchResult = sessionsJson(Triple("s2", "Other", 20)),
            messages = mapOf(
                "s1" to messagesJson("has the needle too"),
                "s2" to messagesJson("has the needle here"),
            ),
        )
        val hit = search("needle", fake = fake).single()
        assertEquals("s2", hit.sessionId)
        // s1's messages were never fetched: the server already narrowed the list
        assertTrue(fake.requested.none { it.contains("s1") })
    }

    @Test
    fun `an old server that cannot search falls back to reading recent sessions`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(
                Triple("recent", "One", 100),
                Triple("older", "Two", 50),
            ),
            searchFails = true,
            messages = mapOf("older" to messagesJson("found the needle")),
        )
        val hit = search("needle", fake = fake).single()
        assertEquals("older", hit.sessionId)
        assertEquals("found the needle", hit.snippet)
    }

    @Test
    fun `an empty search answer also falls back to reading recent sessions`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(Triple("s1", "One", 100)),
            searchResult = "[]",
            messages = mapOf("s1" to messagesJson("a needle in here")),
        )
        assertEquals("s1", search("needle", fake = fake).single().sessionId)
    }

    // ---------- shape and caps ----------

    @Test
    fun `a session matching by title and text is one hit, ranked as a title hit`() = runTest {
        val fake = engineFor(
            sessions = sessionsJson(Triple("s1", "The needle task", 10)),
            messages = mapOf("s1" to messagesJson("and the needle is in the text")),
        )
        val hit = search("needle", fake = fake).single()
        assertEquals("The needle task", hit.snippet)
    }

    @Test
    fun `results are capped at the limit`() = runTest {
        val ids = (1..8).map { "s$it" }
        val fake = engineFor(
            sessions = sessionsJson(*ids.map { Triple(it, "Chat $it", it.drop(1).toLong()) }.toTypedArray()),
            messages = ids.associateWith { messagesJson("a needle in chat") },
        )
        val hits = search("needle", limit = 3, fake = fake)
        assertEquals(3, hits.size)
    }

    @Test
    fun `the fallback scan is bounded to the recent sessions`() = runTest {
        // 45 sessions, only the oldest one matches: beyond the 40 most recent it must not be read
        val ids = (1..45).map { "s%02d".format(it) }
        val updated = ids.mapIndexed { i, id -> Triple(id, "Chat $i", (1000 - i).toLong()) }
        val fake = engineFor(
            sessions = sessionsJson(*updated.toTypedArray()),
            searchFails = true,
            messages = mapOf("s45" to messagesJson("a needle in the very old chat")),
        )
        assertTrue(search("needle", fake = fake).isEmpty())
        assertTrue(fake.requested.none { it.contains("s45/message") })
    }
}
