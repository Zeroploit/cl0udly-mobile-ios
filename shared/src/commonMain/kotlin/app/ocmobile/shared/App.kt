package app.ocmobile.shared

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.ocmobile.Graph
import app.ocmobile.data.I18n
import app.ocmobile.data.LiveNotify
import app.ocmobile.data.Prefs
import app.ocmobile.platform.PlatformLifecycle
import app.ocmobile.platform.PlatformNotify
import app.ocmobile.platform.SettingsStore
import app.ocmobile.ui.OcRoot
import app.ocmobile.ui.ServerConnectScreen
import app.ocmobile.ui.ServerSettings
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.OcTheme

/**
 * Entry point of the shared UI. iOS hosts it in a `ComposeUIViewController`;
 * the desktop preview runs it in a window.
 *
 * iOS cannot run the OpenCode server, so the app is a client: connect to the
 * computer, then read and write in its sessions. Everything below this
 * composable — the API, the SSE stream, the chat state, the theme, the screens
 * and the navigation shell — is the same code on every platform.
 */
/**
 * Whether the process-wide subscriptions are up. A `remember` would tie them to the composition,
 * and the platform objects (notification delegate, lifecycle observers) live outside it.
 */
private var started = false

@Composable
fun App(store: SettingsStore) {
    val settings = remember(store) { ServerSettings(store) }
    val prefs = remember(store) { Prefs(store) }
    // wire the app's dependencies once: the Graph is the same shape as on Android
    LaunchedEffect(settings.configured) {
        if (settings.configured && !Graph.isPrefsReady()) Graph.init(prefs, settings)
    }
    LaunchedEffect(settings.configured) {
        if (!settings.configured || !Graph.isPrefsReady()) return@LaunchedEffect
        // the translation table is a bundled resource now: read it once, then repaint
        I18n.lang = prefs.language
        I18n.load()
        // Android did this in `OcApp.init` and the activity lifecycle; here it happens once,
        // when the graph is up, so a reconnect does not subscribe the event stream twice.
        if (!started) {
            started = true
            PlatformLifecycle.observe { foreground ->
                Graph.chats.foreground = foreground
                if (foreground) LiveNotify.onForeground() else LiveNotify.onBackground()
            }
            LiveNotify.start()
            PlatformNotify.requestPermission()
        }
    }
    OcTheme(prefs) {
        Surface(modifier = Modifier.fillMaxSize(), color = Oc.c.bg) {
            if (settings.configured) OcRoot() else ServerConnectScreen(settings)
        }
    }
}
