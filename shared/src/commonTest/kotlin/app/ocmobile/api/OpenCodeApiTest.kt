package app.ocmobile.api

import app.ocmobile.net.ServerEndpoint
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.content.TextContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The REST client moved to commonMain on Ktor. These tests answer requests with
 * [MockEngine] instead of a socket, so the contract the app depends on is
 * checked without a server: paths, query encoding, the Basic header, JSON
 * bodies, error mapping and the SSE stream.
 */
class OpenCodeApiTest {

    private fun api(engine: MockEngine, password: String = "s3cret") =
        OpenCodeApi({ ServerEndpoint("http://127.0.0.1:4096", password) }, MockFactory(engine))

    /** Hands the same [MockEngine] to every client the API creates. */
    private class MockFactory(private val engine: MockEngine) : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = engine
    }

    private fun MockRequestHandleScope.json(text: String) = respond(
        content = text,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    @Test
    fun `listSessions sends the directory as an encoded query parameter`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/session", request.url.encodedPath)
            assertEquals("/root/my work", request.url.parameters["directory"])
            assertEquals("40", request.url.parameters["limit"])
            assertEquals("fix bug", request.url.parameters["search"])
            // The path is the encoded form on the wire, not the decoded one.
            assertTrue(request.url.encodedQuery.contains("directory=%2Froot%2Fmy%20work"))
            // Basic opencode:<password>
            assertEquals("Basic b3BlbmNvZGU6czNjcmV0", request.headers[HttpHeaders.Authorization])
            json("""[{"id":"ses_1","title":"One","directory":"/root/my work"},{"id":"ses_2","title":"Two"}]""")
        }
        val sessions = api(engine).listSessions("/root/my work", limit = 40, search = "fix bug")
        assertEquals(listOf("ses_1", "ses_2"), sessions.map { it.id })
        assertEquals("One", sessions[0].title)
    }

    @Test
    fun `a null query value is left out`() = runTest {
        val engine = MockEngine { request ->
            assertNull(request.url.parameters["search"])
            assertNull(request.url.parameters["directory"])
            json("[]")
        }
        assertEquals(emptyList(), api(engine).listSessions(directory = null, search = null))
    }

    @Test
    fun `a post without a body still sends an empty object`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/mcp/github/connect", request.url.encodedPath)
            assertEquals("application/json", request.body.contentType?.toString()?.substringBefore(';'))
            assertEquals("{}", (request.body as TextContent).text)
            respond("", HttpStatusCode.OK)
        }
        api(engine).mcpConnect("github")
    }

    @Test
    fun `a get never sends a body`() = runTest {
        val engine = MockEngine { request ->
            assertNull(request.body as? TextContent)
            json("""{"directory":"/root/work"}""")
        }
        assertEquals("/root/work", api(engine).serverDirectory())
    }

    @Test
    fun `a patch sends the json body`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/session/ses_1", request.url.encodedPath)
            assertEquals("""{"title":"New name"}""", (request.body as TextContent).text)
            respond("", HttpStatusCode.OK)
        }
        api(engine).renameSession("ses_1", null, "New name")
    }

    @Test
    fun `an error keeps the server's own message`() = runTest {
        val engine = MockEngine {
            respond("""{"message":"session not found"}""", HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val e = assertFailsWith<ApiException> { api(engine).messages("nope", null) }
        assertEquals(404, e.code)
        assertEquals("session not found", e.message)
    }

    @Test
    fun `an error without a message falls back to the code`() = runTest {
        val engine = MockEngine { respond("<html>bad gateway</html>", HttpStatusCode.BadGateway) }
        val e = assertFailsWith<ApiException> { api(engine).projects() }
        assertEquals(502, e.code)
        assertTrue(e.message!!.startsWith("HTTP 502: <html>"))
    }

    @Test
    fun `an empty body parses as nothing`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.OK) }
        api(engine).deleteSession("ses_1", null) // no throw
    }

    @Test
    fun `health reports the version only when the server says it is healthy`() = runTest {
        val healthy = MockEngine { json("""{"healthy":true,"version":"1.4.2"}""") }
        assertEquals("1.4.2", api(healthy).healthVersionOrThrow())

        val sick = MockEngine { json("""{"healthy":false}""") }
        assertNull(api(sick).healthVersionOrThrow())
    }

    @Test
    fun `an unreachable server becomes a message, not an exception`() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val reason = api(engine).reachable("http://10.0.0.5:4096", "x")
        assertTrue(reason!!.startsWith("Not reachable"))
    }

    @Test
    fun `a wrong password is named as such`() = runTest {
        val engine = MockEngine { respond("nope", HttpStatusCode.Unauthorized) }
        assertEquals("Wrong password", api(engine).reachable("http://10.0.0.5:4096", "wrong"))
    }

    @Test
    fun `the event stream hands over every line`() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("data: {\"type\":\"a\"}\n\ndata: {\"type\":\"b\"}\n\n"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream"),
            )
        }
        val lines = mutableListOf<String>()
        var opened = false
        api(engine).streamLines("/global/event", onOpen = { opened = true }, onLine = { lines += it })
        assertTrue(opened)
        // The blank line after each event is the delimiter the hub dispatches on;
        // the trailing newline of the stream is not a line of its own.
        assertEquals(
            listOf("data: {\"type\":\"a\"}", "", "data: {\"type\":\"b\"}", ""),
            lines,
        )
    }

    @Test
    fun `a rejected stream does not report itself as open`() = runTest {
        val engine = MockEngine { respond("denied", HttpStatusCode.Unauthorized) }
        var opened = false
        val lines = mutableListOf<String>()
        api(engine).streamLines("/global/event", onOpen = { opened = true }, onLine = { lines += it })
        assertEquals(false, opened)
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `prompts carry model, agent and parts`() = runTest {
        val engine = MockEngine { request ->
            val text = (request.body as TextContent).text
            val body = OcJson.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject
            assertEquals("anthropic", body["model"]?.let { (it as kotlinx.serialization.json.JsonObject)["providerID"] }?.let { (it as JsonPrimitive).content })
            assertEquals("opus", (body["model"] as kotlinx.serialization.json.JsonObject)["modelID"]?.let { (it as JsonPrimitive).content })
            assertEquals("build", body["agent"]?.let { (it as JsonPrimitive).content })
            assertEquals(1, (body["parts"] as kotlinx.serialization.json.JsonArray).size)
            respond("", HttpStatusCode.OK)
        }
        api(engine).promptAsync(
            id = "ses_1",
            directory = "/root/work",
            parts = kotlinx.serialization.json.buildJsonArray {
                add(buildJsonObject { put("type", JsonPrimitive("text")); put("text", JsonPrimitive("hola")) })
            },
            providerID = "anthropic",
            modelID = "opus",
            agent = "build",
            variant = null,
        )
    }
}
