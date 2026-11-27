package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.Session
import kotlin.concurrent.Volatile

/**
 * Global search across sessions, for the search bar over the chat list.
 *
 * Two layers, ranked apart: a session whose **title** mentions the query is the
 * kind of hit the user is usually after, so those come first; then sessions
 * whose **messages** mention it, newest first. Message text is searched on the
 * server when it can (`GET /session?search=`), which is one cheap request; when
 * the server is too old to know the parameter (or finds nothing), the recent
 * sessions are read back here and their text parts are scanned instead —
 * bounded, so a big history cannot turn one keystroke into hundreds of
 * requests.
 *
 * Like [Search] (the in-chat search), this never throws: a server that is
 * offline or answers with an error just means an empty result. Callers should
 * debounce typing and skip queries shorter than two characters — a single
 * letter matches half of everything.
 */
object SessionSearch {
    /** One row of results: the session to open, a line of context, and how much matched. */
    class SearchHit(
        val sessionId: String,
        val title: String,
        val snippet: String,
        val matchCount: Int,
        val timestamp: Long,
    )

    /** Results cap; also the number callers should debounce their queries against. */
    const val DEFAULT_LIMIT = 50

    /**
     * The API seam: [Graph.api] in the app, a mock in tests. Set only from tests;
     * they restore it afterwards.
     */
    @Volatile
    internal var apiOverride: OpenCodeApi? = null
    private val api get() = apiOverride ?: Graph.api

    /** Sessions listed for the title pass. */
    private const val SESSION_SCAN = 300

    /** How many sessions the server's own search may name. */
    private const val SERVER_SEARCH = 60

    /** Fallback: how many recent sessions are read back when the server cannot search. */
    private const val FALLBACK_SCAN = 40

    /** Messages read per session: enough to find a recent match, not the whole history. */
    private const val MSG_LIMIT = 40

    /** Snippet budget in characters (the match included). */
    private const val SNIPPET = 120

    /** Title matches first, then message matches, both newest first, at most [limit]. */
    suspend fun sessions(query: String, limit: Int = DEFAULT_LIMIT): List<SearchHit> {
        val q = query.trim()
        if (q.length < 2) return emptyList()
        return runCatching { search(q, limit) }.getOrDefault(emptyList())
    }

    private suspend fun search(q: String, limit: Int): List<SearchHit> {
        val all = runCatching { api.listSessions(limit = SESSION_SCAN) }.getOrDefault(emptyList())
        val byTitle = ArrayList<SearchHit>()
        val byText = ArrayList<SearchHit>()
        val seen = HashSet<String>()

        // The title pass is local: the list is already here, no per-session request.
        for (s in all.sortedByDescending { it.time.updated }) {
            val n = occurrences(s.title, q)
            if (n > 0) {
                byTitle += SearchHit(s.id, s.title, s.title, n, s.time.updated)
                seen += s.id
            }
        }
        if (byTitle.size >= limit) return byTitle.take(limit)

        // The text pass: the server narrows the sessions when it can, otherwise
        // the most recent ones are read here. Sessions already found by title
        // are not read again — their card is the title already.
        val found = runCatching { api.listSessions(limit = SERVER_SEARCH, search = q) }.getOrDefault(emptyList())
        val candidates = if (found.isNotEmpty()) found
        else all.sortedByDescending { it.time.updated }.take(FALLBACK_SCAN)
        for (s in candidates) {
            if (byTitle.size + byText.size >= limit) break
            if (s.id in seen) continue
            val hit = messageHit(s, q) ?: continue
            byText += hit
            seen += s.id
        }
        return (byTitle + byText.sortedByDescending { it.timestamp }).take(limit)
    }

    /** The session's recent messages scanned for [q]: one hit per session, or null. */
    private suspend fun messageHit(s: Session, q: String): SearchHit? {
        val msgs = runCatching { api.messages(s.id, s.directory.ifBlank { null }, limit = MSG_LIMIT) }
            .getOrDefault(emptyList())
        var count = 0
        var snippet: String? = null
        for (m in msgs) for (part in m.parts) {
            if (part.type != "text") continue
            val text = part.text
            if (text.isEmpty()) continue
            var at = text.indexOf(q, ignoreCase = true)
            if (at < 0) continue
            if (snippet == null) snippet = snippet(text, at, q.length)
            while (at >= 0) {
                count++
                at = text.indexOf(q, at + q.length, ignoreCase = true)
            }
        }
        return snippet?.let { SearchHit(s.id, s.title, it, count, s.time.updated) }
    }

    /** Case-insensitive, non-overlapping occurrences of [q] in [text]. */
    private fun occurrences(text: String, q: String): Int {
        if (q.isEmpty() || text.isEmpty()) return 0
        var n = 0
        var at = text.indexOf(q, ignoreCase = true)
        while (at >= 0) {
            n++
            at = text.indexOf(q, at + q.length, ignoreCase = true)
        }
        return n
    }

    /** A piece of text around the match, on one line (about [SNIPPET] chars: a third before, the rest after). */
    private fun snippet(text: String, at: Int, len: Int): String {
        val from = (at - SNIPPET / 3).coerceAtLeast(0)
        val to = (at + len + SNIPPET - SNIPPET / 3).coerceAtMost(text.length)
        return (if (from > 0) "…" else "") + text.substring(from, to).replace(Regex("\\s+"), " ").trim() +
            if (to < text.length) "…" else ""
    }
}
