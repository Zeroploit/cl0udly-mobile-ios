package app.ocmobile.platform

/**
 * Local notifications, the one thing Android did natively inside `LiveNotify` and the shared
 * code cannot: the system tray there, `UNUserNotificationCenter` here.
 *
 * [route] is the string `Graph.chats.openRequest` already understands (`"route:settings"`, or a
 * session id on its own): tapping the notification puts it there, so a tap lands in the right chat.
 *
 * [id] is what the platform reads back when a button is pressed, so it carries the answer:
 *
 * - `"live|<sessionID>"`: the running task, with a Stop action.
 * - `"ask|<requestID>|<sessionID>"`: a permission request, with Allow / Always / Deny.
 * - `"q|<requestID>|<sessionID>"`: an agent question, no buttons.
 *
 * Anything else is a plain banner.
 */
expect object PlatformNotify {
    /** Asks the system for alert, sound and badge permission; harmless when already decided. */
    fun requestPermission()

    /** The last known answer of the system, without blocking; false until one arrives. */
    fun authorized(): Boolean

    /** Shows or replaces the notification with [id] right away, with no trigger. */
    fun show(id: String, title: String, body: String, route: String?)

    /** Removes the notification with [id], pending and already delivered. */
    fun cancel(id: String)

    /** Removes every notification this app posted. */
    fun cancelAll()
}
