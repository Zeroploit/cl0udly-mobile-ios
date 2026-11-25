package app.ocmobile.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.ocmobile.data.Prefs
import app.ocmobile.platform.SettingsStore
import app.ocmobile.ui.ConnectScreen
import app.ocmobile.ui.ConnectedApp
import app.ocmobile.ui.ServerSettings
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.OcTheme

/**
 * Entry point of the shared UI. iOS hosts it in a `ComposeUIViewController`;
 * the desktop preview runs it in a window.
 *
 * iOS cannot run the OpenCode server, so the app is a client: connect to the
 * computer, then read and write in its sessions. Everything below this
 * composable — the API, the SSE stream, the chat state, the theme and the
 * screens — is the same code on every platform.
 */
@Composable
fun App(store: SettingsStore) {
    val settings = remember(store) { ServerSettings(store) }
    val prefs = remember(store) { Prefs(store) }
    OcTheme(prefs) {
        Surface(modifier = Modifier.fillMaxSize(), color = Oc.c.bg) {
            if (settings.configured) ConnectedApp(settings, prefs) else ConnectScreen(settings)
        }
    }
}