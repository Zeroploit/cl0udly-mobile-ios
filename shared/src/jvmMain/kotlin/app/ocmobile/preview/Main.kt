package app.ocmobile.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.ocmobile.Graph
import app.ocmobile.data.Prefs
import app.ocmobile.platform.MemorySettingsStore
import app.ocmobile.ui.NavMemory
import app.ocmobile.ui.OcRoot
import app.ocmobile.ui.ServerConnectScreen
import app.ocmobile.ui.ServerSettings
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.OcTheme

/**
 * Desktop preview of the shared UI.
 *
 * This is not a shipping target. It exists so the Compose code that iOS runs can
 * be built and looked at on Windows, in a window the size of an iPhone, without
 * a Mac and without a device. What you see here is the same composable tree
 * `MainViewController` hands to iOS.
 *
 * `preview.properties` (see [PreviewConfig]) can point it at a server and open a
 * chat straight away.
 */
fun main() = application {
    val preview = PreviewConfig.read()
    val server = preview["server"].orEmpty()
    val session = preview["session"]?.takeIf { it.isNotBlank() }
    val store = MemorySettingsStore(
        buildMap {
            if (server.isNotBlank()) {
                put("server_url", server)
                put("server_password", preview["password"].orEmpty())
            }
            preview["edition"]?.let { put("edition", it) }
            preview["color_mode"]?.let { put("color_mode", it) }
        },
    )
    val settings = remember { ServerSettings(store) }
    val prefs = remember { Prefs(store) }
    val phone = rememberWindowState(size = DpSize(390.dp, 844.dp))
    // the shared screens read Graph as soon as they compose, so the shell waits for the wiring
    var wired by remember { mutableStateOf(false) }
    // wire the dependencies once the address is known, then jump into the chat to preview
    LaunchedEffect(settings.configured) {
        if (settings.configured && !Graph.isPrefsReady()) {
            Graph.init(prefs, settings)
            session?.let { id -> NavMemory.startFresh(); NavMemory.nav.openChatInstant(id) }
        }
        wired = Graph.isPrefsReady()
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "cl0udly · desktop preview",
        state = phone,
    ) {
        OcTheme(prefs) {
            Surface(modifier = Modifier.fillMaxSize(), color = Oc.c.bg) {
                if (wired) OcRoot() else ServerConnectScreen(settings)
            }
        }
    }
}
