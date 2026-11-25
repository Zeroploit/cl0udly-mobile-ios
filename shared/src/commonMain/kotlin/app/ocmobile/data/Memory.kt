package app.ocmobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.Graph
import kotlinx.serialization.json.JsonPrimitive

/** Directories for "regular" chats and memory inside the environment (proot). Everything outside CHAT_DIR is the Code tab. */
// CHAT_DIR itself is in commonMain (Consts.kt): the shared API models need it.
const val MEMORY_DIR = "/root/memory"

/** Where the global rules live inside the environment: the app keeps a memory block in it. */
private const val GLOBAL_AGENTS_MD = "/root/.config/opencode/AGENTS.md"

/**
 * A file on the memory shelf: its name and its text.
 *
 * Ported from Android's `java.io.File` usage. On a client the folder lives on the computer, not on
 * the phone, so the text is a mirror kept by [Memory]: reads come from the OpenCode API, and a write
 * here changes the mirror (the screen shows it at once) — the 1.x API has no route for writing a
 * file, so the agent applies the change on the computer on the next message.
 */
class MemFile internal constructor(
    private val store: MutableMap<String, String>,
    val name: String,
) {
    val nameWithoutExtension: String get() = name.substringBeforeLast('.')
    val extension: String get() = name.substringAfterLast('.', "")

    var text: String
        get() = store[name].orEmpty()
        set(value) { store[name] = value }

    fun exists(): Boolean = name in store
    fun readText(): String = text
    fun readLines(): List<String> = text.lines()
    fun writeText(t: String) { store[name] = t }
    fun appendText(t: String) { store[name] = store[name].orEmpty() + t }
    fun delete(): Boolean = store.remove(name) != null
    fun length(): Long = text.length.toLong()

    override fun equals(other: Any?): Boolean = other is MemFile && other.store === store && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name
}

/**
 * Memory: plain markdown files in /root/memory, topic "shelves" and a MEMORY.md index.
 * The agent learns about it from a block in the global AGENTS.md that the app maintains itself.
 *
 * The folder is on the computer: [load] pulls it over the OpenCode API into a mirror, and everything
 * else here is the Android logic over that mirror.
 */
object Memory {
    private const val START = "<!-- ocm-memory:start -->"
    private const val END = "<!-- ocm-memory:end -->"

    /** The mirror of the memory folder: by file name. */
    private val store = LinkedHashMap<String, String>()

    /** Project notes under memory/projects, also mirrored. */
    private val noteStore = LinkedHashMap<String, String>()

    /** The user's rules: AGENTS.md on the computer, mirrored for reading. */
    private var agentsMd = ""

    /** Bumped whenever the mirror changes, so screens that read it recompose after [load]. */
    var version by mutableStateOf(0)
        private set

    val defaultPrompt = """
# Memory

You have a persistent memory: plain markdown files in $MEMORY_DIR. It survives between conversations.

- Its current contents are already loaded below under "What you remember". Do NOT run ls, cat or read on $MEMORY_DIR just to recall something — answer from what is below right away. Open a shelf file only when the section says it was too long to include.
- Use what you remember quietly; do not announce that you looked it up.
- IMPORTANT: remembering means actually writing to a file with your file tools (write/edit/bash). Never just say "I will remember" — save it in the same turn, before you reply. If the shelf file does not exist, create it, then add its line to MEMORY.md.
- When the user tells you something lasting — preferences, people, projects, goals, routines, decisions, facts about their life or work — or asks you to remember something, save it. Do not save one-off details, and do not save sensitive things unless asked.
- Keep memory sorted into shelves: one file per topic, for example about-me.md, work.md, projects.md, people.md, routines.md, health.md, ideas.md. Put each fact on the shelf that fits best, create a new shelf when none fits, merge duplicates, correct outdated facts, remove wrong ones.
- Every shelf starts with a one-line description of what belongs on it. MEMORY.md stays a short index, one line per shelf: "- [Title](file.md) — what is inside".
- If inbox.md has notes, sort them onto the right shelves and clear it.
- When asked to forget something, delete it. After changing memory, say in one short line what you saved or removed.
""".trim()

    /** How much memory text to put into the instructions in full; beyond that, only the index and the start of each shelf. */
    private const val INLINE_LIMIT = 24_000
    private const val SHELF_LIMIT = 4_000

    /**
     * Memory contents right in the instructions: without this the agent spends two extra rounds
     * at the start of every chat (ls/cat the index, then cat the shelves), about 4 s on a free model.
     */
    fun snapshot(): String {
        val index = store["MEMORY.md"]?.trim().orEmpty()
        val files = shelves().map { it.name to runCatching { it.readText().trim() }.getOrDefault("") }.filter { it.second.isNotEmpty() }
        val whole = files.sumOf { it.second.length } <= INLINE_LIMIT
        return buildString {
            append("## What you remember\n\n")
            append("### MEMORY.md\n").append(index.ifEmpty { "(empty)" }).append("\n")
            if (files.isEmpty()) append("\nNo shelves yet.\n")
            files.forEach { (name, text) ->
                append("\n### ").append(name).append("\n")
                if (whole || text.length <= SHELF_LIMIT) append(text).append("\n")
                else append(text.take(SHELF_LIMIT)).append("\n… (too long to include — open $MEMORY_DIR/").append(name).append(" for the rest)\n")
            }
        }.trim()
    }

