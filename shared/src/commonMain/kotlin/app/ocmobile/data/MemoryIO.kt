package app.ocmobile.data

import app.ocmobile.ui.components.MemGraph

/**
 * Memory in and out: a memory export (a folder of .md notes, a zip unpacked by the caller) comes in
 * as shelves, and the map of memory the agent gets when memory is too big to send whole: each shelf
 * with what it holds and which shelves it is linked to (the same links the graph draws), so it opens
 * only what it needs.
 *
 * Ported from the Android `MemoryIO.kt`. The Android version worked on `java.io.File` and on
 * `ZipInputStream`/`ZipOutputStream`; a client's memory lives on the computer and the 1.x API has no
 * zip route, so the shelves travel as a name → text map, which is what a zip held anyway. The caller
 * unpacks an upload before it gets here.
 */
object MemoryIO {
    /** Shelves that always go whole: who the user is and how they like things. */
    val core = setOf("about-me", "profile", "preferences", "me")

    /** A shelf's one-line description: from its front matter, or its first plain line. */
    fun describe(f: MemFile): String = describe(f.name, f.text)

    fun describe(name: String, text: String): String {
        val lines = text.lines()
        lines.firstOrNull { it.startsWith("description:") }?.let { return it.substringAfter(':').trim().trim('"') }
        var inFront = lines.firstOrNull()?.trim() == "---"
        lines.drop(if (inFront) 1 else 0).forEach { l ->
            if (inFront) { if (l.trim() == "---") inFront = false; return@forEach }
            val t = l.trim().removePrefix("#").trim()
            if (t.isNotEmpty() && !t.startsWith("- ")) return t.take(120)
        }
        return ""
    }

    /**
     * Every .md in [files] lands in [dest] as a shelf (folders flattened); facts merge into shelves
     * that exist. [files] is name → text, the flattened contents of an imported export.
     */
    fun import(files: List<Pair<String, String>>, dest: MutableMap<String, String>): Int {
        var n = 0
        val added = ArrayList<MemFile>()
        files.forEach { (from, text) ->
            val name = from.substringAfterLast('/').substringAfterLast('\\').lowercase().replace(Regex("[^a-z0-9а-яё._-]+"), "-")
            if (!name.endsWith(".md") || name == "MEMORY.md") return@forEach
            val to = MemFile(dest, name)
            if (!to.exists()) { to.writeText(text); added += to }
            else {
                val have = to.readLines().map { it.trim() }.toSet()
                val add = text.lines().filter { it.trim().startsWith("- ") && it.trim() !in have }
                if (add.isNotEmpty()) to.appendText((if (to.readText().endsWith("\n")) "" else "\n") + add.joinToString("\n") + "\n")
            }
            n++
        }
        addToIndex(dest, added)
        return n
    }

    /** New shelves get a line in MEMORY.md; the lines already there (maybe written by hand) stay as they are. */
    fun addToIndex(dir: MutableMap<String, String>, shelves: List<MemFile>) {
        val idx = MemFile(dir, "MEMORY.md")
        if (!idx.exists()) return rebuildIndex(dir)
        val t = idx.readText()
        val lines = shelves.filter { !t.contains("(${it.name})") }.map { f ->
            "- [${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() }}](${f.name}) — ${describe(f)}"
        }
        if (lines.isNotEmpty()) idx.writeText(t.replace("Nothing saved yet.\n", "").trimEnd() + "\n" + lines.joinToString("\n") + "\n")
    }

    fun rebuildIndex(dir: MutableMap<String, String>) {
        val shelves = dir.keys.filter { it.endsWith(".md") && it != "MEMORY.md" }.sorted()
        MemFile(dir, "MEMORY.md").writeText("# Memory index\n\n" + shelves.joinToString("\n") { n ->
            "- [${n.substringBeforeLast('.').replace('-', ' ').replaceFirstChar { it.uppercase() }}]($n) — ${describe(n, dir[n].orEmpty())}"
        } + "\n")
    }

    /**
     * The map: the core shelves whole, every other shelf as one line with its description, how many
     * facts it has and the shelves it's linked to. [path] is where the agent opens a shelf.
     */
    fun map(dir: Map<String, String>, path: String): String {
        val g = MemGraph.read(dir, null, mutableSetOf(), true)
        val shelfOf = HashMap<Int, Int>()
        var cur = -1
        g.nodes.forEachIndexed { i, n -> if (n.kind == 1) cur = i else if (n.kind == 2) shelfOf[i] = cur }
        val related = HashMap<Int, MutableSet<Int>>()
        g.links.forEach { (a, b) ->
            val sa = if (g.nodes[a].kind == 1) a else shelfOf[a]; val sb = if (g.nodes[b].kind == 1) b else shelfOf[b]
            if (sa != null && sb != null && sa != sb && g.nodes[a].kind != 0 && g.nodes[b].kind != 0) {
                related.getOrPut(sa) { mutableSetOf() } += sb; related.getOrPut(sb) { mutableSetOf() } += sa
            }
        }
        return buildString {
            append("## Map of your memory\nYour memory is large, so here is its map (the same graph the user sees): core shelves in full, the rest as one line each. ")
            append("When a topic comes up, open that shelf ($path/<file>) and follow its linked shelves to find connected facts; don't open shelves you don't need.\n")
            g.nodes.forEachIndexed { i, n ->
                if (n.kind != 1) return@forEachIndexed
                val name = n.file ?: return@forEachIndexed
                if (name.substringBeforeLast('.') in core) { append("\n### $name\n").append(dir[name].orEmpty().trim()).append("\n"); return@forEachIndexed }
                val facts = g.neighbours(i).count { g.nodes[it].kind == 2 && shelfOf[it] == i }
                val links = related[i].orEmpty().mapNotNull { g.nodes[it].file?.substringBeforeLast('.') }.sorted().take(6)
                append("\n- $name: ${describe(name, dir[name].orEmpty()).ifBlank { "notes" }} ($facts facts)")
                if (links.isNotEmpty()) append(" → ").append(links.joinToString(", "))
            }
        }.trim()
    }
}
