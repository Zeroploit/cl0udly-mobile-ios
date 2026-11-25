package app.ocmobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.Graph
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Agent team. Helpers are OpenCode subagents (the agent section of the config, mode = subagent);
 * the main agent hands them parts of the task with the task tool, and they work in parallel in child sessions.
 *  - manual: the user sets the lineup: name, role and model of each;
 *  - auto: the user gives a set of models and a cap, the main agent decides how many helpers
 *    to start and on which model (none for a simple task).
 * All helpers are named team-â€¦ so they can be found and removed from the config.
 *
 * KMP port: the app is always a client of a computer running `opencode serve`, so the
 * team file inside the environment ([FILE]) and the server's own spend (`Graph.usage`)
 * are not reachable from here â€” the file is stubbed below, the rest is the Android code.
 */
object Team {
    @Serializable
    data class Member(val name: String, val role: String, val provider: String = "", val model: String = "", val on: Boolean = true)

    private val json = Json { ignoreUnknownKeys = true }
    private val prefs get() = Graph.prefs

    val mode: String get() = prefs.teamMode

    /** "Add sub-agent" from the + sheet: the Sub-agents screen opens with the new-helper form. */
    var addRequested = false

    /** A helper just created: its cloud flies into the row above the input once. */
    var justAdded by mutableStateOf<String?>(null)
    val on: Boolean get() = mode != "off"

    /** Compose-observable: it reads [Prefs.teamMembers], which is Compose state itself. */
    var members: List<Member>
        get() = runCatching { json.decodeFromString(ListSerializer(Member.serializer()), prefs.teamMembers) }.getOrDefault(emptyList())
        set(v) { prefs.teamMembers = json.encodeToString(ListSerializer(Member.serializer()), v) }

    /** Models for auto mode: provider/model; empty means the current model. */
    var pool: List<String>
        get() = prefs.teamPool.split(',').map { it.trim() }.filter { it.contains('/') }
        set(v) { prefs.teamPool = v.joinToString(",") }

    val max: Int get() = prefs.teamMax.toIntOrNull()?.coerceIn(1, 6) ?: 3

    /** Helpers that work without asking first ("Always" in Team or on the OK card), as provider/model. */
    val always: Set<String> get() = prefs.teamAlways.lines().filter { it.isNotBlank() }.toSet()
    fun setAlways(ref: String, on: Boolean) { prefs.teamAlways = (if (on) always + ref else always - ref).joinToString("\n") }

    /** The helper name that runs on [ref], if one does. */
    fun helperFor(ref: String): String? = when (mode) {
        "manual" -> members.firstOrNull { "${it.provider}/${it.model}" == ref }?.let { slug(it.name) }
        "auto" -> slug(label(ref)).takeIf { ref in pool.ifEmpty { listOf("${prefs.providerId}/${prefs.modelId}") } }
        else -> null
    }

    /** The model a helper runs on, as provider/model, from its config entry. */
    fun refOf(agent: String): String? = when (mode) {
        "manual" -> members.firstOrNull { slug(it.name) == agent }?.let { m -> if (m.provider.isNotBlank() && m.model.isNotBlank()) "${m.provider}/${m.model}" else null }
        "auto" -> pool.ifEmpty { listOf("${prefs.providerId}/${prefs.modelId}") }.firstOrNull { slug(label(it)) == agent }
        else -> null
    }

    /** A helper's name for people: the member's name, or its model. */
    fun display(agent: String?): String = when {
        agent.isNullOrBlank() -> "Helper"
        agent == "general" -> "General helper"
        agent == "explore" -> "Explorer"
        else -> members.firstOrNull { slug(it.name) == agent }?.name ?: refOf(agent)?.let(::label) ?: agent.removePrefix("team-")
    }

