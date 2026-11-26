@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import app.ocmobile.Graph
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * Local notifications through `UNUserNotificationCenter`.
 *
 * The Android original created channels and a `Notification.Builder`; iOS has no channels and no
 * ongoing notifications, so a running task is one notification re-added with the same identifier
 * to update in place. Buttons come from categories, which have to be registered before the
 * notification that uses them is posted.
 *
 * The delegate lives in [delegate] because `UNUserNotificationCenter` holds it weakly. The
 * properties of `UNMutableNotificationContent` are read-only in the Kotlin binding: they are set
 * through their setters.
 */
private const val CATEGORY_LIVE = "oc_live"
private const val CATEGORY_ASK = "oc_ask"

private var delegate: NotifyDelegate? = null
private var registered = false

/** The last authorization answer the system gave, so [PlatformNotify.authorized] never blocks. */
private var lastAuthorized = false

actual object PlatformNotify {

    actual fun requestPermission() {
        ensure()
        // The prompt only appears the first time; later calls just report the standing answer.
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { granted, _ -> lastAuthorized = granted }
    }

    actual fun authorized(): Boolean {
        ensure()
        return lastAuthorized
    }

    actual fun show(id: String, title: String, body: String, route: String?) {
        ensure()
        val kind = id.substringBefore('|')
        val content = UNMutableNotificationContent()
        content.setTitle(title)
        content.setBody(body)
        // A progress notification is re-posted on every step: buzzing each time would be unusable,
        // and iOS has no "alert once" flag like Android's.
        if (kind != "live") content.setSound(UNNotificationSound.defaultSound)
        if (route != null) content.setUserInfo(mapOf<Any?, Any?>("route" to route))
        when (kind) {
            "live" -> content.setCategoryIdentifier(CATEGORY_LIVE)
            "ask" -> content.setCategoryIdentifier(CATEGORY_ASK)
        }
        // A null trigger delivers at once; the same identifier replaces what is already posted.
        val request = UNNotificationRequest.requestWithIdentifier(id, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            // Fails while notifications are not allowed; a delivered request proves they are.
            if (error == null) lastAuthorized = true
        }
    }

    actual fun cancel(id: String) {
        ensure()
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removePendingNotificationRequestsWithIdentifiers(listOf(id))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(id))
    }

    actual fun cancelAll() {
        ensure()
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeAllPendingNotificationRequests()
        center.removeAllDeliveredNotifications()
    }
}

/** Sets the delegate and the button categories once: both must exist before a notification is shown. */
private fun ensure() {
    if (registered) return
    registered = true
    val center = UNUserNotificationCenter.currentNotificationCenter()
    val d = NotifyDelegate()
    delegate = d
    center.delegate = d
    val live = UNNotificationCategory.categoryWithIdentifier(
        identifier = CATEGORY_LIVE,
        actions = listOf(
            UNNotificationAction.actionWithIdentifier(
                identifier = "stop",
                title = "Stop",
                options = UNNotificationActionOptionNone,
            ),
        ),
        intentIdentifiers = emptyList<String>(),
        options = UNNotificationCategoryOptionNone,
    )
    val ask = UNNotificationCategory.categoryWithIdentifier(
        identifier = CATEGORY_ASK,
        actions = listOf(
            UNNotificationAction.actionWithIdentifier(
                identifier = "once",
                title = "Allow",
                options = UNNotificationActionOptionForeground,
            ),
            UNNotificationAction.actionWithIdentifier(
                identifier = "always",
                title = "Always",
                options = UNNotificationActionOptionForeground,
            ),
            UNNotificationAction.actionWithIdentifier(
                identifier = "reject",
                title = "Deny",
                options = UNNotificationActionOptionNone,
            ),
        ),
        intentIdentifiers = emptyList<String>(),
        options = UNNotificationCategoryOptionNone,
    )
    center.setNotificationCategories(setOf(live, ask))
}

/** Answers a tap or a lock-screen button: the notification id says which request it was. */
private class NotifyDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    /**
     * Android drew the notification in the tray even with the app open; iOS drops it unless the
     * delegate says otherwise, so the reply-in notification still shows while you are elsewhere in
     * the app. Progress notifications stay quiet, as on Android.
     */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        val id = willPresentNotification.request.identifier
        val options = if (id.startsWith("live|")) {
            UNNotificationPresentationOptionList
        } else {
            UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList or UNNotificationPresentationOptionSound
        }
        withCompletionHandler(options)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val response = didReceiveNotificationResponse
        val action = response.actionIdentifier
        val request = response.notification.request
        val id = request.identifier
        val route = request.content.userInfo["route"] as? String
        // Framework callbacks arrive on an arbitrary queue; Compose state lives on main.
        onMain {
            handle(action, id, route)
            withCompletionHandler()
        }
    }
}

private fun handle(action: String, id: String, route: String?) {
    if (!Graph.isPrefsReady()) return
    val parts = id.split('|')
    val tapped = action == UNNotificationDefaultActionIdentifier
    when (parts.firstOrNull()) {
        "live" -> if (action == "stop") stopSession(parts.getOrNull(1), id)
        "ask" -> if (!tapped) replyPermission(action, parts.getOrNull(1), parts.getOrNull(2), id)
        else -> Unit
    }
    if (tapped && route != null) Graph.chats.openRequest.value = route
}

private fun stopSession(sessionId: String?, id: String) {
    if (sessionId.isNullOrBlank()) return
    PlatformNotify.cancel(id)
    Graph.scope.launch { runCatching { Graph.chats.abort(sessionId) } }
}

private fun replyPermission(kind: String, requestId: String?, sessionId: String?, id: String) {
    if (requestId.isNullOrBlank()) return
    PlatformNotify.cancel(id)
    // The chat list drops it at once, so the in-app sheet does not show a stale request.
    Graph.chats.permissions.value = Graph.chats.permissions.value.filterNot { it.id == requestId }
    Graph.scope.launch {
        runCatching {
            val dir = Graph.chats.session(sessionId.orEmpty())?.directory
            Graph.api.replyPermission(requestId, dir, kind)
        }
    }
}