    /** Always in AGENTS.md: how to hand the user a file and where their attachments are. */
    val filesPrompt = """
# Files and the user

The user talks to you from a phone app. It cannot see your terminal or your disk, only your replies.
- When you make or find a file the user should get — audio (mp3, wav), video (mp4), images, documents, archives, any format — save it (for example in /root/outbox) and put its absolute path in your reply as a markdown link on its own line: [song.mp3](/root/outbox/song.mp3). The app turns that link into a card the user can play, open, save or share. Never paste binary data or base64 into the chat.
- Files the user attaches are saved in /root/uploads; the message tells you the exact path. Open them with your tools.
""".trim()

    /**
     * Reads the memory folder, the project notes and the user's rules from the computer into the
     * mirror. Nothing is cached to disk here: the phone keeps no copy of the shelves between runs.
     */
    suspend fun load() {
        if (!Graph.isPrefsReady()) return
        val dir = runCatching { Graph.api.serverDirectory() }.getOrNull()
        val shelves = LinkedHashMap<String, String>()
        read("$MEMORY_DIR/MEMORY.md", dir)?.let { shelves["MEMORY.md"] = it }
        val listed = runCatching { Graph.api.listFiles(MEMORY_DIR, dir) }.getOrNull().orEmpty()
        // the listing is the truth; the known topics are the fallback for a server that doesn't list folders
        val names = if (listed.isNotEmpty()) listed.filter { !it.isDir && it.name.endsWith(".md") && it.name != "MEMORY.md" }.map { it.name }
        else (topics.map { "${it.first}.md" } + listOf("inbox.md", "projects.md", "notes.md")).distinct()
        names.forEach { n -> read("$MEMORY_DIR/$n", dir)?.let { shelves[n] = it } }
        val notes = LinkedHashMap<String, String>()
        runCatching { Graph.api.listFiles("$MEMORY_DIR/projects", dir) }.getOrNull().orEmpty()
            .filter { !it.isDir && it.name.endsWith(".md") }
            .forEach { e -> read("$MEMORY_DIR/projects/${e.name}", dir)?.let { notes[e.name] = it } }
        val rules = read(GLOBAL_AGENTS_MD, dir)
        store.clear(); store.putAll(shelves)
        noteStore.clear(); noteStore.putAll(notes)
        agentsMd = rules.orEmpty()
        version++
    }

    private suspend fun read(path: String, directory: String?): String? {
        val o = runCatching { Graph.api.fileContent(path, directory) }.getOrNull() ?: return null
        val type = (o["type"] as? JsonPrimitive)?.content
        if (type == "binary") return null
        return (o["content"] as? JsonPrimitive)?.content
    }

    /** The index file, created with its default text when the folder has none yet. */
    fun index(): MemFile {
        if ("MEMORY.md" !in store) store["MEMORY.md"] = "# Memory index\n\nNothing saved yet.\n"
        return MemFile(store, "MEMORY.md")
    }

    /** The index's size in characters; 0 when there is no index yet (no write, so it is safe while composing). */
    fun indexLength(): Long = store["MEMORY.md"]?.length?.toLong() ?: 0L

    /** Detailed project notes: not put into the instructions, the agent opens them via a link on the Projects shelf. */
    fun projectNotes(): List<MemFile> =
        noteStore.keys.filter { it.endsWith(".md") }.sorted().map { MemFile(noteStore, it) }

    fun shelves(): List<MemFile> =
        store.keys.filter { it.endsWith(".md") && it != "MEMORY.md" }.sorted().map { MemFile(store, it) }

    /** One shelf by file name: the read-only view the graph and the sheet work on. */
    fun shelf(name: String): MemFile = MemFile(store, name)

    /** The folder as the graph reads it: the index and the shelves, by name. */
    fun graphFiles(): Map<String, String> = store.toMap()

    /** Shelf topics that can be picked in settings: file, title, what goes on it. */
    val topics = listOf(
        Triple("about-me", "About me", "name, city, language, how to talk to me"),
        Triple("work", "Work", "job, schedule, tools, colleagues"),
        Triple("projects", "Projects", "what I build and where it stands"),
        Triple("people", "People", "family, friends, who is who"),
        Triple("routines", "Routines", "habits, daily plans, reminders"),
        Triple("health", "Health", "sleep, sport, food, wellbeing"),
        Triple("ideas", "Ideas", "plans, wishes, things to try"),
        Triple("music", "Music", "taste, tracks, gear"),
    )

