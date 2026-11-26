package app.ocmobile.platform

/** The custom scheme the app's sign-in redirects come back on; it is declared in Info.plist. */
const val AUTH_CALLBACK_SCHEME = "cl0udly"

/**
 * Opens a web sign-in in the system's auth sheet and hands back the callback URL.
 * [onResult] gets the full URL, or null if the person closed the sheet.
 */
expect fun openAuthSession(url: String, callbackScheme: String, onResult: (String?) -> Unit)

/**
 * Dismisses the sheet [openAuthSession] opened, for when the key arrived another way (paste, or the
 * clipboard poll) and the sign-in is no longer needed. Harmless when nothing is open.
 */
expect fun closeAuthSession()

/**
 * The key an auth callback carries, if there is one: `key`, `api_key` or `token` from the query or
 * the fragment, else a value matching [pattern] anywhere in the URL.
 *
 * Only values that match [pattern] are taken. A redirect also carries OAuth tokens and codes, and
 * saving one of those as an API key would look like a success while every request failed.
 */
internal fun keyFromCallback(url: String, pattern: Regex): String? {
    val query = url.substringAfter('?', "").substringBefore('#')
    val fragment = url.substringAfter('#', "")
    for (part in (query + "&" + fragment).split('&')) {
        val name = part.substringBefore('=', "").trim()
        if (!name.looksLikeKeyName()) continue
        val value = percentDecode(part.substringAfter('=', "")).trim()
        if (value.isEmpty()) continue
        pattern.find(value)?.let { return it.value }
    }
    return pattern.find(percentDecode(url))?.value
}

private fun String.looksLikeKeyName(): Boolean =
    equals("key", ignoreCase = true) || equals("api_key", ignoreCase = true) ||
        equals("apikey", ignoreCase = true) || equals("token", ignoreCase = true) ||
        equals("access_token", ignoreCase = true)

/** Query values arrive percent-encoded; a key has no `+` or `%`, but a wrapper might add them. */
private fun percentDecode(text: String): String {
    if ('%' !in text && '+' !in text) return text
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val ch = text[i]
        if (ch == '+') {
            out.append(' '); i++
            continue
        }
        if (ch == '%' && i + 2 < text.length) {
            val hex = text.substring(i + 1, i + 3).toIntOrNull(16)
            if (hex != null) {
                out.append(hex.toChar()); i += 3
                continue
            }
        }
        out.append(ch); i++
    }
    return out.toString()
}
