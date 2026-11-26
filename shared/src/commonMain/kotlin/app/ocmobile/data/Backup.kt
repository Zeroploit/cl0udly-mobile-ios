package app.ocmobile.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Backup of the environment: on Android it packed the Ubuntu home folder into one `.tgz` and saved
 * it to Downloads, and restoring unpacked it back and restarted the app.
 *
 * On iOS none of that is possible. The chats, the provider sign-ins and the projects live on the
 * computer that runs the server; the phone has no handle on that filesystem, cannot stop that
 * server and cannot write to its Downloads. So only the shape of the old object is kept — the same
 * [State] the screen already renders — and every action reports the limitation instead of
 * pretending to work. A backup is made on the computer itself.
 */
object Backup {

    sealed interface State {
        data object Idle : State
        data class Working(val step: String) : State
        data class Saved(val where: String, val bytes: Long) : State
        data object Restored : State
        data class Failed(val message: String) : State
    }

    val state = MutableStateFlow<State>(State.Idle)

    private const val WHERE = "Backups run on the computer that hosts the server."

    /** Nothing is packed here: the archive would have to be made on the computer. */
    suspend fun export() {
        state.value = State.Failed(WHERE)
    }

    /** [name] is the archive the person picked; the file never leaves the phone, so nothing is unpacked. */
    suspend fun restore(name: String) {
        state.value = State.Failed(WHERE)
    }

    /** Back to the empty state, for a screen that wants to start over. */
    fun reset() {
        state.value = State.Idle
    }
}
