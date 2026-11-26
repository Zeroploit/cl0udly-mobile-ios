package app.ocmobile.data

import app.ocmobile.Graph
import app.ocmobile.platform.nowMillis
import app.ocmobile.ui.screens.zoneOffsetMinutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Scheduled task: at the given time it creates a session in the project and sends the prompt to it. */
@Serializable
data class ScheduledTask(
    val id: String,
    val title: String,
    val prompt: String,
    val directory: String,
    val hour: Int,
    val minute: Int,
    val repeat: String,          // once | daily | weekly
    val enabled: Boolean = true,
    val lastRun: Long = 0,
)

/**
 * Scheduled tasks, ported from the Android `Scheduler.kt`.
 *
 * Android handed each task to `AlarmManager` and a `BroadcastReceiver` woke the app in the
 * background. iOS has neither: an app cannot ask the system to start it at a given hour, and
 * there is no `BroadcastReceiver` to wake. So a task only runs while the app is open — [start]
 * keeps a timer in the app itself and fires whatever came due. Nothing runs with the app
 * closed, which is a real limitation of the platform and not a bug here.
 */
object Scheduler {
    private val json = Json { ignoreUnknownKeys = true }
    val tasks = MutableStateFlow<List<ScheduledTask>>(emptyList())

    /** When each task is due next, by id. Rebuilt on load/upsert, so a task is never run twice. */
    private val due = mutableMapOf<String, Long>()

    private var started = false

    /** How late a task may still run after its time: past this, it waits for the next occurrence. */
    private const val CATCH_UP_MS = 12L * 60 * 60 * 1000

    /** Reads the saved tasks and arms them in memory (Android armed the system alarms here). */
    fun load() {
        if (!Graph.isPrefsReady()) return
        tasks.value = runCatching {
            json.decodeFromString(ListSerializer(ScheduledTask.serializer()), Graph.prefs.scheduledJson)
        }.getOrDefault(emptyList())
        due.clear()
        tasks.value.filter { it.enabled }.forEach { arm(it, catchUp = true) }
    }

    private fun save(l: List<ScheduledTask>) {
        Graph.prefs.scheduledJson = json.encodeToString(ListSerializer(ScheduledTask.serializer()), l)
        tasks.value = l
    }

    fun upsert(t: ScheduledTask) {
        if (!Graph.isPrefsReady()) return
        save(tasks.value.filterNot { it.id == t.id } + t)
        // Android armed or cancelled the alarm: here the in-memory timer takes that place
        if (t.enabled) arm(t, catchUp = false) else due.remove(t.id)
    }

    fun remove(id: String) {
        if (!Graph.isPrefsReady()) return
        due.remove(id)
        save(tasks.value.filterNot { it.id == id })
    }

    /**
     * Picks the moment the timer should fire next. With [catchUp], a task whose time passed while the
     * app was closed (up to [CATCH_UP_MS] ago) fires on the next tick, so opening the app runs it late
     * rather than losing it; anything older waits for the next occurrence, like Android's alarm did.
     * Saving a task never catches up: a time the user picked in the past means tomorrow.
     */
    private fun arm(t: ScheduledTask, catchUp: Boolean) {
        val now = nowMillis()
        val previous = previousTime(t, now)
        due[t.id] = if (catchUp && now - previous <= CATCH_UP_MS && previous > t.lastRun) previous else nextTime(t, now)
    }

    /**
     * The next time [t] should run after [from], in local time. Android used `Calendar`;
     * here the epoch is shifted by the phone's offset, the hour is placed by arithmetic and
     * the offset is taken back out.
     */
    fun nextTime(t: ScheduledTask, from: Long = nowMillis()): Long {
        val offset = zoneOffsetMinutes() * 60_000L
        var at = dayStart(from, offset) + t.hour * 3_600_000L + t.minute * 60_000L - offset
        if (at <= from) at += step(t)
        return at
    }

    /** The most recent time [t] should have run at or before [from]. */
    private fun previousTime(t: ScheduledTask, from: Long): Long {
        val offset = zoneOffsetMinutes() * 60_000L
        var at = dayStart(from, offset) + t.hour * 3_600_000L + t.minute * 60_000L - offset
        if (at > from) at -= step(t)
        return at
    }

    /** Midnight of the local day [ms] falls into, as an epoch shifted by the offset. */
    private fun dayStart(ms: Long, offset: Long): Long = ((ms + offset) / 86_400_000L) * 86_400_000L

    private fun step(t: ScheduledTask): Long = (if (t.repeat == "weekly") 7L else 1L) * 86_400_000L

    /**
     * Starts the in-app timer: every 30 s it runs the tasks that came due. The host calls it
     * once, after [Graph.init]; calling it again does nothing.
     */
    fun start() {
        if (started) return
        started = true
        load()
        Graph.scope.launch {
            while (true) {
                delay(30_000)
                runDue()
            }
        }
    }

    private suspend fun runDue() {
        if (!Graph.isPrefsReady()) return
        val now = nowMillis()
        // a copy: running a task calls upsert, which rewrites tasks.value
        tasks.value.filter { it.enabled }.forEach { t ->
            val at = due.getOrPut(t.id) { nextTime(t) }
            if (now >= at) runTask(t)
        }
    }

    /** Creates the session and sends the prompt, then records the run (a "once" task turns itself off). */
    private suspend fun runTask(t: ScheduledTask) {
        runCatching {
            val s = Graph.chats.createSession(t.directory)
            Graph.chats.send(s.id, t.prompt)
        }.onFailure { Graph.chats.toasts.tryEmit("Scheduled task failed: ${it.message}") }
        upsert(t.copy(lastRun = nowMillis(), enabled = t.repeat != "once"))
    }
}
