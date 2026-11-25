package app.ocmobile.data

import app.ocmobile.net.ServerEndpoint
import app.ocmobile.api.OpenCodeApi
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Chat] moved to commonMain: what was `Handler`-driven frame batching is now a
 * coroutine, and the message/part bookkeeping is plain state. These tests pin
 * the parts an iOS client depends on — streaming text, the server's copy of our
 * own message replacing the local one, and ordering.
 *
 * The API is never called here: `onEvent` takes the same payloads the SSE
 * stream delivers.
 */
class ChatTest {

    private class FixedFactory(private val engine: HttpClientEngine) : HttpClientEngineFactory<MockEngineConfig> {
        override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = engine
    }

    private fun api() = OpenCodeApi({ ServerEndpoint("http://127.0.0.1:4096", "") }, FixedFactory(MockEngine { error("no request expected") }))

    private fun chat(scope: CoroutineScope) = Chat("ses_1", api(), scope)

    private fun part(messageID: String, id: String, text: String) = buildJsonObject {
        put("part", buildJsonObject {
            put("id", id); put("messageID", messageID); put("sessionID", "ses_1")
            put("type", "text"); put("text", text)
        })
    }

    private fun delta(messageID: String, partID: String, text: String) = buildJsonObject {
        put("messageID", messageID); put("partID", partID); put("field", "text"); put("delta", text)
    }

    @Test
    fun `text deltas are buffered and applied once`() = runTest {
        val chat = chat(this)
        chat.onEvent("message.part.updated", part("msg_1", "prt_1", "Hel"))
        chat.onEvent("message.part.delta", delta("msg_1", "prt_1", "lo"))
        chat.onEvent("message.part.delta", delta("msg_1", "prt_1", " there"))

        // Not yet: the flush is scheduled, not immediate.
        assertEquals("Hel", chat.messages.single().parts.single().text)

        advanceTimeBy(30)
        runCurrent()
        assertEquals("Hello there", chat.messages.single().parts.single().text)
    }

    @Test
    fun `the server's user message replaces the one shown at once`() = runTest {
        val chat = chat(this)
        val local = chat.addLocalUser("Add dark mode")
        assertEquals(1, chat.messages.size)
        assertTrue(chat.messages.single().id.startsWith("zzzz-local-"))

        chat.onEvent("message.updated", buildJsonObject {
            put("info", buildJsonObject {
                put("id", "msg_7"); put("sessionID", "ses_1"); put("role", "user")
            })
        })

        assertEquals(listOf("msg_7"), chat.messages.map { it.id })
        assertTrue(chat.messages.none { it.id == local })
    }

    @Test
    fun `messages are kept in id order when parts arrive out of order`() = runTest {
        val chat = chat(this)
        chat.onEvent("message.part.updated", part("msg_b", "prt_b", "second"))
        chat.onEvent("message.part.updated", part("msg_a", "prt_a", "first"))
        chat.onEvent("message.part.updated", part("msg_c", "prt_c", "third"))
        assertEquals(listOf("msg_a", "msg_b", "msg_c"), chat.messages.map { it.id })
    }

    @Test
    fun `a removed part disappears from its message`() = runTest {
        val chat = chat(this)
        chat.onEvent("message.part.updated", part("msg_1", "prt_1", "hello"))
        chat.onEvent("message.part.removed", buildJsonObject {
            put("messageID", "msg_1"); put("partID", "prt_1")
        })
        assertTrue(chat.messages.single().parts.isEmpty())
    }
}