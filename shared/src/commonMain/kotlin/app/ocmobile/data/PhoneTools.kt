package app.ocmobile.data

import app.ocmobile.Graph
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The "phone" tool set: what the agent can do on the device itself.
 *
 * Android ran a small HTTP server on 127.0.0.1 with an MCP endpoint, so the OpenCode server living
 * in the same phone could call back into the app: notifications, reminders, the alarm, the
 * clipboard, the camera, location, Downloads, links and previews.
 *
 * A client of a computer cannot do that: that server is on another machine and cannot reach this
 * device. So the catalog is here for the screen to list and explain, [register] only switches the
 * entry on or off through the server's config, and every tool that would need a callback reports
 * the limitation instead of pretending to run.
 */
object PhoneTools {
    const val NAME = "phone"

    /** One entry of the catalog: what the screen shows and what the agent would be offered. */
    class Tool(val name: String, val title: String, val desc: String)

    private fun schema(required: List<String> = emptyList(), vararg props: Pair<String, Pair<String, String>>) = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") { props.forEach { (k, v) -> putJsonObject(k) { put("type", v.first); put("description", v.second) } } }
        if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
    }

    /** The same list, in the same order and with the same words as the Android bridge. */
    val all: List<Tool> = listOf(
        Tool("notify", "Notification", "Show a notification on the phone right now."),
        Tool("remind", "Reminders", "Remind the user later with a notification. Give either in_minutes or at (24h local time HH:MM, today or tomorrow if it has passed)."),
        Tool("set_alarm", "Alarm clock", "Set an alarm in the phone's Clock app."),
        Tool("clipboard_read", "Read clipboard", "Read the text currently on the phone's clipboard."),
        Tool("clipboard_write", "Copy to clipboard", "Put text on the phone's clipboard so the user can paste it anywhere."),
        Tool("location", "Location", "Get the phone's current location (latitude, longitude, accuracy and, when available, the address)."),
        Tool("take_photo", "Camera", "Open the camera so the user takes a photo; returns the path of the saved JPEG, which you can then read."),
        Tool("save_to_downloads", "Downloads", "Copy a file from this environment into the phone's Downloads folder so the user can open or share it."),
        Tool("open_url", "Links", "Open a link on the phone (browser or the app that handles it)."),
        Tool("preview", "Preview", "Show a web page you built in the app's preview with live reload: give a local dev server URL (http://localhost:5173) or the absolute path of an .html file."),
        Tool("share", "Share", "Open the share sheet with text so the user can send it to any app (Telegram, mail…)."),
        Tool("device_status", "Phone status", "Battery, charging, network, local time and free storage of the phone."),
    )

    /** The tools the person left on, in the order of [all]. */
    fun enabled(): List<Tool> {
        val off = Graph.prefs.phoneToolsOff.split(',').filter { it.isNotBlank() }.toSet()
        return all.filter { it.name !in off }
    }

    /** The JSON schema of one tool, so the config the server reads stays the same shape as on Android. */
    fun schemaOf(name: String): JsonObject = when (name) {
        "notify" -> schema(listOf("text"), "title" to ("string" to "Short title"), "text" to ("string" to "The message"))
        "remind" -> schema(listOf("text"), "text" to ("string" to "What to remind about"), "in_minutes" to ("number" to "Minutes from now"), "at" to ("string" to "Local time HH:MM"))
        "set_alarm" -> schema(listOf("hour", "minute"), "hour" to ("integer" to "0–23"), "minute" to ("integer" to "0–59"), "label" to ("string" to "Optional label"))
        "clipboard_write" -> schema(listOf("text"), "text" to ("string" to "Text to copy"))
        "take_photo" -> schema(emptyList(), "save_to" to ("string" to "Folder to save into (default /root/outbox/photos)"))
        "save_to_downloads" -> schema(listOf("path"), "path" to ("string" to "Absolute path of the file"))
        "open_url" -> schema(listOf("url"), "url" to ("string" to "http(s), tel:, mailto:, geo: …"))
        "preview" -> schema(listOf("target"), "target" to ("string" to "URL or absolute path to an .html file"))
        "share" -> schema(listOf("text"), "text" to ("string" to "Text to share"))
        else -> schema()
    }

    /**
     * Turns the entry on or off in the server's global config, as Android's [register] did with the
     * config file. The tools themselves cannot be offered from a client (the server cannot call back
     * into this device), so the entry points at the phone's own bridge only when it exists.
     */
    suspend fun register() {
        if (Graph.devices.isRemote) return
        val on = Graph.prefs.phoneTools
        val block = buildJsonObject {
            put("type", "remote")
            put("url", "http://127.0.0.1:$PORT/mcp")
            put("enabled", on)
            putJsonObject("headers") { put("Authorization", "Bearer ${Graph.prefs.phoneToken}") }
        }
        runCatching { Graph.api.patchGlobalConfig(buildJsonObject { putJsonObject("mcp") { put(NAME, block) } }) }
        runCatching { if (on) Graph.api.mcpConnect(NAME) else Graph.api.mcpDisconnect(NAME) }
    }

    /** The port Android's bridge listened on; kept so the config shape matches. */
    private const val PORT = 4097
}
