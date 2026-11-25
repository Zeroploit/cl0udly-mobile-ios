package app.ocmobile.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The computers the phone can connect to.
 *
 * Ported from the Android `Devices.kt` for the iOS client: iOS cannot run the
 * OpenCode server itself and there is no device-switching UI yet, so the app is
 * always a client of one remote computer and `isRemote` is always true.
 */
class Devices {
    val activeId = MutableStateFlow("")
    val isRemote: Boolean get() = true
    fun active(): Device? = null

    class Device(val id: String, val url: String, val password: String)
}
