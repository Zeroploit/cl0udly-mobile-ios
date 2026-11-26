@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * The system auth sheet, ASWebAuthenticationSession: Safari in a sheet, with the app's cookies out
 * of the way. It dismisses itself and calls back with the redirect URL, which is what Android got
 * from its injected script's `onKey`.
 *
 * The session and its presentation anchor are held in module state: UIKit keeps both weakly, and a
 * sheet whose session is collected mid-flight never calls back. [generation] tells a late callback
 * from a session that has already been replaced or cancelled.
 */
private var activeSession: ASWebAuthenticationSession? = null
private var activeAnchor: AuthAnchor? = null
private var generation = 0

actual fun openAuthSession(url: String, callbackScheme: String, onResult: (String?) -> Unit) {
    val target = NSURL.URLWithString(url) ?: return onResult(null)
    activeSession?.cancel()
    val id = ++generation
    val anchor = activeAnchor ?: AuthAnchor().also { activeAnchor = it }
    val session = ASWebAuthenticationSession(
        uRL = target,
        callbackURLScheme = callbackScheme,
    ) { callbackUrl: NSURL?, _: NSError? ->
        // the error is set when the person swipes the sheet away too; both end the same way
        val result = callbackUrl?.absoluteString
        onMain {
            if (id == generation) activeSession = null
            onResult(result)
        }
    }
    session.presentationContextProvider = anchor
    // no shared cookies: the person signs in to the console, not to whatever Safari already had
    session.prefersEphemeralWebBrowserSession = true
    activeSession = session
    if (!session.start()) {
        activeSession = null
        onResult(null)
    }
}

actual fun closeAuthSession() {
    val session = activeSession ?: return
    activeSession = null
    generation++
    session.cancel()
}

private class AuthAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    /** The sheet needs a window; the top controller has one unless it is still off screen. */
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        topViewController()?.view?.window
            ?: UIApplication.sharedApplication.keyWindow
            ?: UIWindow(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))
}
