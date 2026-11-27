package app.ocmobile.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.I18n
import app.ocmobile.data.LiveNotify
import app.ocmobile.data.Prefs
import app.ocmobile.platform.PlatformLifecycle
import app.ocmobile.platform.PlatformNotify
import app.ocmobile.platform.SettingsStore
import app.ocmobile.platform.appLockAvailable
import app.ocmobile.platform.authenticateAppLock
import app.ocmobile.platform.nowMillis
import app.ocmobile.ui.OcRoot
import app.ocmobile.ui.ServerConnectScreen
import app.ocmobile.ui.ServerSettings
import app.ocmobile.ui.components.PixelCloud
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Txt
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

/** When the app last went to the background, to tell a real return apart from the observer's first callback. */
private var wentBackgroundAt = 0L

/**
 * The lock screen hands its "came back to the foreground" event here. The platform observer is
 * registered once for the process lifetime, outside any composition, so it goes through this hook
 * to reach the gate's state. Set while the gate is composed, cleared when it leaves.
 */
private var onForegroundAfterBackground: (() -> Unit)? = null

@Composable
fun App(store: SettingsStore) {
    val settings = remember(store) { ServerSettings(store) }
    val prefs = remember(store) { Prefs(store) }
    // the lock gate: when Face ID lock is on, the app starts locked and asks to unlock
    var locked by remember { mutableStateOf(prefs.appLock && appLockAvailable) }
    DisposableEffect(Unit) {
        onForegroundAfterBackground = { if (prefs.appLock && appLockAvailable) locked = true }
        onDispose { onForegroundAfterBackground = null }
    }
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
                // the lock gate only cares about real background/foreground transitions: the
                // observer's first "state right now" callback has no background before it
                if (!foreground) wentBackgroundAt = nowMillis()
                else if (wentBackgroundAt > 0L) {
                    wentBackgroundAt = 0L
                    onForegroundAfterBackground?.invoke()
                }
            }
            LiveNotify.start()
            PlatformNotify.requestPermission()
        }
    }
    OcTheme(prefs) {
        Surface(modifier = Modifier.fillMaxSize(), color = Oc.c.bg) {
            if (locked) LockScreen(onUnlock = { authenticateAppLock("Unlock cl0udly") { ok -> if (ok) locked = false } })
            else if (settings.configured) OcRoot() else ServerConnectScreen(settings)
        }
    }
}

/** Shown while the app is locked: the mascot, and one button that runs the system unlock. */
@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    val c = Oc.c
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // the mascot reads the prefs, which are not up yet on a first launch with nothing configured
        if (Graph.isPrefsReady()) PixelCloud(Modifier.size(width = 150.dp, height = 102.dp), interactive = false)
        Spacer(Modifier.height(20.dp))
        Txt("Locked", size = 22.sp, weight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        SmallButton("Unlock with Face ID", onUnlock, filled = true)
    }
}
