package app.ocmobile.api

import app.ocmobile.net.ServerEndpoint
import app.ocmobile.net.httpEngineFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import app.ocmobile.platform.nowMillis
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A non-2xx answer from the server, carrying the code and the server's own
 * message. It used to extend `java.io.IOException`; common code has no such
 * class, so it extends [Exception] and callers catch `ApiException` itself.
 */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * REST client for `opencode serve` (OpenCode 1.x).
 *
 * It used to be Android-only, built on OkHttp, and it asked [app.ocmobile.Graph]
 * for the address of the server. Both are gone: the address comes from
 * [endpoint] and the transport is an engine per platform, which is what lets
 * the iOS app talk to a server on your computer with the same code.
 */
class OpenCodeApi(
    private val endpoint: () -> ServerEndpoint,
    private val engineFactory: HttpClientEngineFactory<*> = httpEngineFactory(),
) {
    val baseUrl get() = endpoint().url
    private val password get() = endpoint().password

    private fun newClient(connectMs: Long, socketMs: Long, requestMs: Long): HttpClient = HttpClient(engineFactory) {
        // The app decides what a non-2xx means (it reads the server's message).
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = connectMs
            socketTimeoutMillis = socketMs
            requestTimeoutMillis = requestMs
        }
    }

    /** 60 s to read: a long prompt or a big session list still arrives. */
    private val http by lazy { newClient(connectMs = 5_000, socketMs = 60_000, requestMs = 60_000) }

    /**
     * SSE: the server sends a heartbeat every 10 s, so 30 s of silence means a
     * dropped connection and the caller reconnects. No overall request timeout,
     * or the stream would be cut on a schedule.
     */
    private val streamHttp by lazy {
        // Long.MAX_VALUE is Ktor's own "infinite" request timeout.
        newClient(connectMs = 5_000, socketMs = 30_000, requestMs = Long.MAX_VALUE)
    }

    /** For readiness polling: a short timeout so a stuck request doesn't delay the next try. */
    private val healthHttp by lazy { newClient(connectMs = 2_000, socketMs = 2_000, requestMs = 2_000) }

    /** Device-code sign-in waits for the user to confirm in the browser, hence the long timeout. */
    private val longHttp by lazy { newClient(connectMs = 5_000, socketMs = 600_000, requestMs = Long.MAX_VALUE) }

    private fun authHeader(password: String) = "Basic " + base64("opencode:$password")

    private fun urlFor(path: String, query: Map<String, String?> = emptyMap()): String = buildString {
        append(endpoint().url.trimEnd('/')).append(path)
        var first = true
        query.forEach { (k, v) ->
            if (v != null) {
                append(if (first) '?' else '&')
                append(k.encodeURLParameter()).append('=').append(v.encodeURLParameter())
                first = false
            }
        }
    }

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, String?> = emptyMap(),
        body: JsonElement? = null,
    ): JsonElement? {
        val payload = body?.let { OcJson.encodeToString(JsonElement.serializer(), it) }
            ?: if (method == "GET" || method == "DELETE") null else "{}"
        val res = http.request(urlFor(path, query)) {
            this.method = HttpMethod.parse(method)
            header(HttpHeaders.Authorization, authHeader(password))
            if (payload != null) {
                header(HttpHeaders.ContentType, "application/json")
                setBody(payload)
            }
        }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) throw ApiException(res.status.value, errorText(text, res.status.value))
        return if (text.isBlank()) null else runCatching { OcJson.parseToJsonElement(text) }.getOrNull()
    }

    private fun errorText(body: String, code: Int): String {
        val o = runCatching { OcJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
        val msg = o?.str("message") ?: (o?.obj("data"))?.str("message") ?: (o?.arr("error")?.firstOrNull() as? JsonObject)?.str("message")
        return msg ?: "HTTP $code" + body.take(200).let { if (it.isBlank()) "" else ": $it" }
    }

    private fun dir(d: String?) = mapOf("directory" to d)

    /** One GET against a given address, for the reachability and readiness probes. */
    private suspend fun probeGet(client: HttpClient, url: String, pw: String): Pair<Int, String> {
        val res = client.get(url) { header(HttpHeaders.Authorization, authHeader(pw)) }
        return res.status.value to res.bodyAsText()
    }

    // ---------- server ----------

    suspend fun healthVersionOrThrow(): String? {
        val (code, text) = probeGet(healthHttp, urlFor("/global/health"), password)
        if (code !in 200..299) throw ApiException(code, "health $code")
        return (runCatching { OcJson.parseToJsonElement(text) as? JsonObject }.getOrNull())
            ?.takeIf { it.bool("healthy") == true }?.str("version")
    }

    /** Checks another server before switching to it: does it answer and does the password fit. null means fine, otherwise the reason. */
    suspend fun reachable(url: String, password: String): String? = try {
        val (code, _) = probeGet(healthHttp, url.trimEnd('/') + "/global/health", password)
        when {
            code == 401 -> "Wrong password"
            code !in 200..299 -> "Server answered $code"
            else -> null
        }
    } catch (e: Exception) { "Not reachable — is it on, on the same network, and is `opencode serve` running?" }

    suspend fun healthVersion(): String? = try {
        (call("GET", "/global/health") as? JsonObject)?.takeIf { it.bool("healthy") == true }?.str("version")
    } catch (_: Exception) { null }

    /** Checks a remote server's address and password before saving; returns the OpenCode version or throws with the reason. */
    suspend fun probe(url: String, password: String): String {
        val (code, text) = probeGet(http, url.trimEnd('/') + "/global/health", password)
        return when {
            code == 401 -> throw ApiException(401, "Wrong password")
            code !in 200..299 -> throw ApiException(code, "Server answered $code")
            else -> (runCatching { OcJson.parseToJsonElement(text) as? JsonObject }.getOrNull())?.str("version") ?: "unknown"
        }
    }

    // ---------- sessions ----------

    suspend fun listSessions(directory: String? = null, limit: Int = 300, search: String? = null): List<Session> =
        OcJson.decodeFromJsonElement(
            ListSerializer(Session.serializer()),
            call("GET", "/session", dir(directory) + mapOf("limit" to limit.toString(), "search" to search)) ?: JsonArray(emptyList()),
        )

    suspend fun createSession(directory: String, title: String? = null, permission: JsonArray? = null): Session {
        val body = buildJsonObject {
            title?.let { put("title", it) }
            permission?.let { put("permission", it) }
        }
        return OcJson.decodeFromJsonElement(Session.serializer(), call("POST", "/session", dir(directory), body)!!)
    }

    suspend fun getSession(id: String, directory: String?): Session =
        OcJson.decodeFromJsonElement(Session.serializer(), call("GET", "/session/$id", dir(directory))!!)

    suspend fun renameSession(id: String, directory: String?, title: String) {
        call("PATCH", "/session/$id", dir(directory), buildJsonObject { put("title", title) })
    }

    suspend fun archiveSession(id: String, directory: String?) {
        call("PATCH", "/session/$id", dir(directory), buildJsonObject { putJsonObject("time") { put("archived", nowMillis()) } })
    }

    suspend fun setSessionPermission(id: String, directory: String?, rules: JsonArray) {
        call("PATCH", "/session/$id", dir(directory), buildJsonObject { put("permission", rules) })
    }

    suspend fun deleteSession(id: String, directory: String?) { call("DELETE", "/session/$id", dir(directory)) }

    suspend fun forkSession(id: String, directory: String?, messageID: String?): Session =
        OcJson.decodeFromJsonElement(
            Session.serializer(),
            call("POST", "/session/$id/fork", dir(directory), buildJsonObject { messageID?.let { put("messageID", it) } })!!,
        )

    suspend fun summarize(id: String, directory: String?, providerID: String, modelID: String) {
        call("POST", "/session/$id/summarize", dir(directory), buildJsonObject { put("providerID", providerID); put("modelID", modelID) })
    }

    suspend fun deleteMessage(id: String, directory: String?, messageID: String) { call("DELETE", "/session/$id/message/$messageID", dir(directory)) }

    suspend fun abort(id: String, directory: String?) { call("POST", "/session/$id/abort", dir(directory)) }

    suspend fun revert(id: String, directory: String?, messageID: String) {
        call("POST", "/session/$id/revert", dir(directory), buildJsonObject { put("messageID", messageID) })
    }

    suspend fun unrevert(id: String, directory: String?) { call("POST", "/session/$id/unrevert", dir(directory)) }

    suspend fun statuses(directory: String?): Map<String, JsonObject> =
        (call("GET", "/session/status", dir(directory)) as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonObject)?.let { k to it } }?.toMap().orEmpty()

    suspend fun messages(id: String, directory: String?, limit: Int = 300): List<MessageWithParts> =
        parseMessages(call("GET", "/session/$id/message", dir(directory) + mapOf("limit" to limit.toString())) ?: JsonArray(emptyList()))

    suspend fun todos(id: String, directory: String?): JsonArray =
        call("GET", "/session/$id/todo", dir(directory)) as? JsonArray ?: JsonArray(emptyList())

    suspend fun diff(id: String, directory: String?): JsonArray =
        call("GET", "/session/$id/diff", dir(directory)) as? JsonArray ?: JsonArray(emptyList())

    /** [parts] are ready-made parts: text and files (images as data: URLs). */
    suspend fun promptAsync(
        id: String,
        directory: String?,
        parts: JsonArray,
        providerID: String?,
        modelID: String?,
        agent: String?,
        variant: String?,
        system: String? = null,
    ) {
        val body = buildJsonObject {
            if (!providerID.isNullOrBlank() && !modelID.isNullOrBlank()) {
                putJsonObject("model") { put("providerID", providerID); put("modelID", modelID) }
            }
            if (!agent.isNullOrBlank()) put("agent", agent)
            if (!variant.isNullOrBlank()) put("variant", variant)
            if (!system.isNullOrBlank()) put("system", system)
            put("parts", parts)
        }
        call("POST", "/session/$id/prompt_async", dir(directory), body)
    }

    // ---------- permissions and questions ----------

    suspend fun permissions(directory: String?): List<PermissionReq> =
        (call("GET", "/permission", dir(directory)) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parsePermission) }

    suspend fun replyPermission(requestId: String, directory: String?, reply: String) {
        call("POST", "/permission/$requestId/reply", dir(directory), buildJsonObject { put("reply", reply) })
    }

    suspend fun questions(directory: String?): List<QuestionReq> =
        (call("GET", "/question", dir(directory)) as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseQuestion) }

    suspend fun replyQuestion(requestId: String, directory: String?, answers: List<List<String>>) {
        call("POST", "/question/$requestId/reply", dir(directory), buildJsonObject {
            putJsonArray("answers") { answers.forEach { a -> add(buildJsonArray { a.forEach { add(JsonPrimitive(it)) } }) } }
        })
    }

    suspend fun rejectQuestion(requestId: String, directory: String?) { call("POST", "/question/$requestId/reject", dir(directory)) }

    // ---------- providers and models ----------

    suspend fun connectedProviders(): ProvidersResponse =
        OcJson.decodeFromJsonElement(ProvidersResponse.serializer(), call("GET", "/config/providers")!!)

    suspend fun allProviders(): AllProvidersResponse =
        OcJson.decodeFromJsonElement(AllProvidersResponse.serializer(), call("GET", "/provider")!!)

    suspend fun providerAuthMethods(): Map<String, List<AuthMethod>> =
        OcJson.decodeFromJsonElement(
            MapSerializer(String.serializer(), ListSerializer(AuthMethod.serializer())),
            call("GET", "/provider/auth") ?: JsonObject(emptyMap()),
        )

    suspend fun setApiKey(providerId: String, key: String) {
        call("PUT", "/auth/$providerId", body = buildJsonObject { put("type", "api"); put("key", key) })
    }

    suspend fun removeAuth(providerId: String) { call("DELETE", "/auth/$providerId") }

    /** Returns the sign-in link and a hint; method is an index from [providerAuthMethods]. */
    suspend fun oauthAuthorize(providerId: String, method: Int, inputs: Map<String, String> = emptyMap()): JsonObject =
        call("POST", "/provider/$providerId/oauth/authorize", body = buildJsonObject {
            put("method", method)
            if (inputs.isNotEmpty()) putJsonObject("inputs") { inputs.forEach { (k, v) -> put(k, v) } }
        }) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun oauthCallback(providerId: String, method: Int, code: String?) {
        val payload = OcJson.encodeToString(JsonElement.serializer(), buildJsonObject { put("method", method); code?.let { put("code", it) } })
        val res = longHttp.request(urlFor("/provider/$providerId/oauth/callback")) {
            this.method = HttpMethod.Post
            header(HttpHeaders.Authorization, authHeader(password))
            header(HttpHeaders.ContentType, "application/json")
            setBody(payload)
        }
        val text = res.bodyAsText()
        if (!res.status.isSuccess()) throw ApiException(res.status.value, errorText(text, res.status.value))
    }

    /** After keys change, instances must be recreated to see the new providers. */
    suspend fun disposeAll() { call("POST", "/global/dispose") }

    // ---------- server-sent events ----------

    /**
     * Reads `/global/event` until the stream ends or the caller cancels.
     * [onOpen] runs when the server accepts the stream; [onLine] gets every line
     * of it (including the blank ones that separate events).
     */
    suspend fun streamLines(
        path: String,
        query: Map<String, String?> = emptyMap(),
        onOpen: suspend () -> Unit,
        onLine: suspend (String) -> Unit,
    ) {
        streamHttp.prepareGet(urlFor(path, query)) {
            header(HttpHeaders.Authorization, authHeader(password))
            header(HttpHeaders.Accept, "text/event-stream")
        }.execute { res ->
            if (!res.status.isSuccess()) return@execute
            onOpen()
            val channel: ByteReadChannel = res.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line() ?: break
                onLine(line)
            }
        }
    }

    // ---------- config, agents, MCP ----------

    suspend fun config(): JsonObject = call("GET", "/config") as? JsonObject ?: JsonObject(emptyMap())

    /** Global config (~/.config/opencode): the server writes the file itself and applies changes without a restart. */
    suspend fun patchGlobalConfig(patch: JsonObject): JsonObject = call("PATCH", "/global/config", body = patch) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun patchConfig(patch: JsonObject): JsonObject = call("PATCH", "/config", body = patch) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun agents(): List<AgentInfo> =
        OcJson.decodeFromJsonElement(ListSerializer(AgentInfo.serializer()), call("GET", "/agent") ?: JsonArray(emptyList()))

    suspend fun commands(): List<NamedItem> =
        OcJson.decodeFromJsonElement(ListSerializer(NamedItem.serializer()), call("GET", "/command") ?: JsonArray(emptyList()))

    suspend fun skills(): List<NamedItem> =
        OcJson.decodeFromJsonElement(ListSerializer(NamedItem.serializer()), call("GET", "/skill") ?: JsonArray(emptyList()))

    /** Tools the server gives the model (built-in and MCP), with descriptions and parameter schemas. */
    suspend fun tools(providerId: String, modelId: String, directory: String?): JsonArray =
        call("GET", "/experimental/tool", mapOf("provider" to providerId, "model" to modelId, "directory" to directory)) as? JsonArray ?: JsonArray(emptyList())

    suspend fun mcpStatus(): JsonObject = call("GET", "/mcp") as? JsonObject ?: JsonObject(emptyMap())

    suspend fun mcpConnect(name: String) { call("POST", "/mcp/$name/connect") }
    suspend fun mcpDisconnect(name: String) { call("POST", "/mcp/$name/disconnect") }

    suspend fun mcpAdd(name: String, config: JsonObject) {
        call("POST", "/mcp", body = buildJsonObject { put("name", name); put("config", config) })
    }

    /** The server's default working directory (for a remote device, where the app has no projects of its own). */
    suspend fun serverDirectory(): String? = (call("GET", "/path") as? JsonObject)?.str("directory")

    suspend fun projects(): JsonArray = call("GET", "/project") as? JsonArray ?: JsonArray(emptyList())

    suspend fun findFiles(query: String, directory: String?): List<String> =
        (call("GET", "/find/file", dir(directory) + mapOf("query" to query, "limit" to "20")) as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

    /**
     * A file's contents from the server's computer (inside the project): text as is, anything else as
     * base64 with its type. For Remote Control, where the files are not on this phone.
     */
    suspend fun fileContent(path: String, directory: String?): JsonObject? =
        call("GET", "/file/content", dir(directory) + mapOf("path" to path)) as? JsonObject

    private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())
}
