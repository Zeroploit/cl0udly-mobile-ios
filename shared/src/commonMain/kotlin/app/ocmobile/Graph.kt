package app.ocmobile

import app.ocmobile.api.EventHub
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.data.Catalog
import app.ocmobile.data.Chats
import app.ocmobile.data.Devices
import app.ocmobile.data.Prefs
import app.ocmobile.data.UsageTracker
import app.ocmobile.net.ServerEndpoint
import app.ocmobile.ui.ServerSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf

/**
 * Single place where dependencies are wired up: the app is small, no DI framework needed.
 *
 * Ported from the Android `OcApp.kt`. One difference: iOS cannot run the OpenCode server,
 * so there is no RuntimeController/UsageTracker/OcUpdater here — the phone is always a
 * client of the computer running `opencode serve`, and `Graph.api` points at whatever
 * address the user saved in the connect screen.
 */
object Graph {
    /** Dispatchers.Main: the UI thread on Android, iOS and the desktop preview alike. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var prefs: Prefs
        private set
    lateinit var server: ServerSettings
        private set
    lateinit var api: OpenCodeApi
        private set
    lateinit var events: EventHub
        private set
    lateinit var chats: Chats
        private set
    lateinit var catalog: Catalog
        private set
    lateinit var usage: UsageTracker
        private set

    /** iOS is remote-only: no local server, no device switching. */
    val devices = Devices()

    /** False in UI previews, where the app never started. */
    fun isPrefsReady() = ::prefs.isInitialized

    fun init(prefs: Prefs, server: ServerSettings) {
        this.prefs = prefs
        this.server = server
        api = OpenCodeApi(endpoint = { ServerEndpoint(server.url, server.password) })
        events = EventHub(api)
        chats = Chats(api, events, prefs, scope)
        catalog = Catalog(api, prefs, events)
        usage = UsageTracker(api, prefs, events)
        // The stream targets the one server there is: the computer.
        events.start(scope, flowOf("remote"))
        chats.start()
        catalog.start()
        usage.start()
    }
}
