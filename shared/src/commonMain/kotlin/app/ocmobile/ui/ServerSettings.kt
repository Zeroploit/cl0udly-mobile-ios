package app.ocmobile.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.ocmobile.platform.SettingsStore

/**
 * The address and password of the OpenCode server this device talks to.
 *
 * On Android the app usually runs the server itself; on iOS there is no way to
 * run it, so the server is the one on your computer and these two values are
 * the whole connection.
 */
class ServerSettings(private val store: SettingsStore) {
    private var currentUrl by mutableStateOf(store.getString(KEY_URL, "").orEmpty())
    private var currentPassword by mutableStateOf(store.getString(KEY_PASSWORD, "").orEmpty())

    var url: String
        get() = currentUrl
        set(value) {
            currentUrl = value
            store.putString(KEY_URL, value)
        }

    var password: String
        get() = currentPassword
        set(value) {
            currentPassword = value
            store.putString(KEY_PASSWORD, value)
        }

    val configured: Boolean get() = url.isNotBlank()

    fun forget() {
        url = ""
        password = ""
    }

    private companion object {
        const val KEY_URL = "server_url"
        const val KEY_PASSWORD = "server_password"
    }
}

/**
 * What the user typed into an address field, as a URL the client can use:
 * "192.168.1.10:4096" is what people write, "http://192.168.1.10:4096" is what
 * the request needs, and a trailing slash would produce "//global/health".
 */
fun normalizeServerUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    return withScheme.trimEnd('/')
}