    /** What a model costs: free, part of a subscription, or the price per million tokens. */
    fun cost(ref: String?): String {
        if (ref == null) return "chat model"
        val (p, m) = ref.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val c = Graph.catalog.model(p, m)?.cost
        fun num(k: String) = (c?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: 0.0
        val inp = num("input"); val out = num("output")
        fun f(d: Double) = if (d >= 10) fixed(d, 0) else fixed(d, 2).trimEnd('0').trimEnd('.')
        return when {
            m.endsWith("-free") || p == "pollinations" -> "free"
            p == "opencode-go" -> "subscription: OpenCode Go"
            p == "github-copilot" || p == "copilot-chat" -> "subscription: GitHub Copilot"
            inp == 0.0 && out == 0.0 -> "free"
            else -> "paid: \$${f(inp)} in, \$${f(out)} out per 1M tokens"
        }
    }

    /** Java's `String.format(Locale.US, "%.<digits>f", d)`: the same HALF-UP rounding, a "." separator. */
    private fun fixed(d: Double, digits: Int): String {
        val f = when (digits) { 0 -> 1L; 1 -> 10L; 2 -> 100L; else -> 1000L }
        val n = kotlin.math.round(d * f).toLong()
        val a = kotlin.math.abs(n)
        var s = (a / f).toString()
        if (digits > 0) s += "." + (a % f).toString().padStart(digits, '0')
        return if (n < 0) "-$s" else s
    }

    /**
     * What is left of the subscription, as the main agent sees it.
     * Android read the app's own spend (`Graph.usage.snapshot`); a client does not spend
     * or count anything itself, so the note is left out.
     */
    private fun budget(): String? = null

    fun slug(name: String) = "team-" + name.lowercase().replace(Regex("[^a-z0-9Ð°-ÑÑ‘]+"), "-").trim('-').ifBlank { "helper" }

    private fun label(ref: String): String {
        val (p, m) = ref.split('/', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return Graph.catalog.model(p, m)?.name?.ifBlank { null } ?: m
    }

    /** Helpers that should be in the config now: name â†’ agent description. */
    private fun agents(): Map<String, JsonObject> = when (mode) {
        "manual" -> members.filter { it.on }.associate { m ->
            slug(m.name) to buildJsonObject {
                put("mode", "subagent")
                put("description", "${m.name}: ${m.role}".take(300))
                if (m.provider.isNotBlank() && m.model.isNotBlank()) put("model", "${m.provider}/${m.model}")
                put("prompt", "You are ${m.name}, a member of a small team led by the main agent. Your role: ${m.role}. " +
                    "Do only the part you were given, do it well, and finish with a short report of what you did and what you found.")
            }
        }
        "auto" -> pool.ifEmpty { listOf("${prefs.providerId}/${prefs.modelId}") }.associate { ref ->
            slug(label(ref)) to buildJsonObject {
                put("mode", "subagent")
                put("description", "General helper on ${label(ref)}. Give it a self-contained part of the task.")
                put("model", ref)
                put("prompt", "You are a helper in a team led by the main agent. Do exactly the part you were given and finish with a short report.")
            }
        }
        else -> emptyMap()
    }

    /**
     * Writes the helpers to the shared OpenCode config and disables old team-â€¦ ones. Called when the mode or lineup changes
     * and before sending, if the team is on.
     */
    @kotlin.concurrent.Volatile private var synced: String? = null

    suspend fun sync(force: Boolean = false) {
        readFile()
        writeFile()
        val want = agents()
        // the same lineup is already written: leave the config alone, OpenCode rereads it on every edit
        val key = want.toString()
        if (!force && key == synced) return
        val have = runCatching { Graph.api.agents().map { it.name }.filter { it.startsWith("team-") } }.getOrDefault(emptyList())
        val patch = buildJsonObject {
            put("agent", buildJsonObject {
                want.forEach { (k, v) -> put(k, v) }
                (have - want.keys).forEach { put(it, buildJsonObject { put("disable", true) }) }
            })
        }
        // shared config: chats and projects live in different folders, helpers are needed in all of them
        if (runCatching { Graph.api.patchGlobalConfig(patch) }.isSuccess) synced = key
    }

    // ---------- the team file: the main agent can change its helpers itself ----------

    /** Where the agent sees the lineup (inside the environment) and where the app reads it back. */
    const val FILE = "/root/.config/opencode/cl0udly-team.json"
    private val pretty = Json { prettyPrint = true; encodeDefaults = true }

    @Serializable
    private data class Lineup(val mode: String, val max: Int, val members: List<FileMember>, val pool: List<String>)
    @Serializable
    private data class FileMember(val name: String, val role: String, val model: String = "", val on: Boolean = true)

    private fun lineupText(): String = pretty.encodeToString(Lineup.serializer(), Lineup(
        mode, max,
        members.map { FileMember(it.name, it.role, if (it.model.isBlank()) "" else "${it.provider}/${it.model}", it.on) },
        pool,
    ))

    /**
     * The agent edited the file since the app last wrote it: take its lineup.
     * A client cannot read a file on the computer running the server, and
     * [Graph.devices] is always remote here, so there is nothing to read.
     */
    fun readFile() {
        if (Graph.devices.isRemote) return
    }

    /**
     * Writes the lineup where the agent can read it â€” a file next to the server, which a
     * client does not have. What the app last wrote is still remembered in
     * [Prefs.teamFileLast], as on Android; the helpers themselves go into the config in [sync].
     */
    fun writeFile() {
        if (Graph.devices.isRemote) return
        val text = lineupText()
        if (text == prefs.teamFileLast) return
        prefs.teamFileLast = text
    }

    /** How the agent changes its helpers: one short line, so it costs almost nothing. */
    private fun manage(): String? = if (Graph.devices.isRemote) null else
        "You can manage your helper subagents yourself: edit $FILE (mode off|manual|auto; members: name, role, model \"provider/model\", on true|false; " +
            "pool: models for auto; max). Add, rename, switch off or change a helper's model there; it applies from the next message. Tell the user what you changed."

    /** Note to the main agent attached to every message: how to use the team and how to change it. */
    fun instruction(): String? {
        val list = agents()
        if (list.isEmpty() || !on) return manage()
        val names = list.entries.joinToString("\n") { (k, v) -> "- $k â€” " + ((v["description"] as? JsonPrimitive)?.content ?: "") + " (" + cost(refOf(k)) + ")" } +
            "\nThe user approves each helper the first time it is used. Prefer free and subscription helpers; paid ones spend the user's money." +
            (budget()?.let { "\n$it" } ?: "")
        return listOfNotNull(when (mode) {
            "manual" -> "You lead a team. Split the task and hand parts to these subagents with the task tool " +
                "(run independent parts in parallel, several task calls in one message), then check and combine their results:\n$names"
            else -> "You can lead a team of up to $max helpers. First decide whether the task needs help at all â€” for small tasks do it yourself. " +
                "If it does, split it into self-contained parts and hand them out with the task tool (several task calls in one message run in parallel). " +
                "Pick a stronger model for hard parts and a faster one for simple parts. Then check and combine the results. Helpers:\n$names"
        }, manage()).joinToString("\n\n")
    }
}
