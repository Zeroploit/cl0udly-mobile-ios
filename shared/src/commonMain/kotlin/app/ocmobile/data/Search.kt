package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.api.Session

/**
 * Search through chat text and memory.
 *
 * Android opened the OpenCode SQLite database on the phone and matched message parts there. On iOS
 * the conversations live on the computer, so [chats] asks the server instead: `GET /session?search=`
 * finds the sessions whose text matches, and each of those sessions is then read back to cut a
 * snippet around the match. That is a handful of requests (one per session, at most [SESSIONS] of
 * them, each capped at [MSG_LIMIT] messages), so callers should debounce typing and only run it for
 * a query of two characters or more. [memory] is local, because the shelves are mirrored here.
 */
object Search {
    class Hit(val sessionId: String, val title: String, val snippet: String, val user: Boolean, val time: Long)
    class MemoryHit(val shelf: String, val line: String)

    /** Sessions read per search: the server's own search route already narrows the list. */
    private const val SESSIONS = 60

    /** Messages read per session: enough to find the match, not the whole history. */
    private const val MSG_LIMIT = 40

    /** At most three matches per chat, as Android did, so one long chat does not fill the list. */
    private const val PER_CHAT = 3

    /**
     * Message-text search on the server, at most [limit] hits. Falls back to listing sessions and
     * filtering by title and text when the server is too old to know the search parameter; never throws.
     */
    suspend fun chats(q: String, limit: Int = 60): List<Hit> {
        if (q.length < 2) return emptyList()
        return runCatching {
            val found = runCatching { Graph.api.listSessions(limit = SESSIONS, search = q) }.getOrDefault(emptyList())
            if (found.isNotEmpty()) scan(found.take(SESSIONS), q, limit, titles = false)
            else scan(Graph.api.listSessions(limit = 200).take(SESSIONS), q, limit, titles = true)
        }.getOrDefault(emptyList())
    }

    /** Facts on the shelves that contain [q], at most 30. */
    fun memory(q: String): List<MemoryHit> {
        if (q.length < 2) return emptyList()
        return runCatching {
            Memory.shelves()
                .flatMap { f -> Memory.facts(f).filter { it.contains(q, true) }.map { MemoryHit(f.nameWithoutExtension, it.trim().removePrefix("- ")) } }
                .take(30)
        }.getOrDefault(emptyList())
    }

    /**
     * Reads [sessions] and keeps the text parts that contain [q]. With [titles] on (the fallback,
     * where nothing narrowed the list) a matching title is a hit too, with the title as its snippet.
     */
    private suspend fun scan(sessions: List<Session>, q: String, limit: Int, titles: Boolean): List<Hit> {
        val out = ArrayList<Hit>()
        for (s in sessions) {
            if (out.size >= limit) break
            val titleHit = titles && s.title.contains(q, true)
            if (titleHit) out += Hit(s.id, s.title, s.title, false, s.time.updated)
            var here = if (titleHit) 1 else 0
            val msgs = runCatching { Graph.api.messages(s.id, s.directory.ifBlank { null }, limit = MSG_LIMIT) }.getOrDefault(emptyList())
            for (m in msgs) {
                if (here >= PER_CHAT || out.size >= limit) break
                // one hit per message: the first text part that matches is the one the card shows
                val part = m.parts.firstOrNull { it.type == "text" && it.text.contains(q, true) } ?: continue
                val at = part.text.indexOf(q, ignoreCase = true)
                out += Hit(s.id, s.title, snippet(part.text, at, q.length), m.info.isUser, s.time.updated)
                here++
            }
        }
        return out
    }

    /** A piece of text around the match, on one line (as Android's `snippet`: 50 before, 90 after). */
    private fun snippet(text: String, at: Int, len: Int): String {
        val from = (at - 50).coerceAtLeast(0)
        val to = (at + len + 90).coerceAtMost(text.length)
        return (if (from > 0) "…" else "") + text.substring(from, to).replace(Regex("\\s+"), " ").trim() + if (to < text.length) "…" else ""
    }
}
