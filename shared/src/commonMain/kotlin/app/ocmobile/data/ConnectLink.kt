package app.ocmobile.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.Graph
import app.ocmobile.ui.normalizeServerUrl

/**
 * Connecting to a computer by QR code. The code holds a link
 * `cl0udly://connect?n=<name>&u=<server address>&p=<password>`, shown by `tools/connect-qr.sh`
 * (and later by the desktop app). Scanned in the app or opened as a link:
 * first we ask "connect?", and only then add the device.
 *
 * Android parsed the link with `android.net.Uri`; iOS has no such class, so the query string is
 * read by hand. iOS has one server, so accepting a link overwrites the saved connection.
 */
object ConnectLink {
    /** [session]: the chat open on the computer when the code was shown; it opens right after connecting. */
    data class Offer(val name: String, val url: String, val password: String, val session: String? = null)

    /** Waits for confirmation in the dialog (Root). */
    var pending by mutableStateOf<Offer?>(null)

    /** A link or text from a QR code; null means it isn't a connect code. */
    fun parse(raw: String): Offer? {
        val text = raw.trim()
        val schemeEnd = text.indexOf("://")
        if (schemeEnd <= 0) return null
        if (!text.substring(0, schemeEnd).equals("cl0udly", true)) return null
        // a link may carry a fragment after the query; the query ends there
        val rest = text.substringAfter("://").substringBefore('#')
        val host = rest.substringBefore('?').substringBefore('/')
        if (!host.equals("connect", true)) return null
        if ('?' !in rest) return null
        val params = queryParams(rest.substringAfter('?'))
        val url = params["u"]?.takeIf { it.isNotBlank() } ?: return null
        return Offer(
            name = params["n"].orEmpty(),
            url = normalizeServerUrl(url),
            password = params["p"].orEmpty(),
            session = params["s"]?.takeIf { it.isNotBlank() },
        )
    }

    /** A link or text from a QR code; false means it isn't a connect code. */
    fun offer(raw: String): Boolean {
        val o = parse(raw) ?: return false
        pending = o
        return true
    }

    /** Checks the server, saves it as the connection and opens the chat the computer had open. */
    suspend fun accept(o: Offer): Devices.Device {
        Graph.api.probe(o.url, o.password)
        Graph.server.url = o.url
        Graph.server.password = o.password
        o.session?.takeIf { it.isNotBlank() }?.let { Graph.chats.openRequest.value = it }
        // iOS keeps no device list: the saved server is the device, reported with a fixed id.
        return Devices.Device(REMOTE_ID, o.url, o.password)
    }

    /** Splits `n=a&u=b` and percent-decodes both sides; the last value of a repeated key wins. */
    private fun queryParams(query: String): Map<String, String> {
        val out = HashMap<String, String>()
        for (pair in query.split('&')) {
            if (pair.isEmpty()) continue
            val key = decodeURLPart(pair.substringBefore('='))
            if (key.isEmpty()) continue
            val value = if ('=' in pair) decodeURLPart(pair.substringAfter('=')) else ""
            out[key] = value
        }
        return out
    }

    /** Percent-decoding for the query: `%XX` bytes (UTF-8) and `+` for a space, as `Uri` did on Android. */
    private fun decodeURLPart(raw: String): String {
        if ('%' !in raw && '+' !in raw) return raw
        val bytes = ArrayList<Byte>(raw.length)
        val plain = StringBuilder()
        fun flushPlain() {
            if (plain.isEmpty()) return
            bytes.addAll(plain.toString().encodeToByteArray().toList())
            plain.clear()
        }
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '+') {
                plain.append(' ')
                i++
            } else if (ch == '%' && i + 2 < raw.length) {
                val high = raw[i + 1].digitToIntOrNull(16)
                val low = raw[i + 2].digitToIntOrNull(16)
                if (high != null && low != null) {
                    flushPlain()
                    bytes += (high * 16 + low).toByte()
                    i += 3
                } else {
                    // a stray percent sign is a literal one, not a broken escape
                    plain.append(ch)
                    i++
                }
            } else {
                plain.append(ch)
                i++
            }
        }
        flushPlain()
        return bytes.toByteArray().decodeToString()
    }

    private const val REMOTE_ID = "remote"
}
