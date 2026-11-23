package app.ocmobile.api

import app.ocmobile.Graph
import app.ocmobile.runtime.RuntimeController
import app.ocmobile.runtime.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/** One global SSE connection to `/global/event`: events of all projects and sessions. */
class EventHub(private val api: OpenCodeApi, private val runtime: RuntimeController) {
    data class Event(val directory: String?, val type: String, val props: JsonObject)

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 1024)
    val connected = MutableStateFlow(false)
    /** Grows on every (re)connect: subscribers must reload their state. */
    val generation = MutableStateFlow(0)

    fun start(scope: CoroutineScope) {
        scope.launch {
            // Connect to the local server when it runs, or to the selected remote device.
            combine(runtime.state, Graph.devices.activeId) { s, d -> if (d.isNotEmpty()) "remote:$d" else if (s is RuntimeState.Running) "local" else "" }
                .distinctUntilChanged()
                .collectLatest { target ->
                    if (target.isNotEmpty()) connectLoop() else connected.value = false
                }
        }
    }

    private suspend fun connectLoop() {
        var backoff = 500L
        while (currentCoroutineContext().isActive) {
            try {
                withContext(Dispatchers.IO) { stream { backoff = 500L } }
            } catch (_: Exception) { }
            connected.value = false
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(8000)
        }
    }

    private suspend fun stream(onOpen: () -> Unit) {
        // kotlinx.coroutines cancels the Ktor call with the coroutine, so there
        // is no socket to cancel by hand any more.
        val data = StringBuilder()
        api.streamLines(
            path = "/global/event",
            onOpen = {
                onOpen()
                connected.value = true
                generation.value++
            },
            onLine = { line ->
                if (line.isEmpty()) {
                    if (data.isNotEmpty()) { dispatch(data.toString()); data.setLength(0) }
                } else if (line.startsWith("data:")) {
                    data.append(line.removePrefix("data:").removePrefix(" "))
                }
            },
        )
    }

    private fun dispatch(json: String) {
        val o = runCatching { OcJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return
        val payload = o.obj("payload") ?: o
        val type = payload.str("type") ?: return
        events.tryEmit(Event(o.str("directory"), type, payload.obj("properties") ?: JsonObject(emptyMap())))
    }
}
