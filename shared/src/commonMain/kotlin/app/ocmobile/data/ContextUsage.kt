package app.ocmobile.data

import app.ocmobile.Graph
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One slice of context: what it is and how many tokens it takes. */
data class CtxPart(val key: String, val label: String, val tokens: Long)

/** What fills the model's window in this chat, like /context in Claude Code. */
data class ContextInfo(
    val window: Long,
    val used: Long,
    val buffer: Long,
    val parts: List<CtxPart>,
    val messages: Int,
    val yours: Int,
    val cost: Double,
    val tokensIn: Long,
    val tokensOut: Long,
    val measured: Boolean,
) {
    val free get() = (window - used - buffer).coerceAtLeast(0)
    val fraction get() = if (window > 0) (used.toFloat() / window).coerceIn(0f, 1f) else 0f
}

/**
 * The server doesn't give a context breakdown, so we build it ourselves:
 * tools from their descriptions and schemas at /experimental/tool, memory from AGENTS.md,
 * the system prompt from the first reply (its input minus tools, memory and the first message),
 * and messages are the rest of the real token count of the last reply.
 *
 * Ported from the Android `ContextUsage.kt`. Memory files live on the computer here
 * (a client can't read them), so the memory slice is 0 — the server counts it instead.
 */
object ContextUsage {
    private val builtIn = setOf(
        "invalid", "question", "bash", "read", "glob", "grep", "edit", "write", "task", "webfetch", "todowrite", "todoread",
        "websearch", "codesearch", "skill", "apply_patch", "patch", "list", "lsp", "batch", "multiedit", "plan_enter", "plan_exit",
    )
    private val toolCache = HashMap<String, List<Pair<String, Int>>>()

    /** The model's tools: id and size in characters. Cached per model and folder. */
    suspend fun tools(providerId: String, modelId: String, directory: String?): List<Pair<String, Int>> {
        val key = "$providerId/$modelId@$directory"
        toolCache[key]?.let { return it }
        val list = runCatching { Graph.api.tools(providerId, modelId, directory) }.getOrNull()?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            id to ((o["description"]?.jsonPrimitive?.content?.length ?: 0) + (o["parameters"]?.toString()?.length ?: 0))
        } ?: return emptyList()
        if (list.isNotEmpty()) toolCache[key] = list
        return list
    }

    private fun tok(chars: Int) = (chars / 4).toLong()

    /** Memory and rules: on a client these live on the computer, not the phone. */
    fun memoryTokens(directory: String?): Long = 0

    /** Headroom for auto-compaction, as in OpenCode: 15 % of the window, but at least 16k for windows of 32k and up. */
    fun buffer(window: Long): Long = if (window <= 0) 0 else maxOf((window * 0.15).toLong(), if (window >= 32_000) 16_000L else 0L)

    fun compute(messages: List<ChatMessage>, tools: List<Pair<String, Int>>, window: Long, directory: String?): ContextInfo {
        val infos = messages.map { it.info }
        // The tokens a local val instead of a smart cast on `it.tokens`: the
        // model now lives in :shared, and Kotlin refuses to smart cast a
        // property declared in another module.
        val measuredTokens = infos.filter { !it.isUser }.mapNotNull { i -> i.tokens?.takeIf { it.sum > 0 } }
        val last = measuredTokens.lastOrNull()
        val first = measuredTokens.firstOrNull()

        val skills = tools.filter { it.first == "skill" }.sumOf { tok(it.second) }
        val system = tools.filter { it.first in builtIn && it.first != "skill" }.sumOf { tok(it.second) }
        val mcp = tools.filter { it.first !in builtIn }.sumOf { tok(it.second) }
        val memory = memoryTokens(directory)
        val firstUser = messages.firstOrNull { it.info.isUser }?.parts?.sumOf { it.text.length } ?: 0
        val prompt = first?.let { (it.input + it.cache.read + it.cache.write) - skills - system - mcp - memory - tok(firstUser) }
            ?.takeIf { it in 500..20_000 } ?: 2_200L

        val fixed = prompt + system + mcp + skills + memory
        val used = last?.let { it.input + it.cache.read + it.cache.write + it.output }
            ?: (fixed + tok(messages.sumOf { m -> m.parts.sumOf { it.text.length } }))
        val parts = listOf(
            CtxPart("prompt", "System prompt", prompt),
            CtxPart("tools", "System tools", system),
            CtxPart("mcp", "MCP tools", mcp),
            CtxPart("skills", "Skills", skills),
            CtxPart("memory", "Memory files", memory),
            CtxPart("messages", "Messages", (used - fixed).coerceAtLeast(0)),
        )
        return ContextInfo(
            window = window, used = used, buffer = buffer(window), parts = parts,
            messages = infos.size, yours = infos.count { it.isUser },
            cost = infos.sumOf { it.cost ?: 0.0 },
            tokensIn = measuredTokens.sumOf { it.input + it.cache.read + it.cache.write },
            tokensOut = measuredTokens.sumOf { it.output + it.reasoning },
            measured = last != null,
        )
    }
}
