package app.ocmobile.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonObject

/**
 * One global SSE connection to `/global/event`: events of all projects and
 * sessions.
 *
 * It used to decide by itself whether to talk to the local server or to a
 * selected remote device, which meant reaching into [app.ocmobile.Graph] and
 * the Android runtime. Now [start] receives that decision as a flow of targets:
 * an empty string means "not connected", anything else is just a label used to
 * restart the stream when it changes. Android builds it from the running
 * runtime and the chosen device; iOS, which has no local server at all, passes a
 * constant.
 */
class EventHub(private val api: OpenCodeApi) {
    data class Event(val directory: String?, val type: String, val props: JsonObject)

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 1024)
    val connected = MutableStateFlow(false)
    /** Grows on every (re)connect: subscribers must reload their state. */
    val generation = MutableStateFlow(0)

    /**
     * [target] must emit a different label every time the stream has to be
     * rebuilt, and `""` while there is nothing to connect to.
     */
    fun start(scope: CoroutineScope, target: Flow<String>) {
        scope.launch {
            target
                .distinctUntilChanged()
                .collectLatest { label ->
                    if (label.isNotEmpty()) connectLoop() else connected.value = false
                }
        }
    }

    private suspend fun connectLoop() {
        var backoff = 500L
        while (currentCoroutineContext().isActive) {
            try {
                // Default, not IO: Dispatchers.IO does not exist on Kotlin/Native.
                withContext(Dispatchers.Default) { stream { backoff = 500L } }
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