    fun prompt(): String {
        val p = Graph.prefs
        val extra = buildList {
            when (p.memoryMode) {
                "off" -> add("- Save to memory ONLY when the user explicitly asks you to remember something. Otherwise only read it.")
                "ask" -> add("- When the user asks you to remember something, save it yourself. When they mention something lasting about themselves without asking (preferences, people, projects, goals, routines, decisions), do not save it yourself: call the remember tool with the fact (one short line in the user's language) and a shelf (a shelf file name without .md). The user sees a \"Remember this?\" card and decides. At most two per reply, never something already remembered, never one-off details or sensitive things.")
            }
            val t = p.memoryTopics.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (t.isNotEmpty()) add("- Keep only these shelves: " + t.joinToString(", ") { "$it.md" } + ". Do not create other shelves; put anything else in the closest one or skip it.")
        }
        val base = if (extra.isEmpty()) defaultPrompt else defaultPrompt + "\n\n## Settings chosen by the user\n" + extra.joinToString("\n")
        val custom = p.memoryCustom.trim()
        return if (custom.isEmpty()) base else base + "\n\n## The user's own instructions for memory\n" + custom
    }

    /** Fact lines on a shelf. */
    fun facts(f: MemFile): List<String> = runCatching { f.readLines() }.getOrDefault(emptyList())
        .filter { it.trimStart().startsWith("- ") }.map { it.trimStart().removePrefix("- ").trim() }

    fun removeFact(f: MemFile, fact: String) {
        runCatching {
            val lines = f.readLines().toMutableList()
            val i = lines.indexOfFirst { it.trimStart().removePrefix("- ").trim() == fact && it.trimStart().startsWith("- ") }
            if (i >= 0) { lines.removeAt(i); f.writeText(lines.joinToString("\n") + "\n") }
            sync()
        }
    }

    fun addFact(f: MemFile, fact: String) {
        runCatching {
            val head = if (f.exists()) "" else "# ${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() }}\n\n"
            val t = if (f.exists()) f.readText() else ""
            f.writeText(head + t.trimEnd() + (if (t.isBlank()) "" else "\n") + "- " + fact.trim().replace("\n", " ") + "\n")
            if (f.name != "MEMORY.md") {
                val idx = index()
                val it = idx.readText()
                if (!it.contains("(${f.name})")) idx.writeText(it.replace("Nothing saved yet.\n", "").trimEnd() + "\n- [${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }}](${f.name})\n")
            }
            sync()
        }
    }

    /** All memory text as one file, to share or save. */
    fun exportText(): String = buildString {
        append(store["MEMORY.md"]?.trim().orEmpty()).append("\n\n")
        (shelves() + projectNotes()).forEach { append("---\n\n").append(runCatching { it.readText().trim() }.getOrDefault("")).append("\n\n") }
    }.trim()

    /**
     * Syncs the memory block in AGENTS.md with the settings and memory files.
     *
     * Android created the folders and rewrote the block in `/root/.config/opencode/AGENTS.md` on the
     * phone. The client has no filesystem and the 1.x API has no route to write one, so the block is
     * the server's business: this stays a no-op with the same name, which is what every caller
     * already expects, and memory itself travels with each request through [systemBlock].
     */
    fun sync() {
        // On the computer the app maintains the block; a client cannot, and nothing waits on it.
    }

    /** How to use memory and what's in it: sent as the system part of every request in a chat with memory on. */
    fun systemBlock(): String {
        // too big to send every time: the map (from the graph) instead, the agent opens what it needs
        val size = shelves().sumOf { it.length() }
        return prompt() + "\n\n" + if (size > MAP_LIMIT) MemoryIO.map(store, MEMORY_DIR) else snapshot()
    }
    private const val MAP_LIMIT = 12_000

    /**
     * .md notes from the user (a memory export from Claude, say) onto the shelves.
     * Android read them from content URIs with a zip unpacker; here the caller hands over the
     * notes it already has (name and text), and the merge is [MemoryIO.import].
     */
    fun importFiles(files: List<Pair<String, String>>): Int {
        val n = MemoryIO.import(files, store)
        sync()
        version++
        return n
    }

    /** All shelves as text, the way the zip used to hold them, for sharing or saving. */
    fun exportFiles(): List<Pair<String, String>> = shelves().map { it.name to it.readText() }

    /** The user's rules: AGENTS.md without the memory service block (the app maintains that). */
    fun userRules(): String {
        val old = agentsMd
        val a = old.indexOf(START); val b = old.indexOf(END)
        return (if (a >= 0 && b > a) old.substring(0, a) + old.substring(b + END.length) else old).trim()
    }

    /**
     * Writes the user's rules into the mirror of AGENTS.md. As with every other write on a client,
     * the computer's file is the agent's to change: the screen shows the new text, the agent applies
     * it there.
     */
    fun saveUserRules(text: String) {
        agentsMd = text.trim() + "\n"
        sync()
        version++
    }

    fun addNote(text: String) {
        runCatching {
            val f = MemFile(store, "inbox.md")
            val head = if (f.exists()) "" else "Notes added by hand, waiting to be sorted onto shelves."
            f.appendText(head + "- " + text.trim().replace("\n", " ") + "\n")
            sync()
            version++
        }
    }
}
