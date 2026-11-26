package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.api.NamedItem
import app.ocmobile.api.base64Bytes
import app.ocmobile.platform.PickedFile
import kotlinx.coroutines.launch

/**
 * Skills are folders with SKILL.md in [DIR], the same format as Claude, ChatGPT and Codex:
 * YAML with name and description at the top, instructions below. The agent loads a skill itself
 * when the description fits, or when the user types "/name".
 *
 * Ported from the Android `Skills.kt`. Android read and wrote those folders on the phone's own
 * rootfs; a client has no filesystem on the server's computer, so the list comes from `GET /skill`
 * and every write is asked of the agent in a chat of its own, which is the one holding the file
 * tools. Nothing is invented: the routes used here are the ones [app.ocmobile.api.OpenCodeApi] has.
 */
object Skills {
    const val DIR = "/root/.config/opencode/skills"

    /**
     * One skill as the app shows it. Android also kept its `dir: File`; a client has no such
     * handle, and [files] is what the server tells us, which is SKILL.md alone.
     */
    data class Skill(val name: String, val description: String, val body: String, val files: Int)

    /** For AGENTS.md: so a skill can be asked for right in the chat. */
    val prompt = """
# Skills

Skills are folders in $DIR, one per skill, each with a SKILL.md:

    ---
    name: short-kebab-name
    description: One or two sentences: what it does AND when to use it (this is how you decide to load it).
    ---

    Instructions in markdown. Extra files (scripts, templates, references) may sit next to it.

- When the user asks to create, save or edit a skill, write or edit that SKILL.md with your file tools. Ask what it should do first if it is unclear. Keep the name lowercase with dashes.
- After saving, tell the user in one line that they can call it with /name. New skills appear in the app's "/" menu right away.
""".trim()

    /** The user's own skills: the ones the server reports under [DIR]. */
    suspend fun local(): List<Skill> = local(runCatching { Graph.api.skills() }.getOrDefault(emptyList()))

    /** The same list from an already fetched [items], so a screen asks the server once. */
    fun local(items: List<NamedItem>): List<Skill> = items
        .filter { it.location?.startsWith(DIR) == true }
        .mapNotNull { item ->
            val (name, desc, body) = parse(item.content.orEmpty())
            // The server's name is the folder; SKILL.md may carry its own, and that one wins.
            Skill(name ?: item.name, desc ?: item.description.orEmpty(), body, 1)
        }
        .sortedBy { it.name }

    /** name, description and body from SKILL.md. Without a header, everything is the body. */
    fun parse(text: String): Triple<String?, String?, String> {
        val t = text.replace("\r\n", "\n").trimStart('\uFEFF')
        if (!t.startsWith("---\n")) return Triple(null, null, t.trim())
        val end = t.indexOf("\n---", 4).takeIf { it > 0 } ?: return Triple(null, null, t.trim())
        val head = t.substring(4, end)
        val body = t.substring(end + 4).trimStart('-').trim()
        fun field(k: String): String? {
            val lines = head.lines()
            val i = lines.indexOfFirst { it.startsWith("$k:") }.takeIf { it >= 0 } ?: return null
            var v = lines[i].substringAfter(':').trim()
            // "description: >" and multi-line values
            if (v == ">" || v == "|" || v == ">-" || v == "|-") v = lines.drop(i + 1).takeWhile { it.startsWith(" ") }.joinToString(" ") { it.trim() }
            return v.removeSurrounding("\"").removeSurrounding("'").replace("\\\"", "\"").takeIf { it.isNotBlank() }
        }
        return Triple(field("name"), field("description"), body)
    }

    fun slug(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9а-яё]+"), "-").trim('-').take(64).ifBlank { "skill" }

    fun render(name: String, description: String, body: String): String =
        "---\nname: $name\ndescription: \"" + description.replace("\n", " ").replace("\"", "\\\"").trim() + "\"\n---\n\n" + body.trim() + "\n"

