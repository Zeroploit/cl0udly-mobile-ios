package app.ocmobile.runtime

/**
 * The state of the OpenCode server, as the screens show it.
 *
 * On Android this describes the runtime installed on the phone itself. On iOS there is no
 * local runtime — the phone is a client of the computer — so the same shape describes the
 * connection: [Running] while the server answers, [Stopped]/[Crashed] while it does not.
 * Keeping the type identical lets the settings, setup and server screens stay as they were.
 */
sealed interface RuntimeState {
    /** The environment isn't installed yet. */
    data object NotInstalled : RuntimeState
    data class Installing(val step: String, val progress: Float?, val detail: String = "") : RuntimeState
    data class InstallFailed(val message: String) : RuntimeState
    data object Stopped : RuntimeState
    data object Starting : RuntimeState
    data class Running(val version: String) : RuntimeState
    data class Crashed(val message: String) : RuntimeState
}
