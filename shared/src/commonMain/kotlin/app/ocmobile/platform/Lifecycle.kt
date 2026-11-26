package app.ocmobile.platform

/**
 * Foreground and background, which commonMain cannot see by itself: Android had the activity's
 * lifecycle, iOS has `UIApplicationWillResignActive` / `UIApplicationDidBecomeActive`.
 *
 * The host wires it once: `PlatformLifecycle.observe { Graph.chats.foreground = it }`.
 */
expect object PlatformLifecycle {
    /** Called whenever the app goes to the background or comes back, plus once with the state right now. */
    fun observe(onChange: (foreground: Boolean) -> Unit)
}
