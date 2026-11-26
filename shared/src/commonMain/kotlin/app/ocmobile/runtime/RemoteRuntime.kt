package app.ocmobile.runtime

import app.ocmobile.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The server the app talks to, seen through the [RuntimeState] the screens already know.
 *
 * Android runs OpenCode on the phone, so `RuntimeController` installs, starts and stops it.
 * iOS only ever connects to the computer: there is nothing to install and nothing to start,
 * so every action here is a no-op and the state follows the event stream — [RuntimeState.Running]
 * while the server answers, [RuntimeState.Stopped] while it does not. Screens that offered
 * "Start"/"Stop"/"Reinstall" therefore render the same shape and do nothing harmful.
 */
class RemoteRuntime {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Starting)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    /** There is no local process whose output could be shown. */
    val log: StateFlow<List<String>> = MutableStateFlow<List<String>>(emptyList()).asStateFlow()

    fun start() {
        scope.launch {
            Graph.events.connected.collect { up -> _state.value = if (up) running() else RuntimeState.Stopped }
        }
        scope.launch {
            // the version comes from /global/health; it may lag the socket, so it is asked again
            val version = runCatching { Graph.api.healthVersion() }.getOrNull()
            if (Graph.events.connected.value && version != null) _state.value = RuntimeState.Running(version)
        }
    }

    private suspend fun running(): RuntimeState {
        val version = runCatching { Graph.api.healthVersion() }.getOrNull()
        return RuntimeState.Running(version.orEmpty())
    }

    /** Nothing to install on this device. */
    suspend fun install() { }

    fun startServer() { }

    fun stopServer() { }

    fun resetRuntime() { }

    /** Developer tools are installed on the computer, not here. */
    suspend fun installDevTools(onLine: (String) -> Unit): Int = 0
}