    /**
     * Save a skill and return its folder name.
     *
     * On Android this wrote the folder itself and renamed it on a rename. A client cannot write on
     * the server's computer, so the SKILL.md travels to the agent in a chat of its own and the agent
     * writes it with its own file tools; the rename is the same chat deleting the old folder.
     */
    suspend fun save(name: String, description: String, body: String, oldName: String? = null): String {
        val n = slug(name)
        val move = if (oldName != null && oldName != n) "Remove the folder $DIR/$oldName, then " else ""
        val ask = buildString {
            append(move).append("write the file $DIR/$n/SKILL.md with exactly the text between the markers and nothing else. ")
            append("Create the folder if it does not exist and keep that folder name. Reply in one line when it is written.\n\n")
            append("<<<SKILL\n").append(render(n, description, body)).append("SKILL\n")
        }
        Graph.chats.send(Graph.chats.createSession(CHAT_DIR).id, ask)
        changed()
        return n
    }

    /** Delete a skill: the agent removes its folder, files and all. */
    suspend fun delete(name: String) {
        // The name comes from the UI, but a slash would point at another folder on the server.
        if (name.isBlank() || name.contains('/') || name.contains('\\') || name.contains("..")) return
        Graph.chats.send(Graph.chats.createSession(CHAT_DIR).id, "Delete the skill folder $DIR/$name with everything in it, then reply in one line.")
        changed()
    }

    /**
     * Import from files the picker returned. A single .md becomes a skill; an archive (.zip, .skill:
     * a Claude or ChatGPT export) is handed to the agent in a chat, which unpacks it itself, because
     * common code has no `ZipInputStream`. Returns the names of the skills asked for.
     */
    suspend fun import(files: List<PickedFile>): List<String> {
        val added = ArrayList<String>()
        for (f in files) {
            val zip = f.bytes.size > 4 && f.bytes[0] == 'P'.code.toByte() && f.bytes[1] == 'K'.code.toByte()
            if (zip) { unpackInChat(f); continue }
            val text = f.bytes.decodeToString()
            val (name, desc, body) = parse(text)
            if (body.isBlank()) throw IllegalArgumentException("The file is empty")
            val fallback = f.name.substringBeforeLast('.')
            val n = slug(name ?: fallback.takeUnless { it.equals("SKILL", true) } ?: "imported-skill")
            val description = desc ?: body.lineSequence().firstOrNull { it.isNotBlank() }?.trim('#', ' ').orEmpty()
            added += save(n, description, body)
        }
        changed()
        return added
    }

    /** An archive goes to the agent with the bytes attached; it writes the folders and the files. */
    private suspend fun unpackInChat(f: PickedFile) {
        if (f.bytes.size > 4_000_000) throw IllegalArgumentException("The archive is too big to send from the phone")
        val mime = f.mime.ifBlank { "application/zip" }
        val ask = "Unpack the archive attached into $DIR: every folder that has a SKILL.md is a skill. " +
            "Keep the folder names, and skip anything outside $DIR. Reply in one line when it is done."
        Graph.chats.send(
            Graph.chats.createSession(CHAT_DIR).id,
            ask,
            listOf(LocalFile(mime, f.name, "data:$mime;base64," + base64Bytes(f.bytes))),
        )
    }

    /**
     * Instructions from ChatGPT (a custom GPT, "Custom instructions", a project) or from a Claude project, as a skill.
     */
    suspend fun fromInstructions(name: String, description: String, instructions: String): String =
        save(name, description.ifBlank { "Use when the user asks for ${name.replace('-', ' ')}." }, instructions)

    /** The server reads skills when an instance starts: reload the list and the "/" menu. */
    private fun changed() {
        Graph.scope.launch {
            // leave a busy agent alone: skills are picked up on the next server start
            if (Graph.chats.statuses.value.values.none { it == "busy" }) runCatching { Graph.api.disposeAll() }
            runCatching { Graph.catalog.refresh() }
        }
    }
}
