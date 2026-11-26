package app.ocmobile.data

/**
 * Search through chat text and memory.
 *
 * Android opened the OpenCode SQLite database on the phone (`~/.local/share/opencode/opencode.db`)
 * and matched message parts there. On iOS the conversations live on the computer and the app has
 * neither a local database nor a search route in the 1.x API, so [chats] returns nothing: the
 * chat list itself still matches titles and folders, and the index of message text stays on the
 * computer. [memory] works, because the shelves are mirrored here by [Memory].
 */
object Search {
    class Hit(val sessionId: String, val title: String, val snippet: String, val user: Boolean, val time: Long)
    class MemoryHit(val shelf: String, val line: String)

    /** Message-text search: the index is on the computer, so a client has nothing to search here. */
    suspend fun chats(q: String, limit: Int = 60): List<Hit> = emptyList()

    /** Facts on the shelves that contain [q], at most 30. */
    fun memory(q: String): List<MemoryHit> {
        if (q.length < 2) return emptyList()
        return runCatching {
            Memory.shelves()
                .flatMap { f -> Memory.facts(f).filter { it.contains(q, true) }.map { MemoryHit(f.nameWithoutExtension, it.trim().removePrefix("- ")) } }
                .take(30)
        }.getOrDefault(emptyList())
    }
}
