@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import app.ocmobile.platform.PlatformMedia
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import kotlinx.coroutines.delay
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject

/**
 * The in-app browser, backed by WKWebView.
 *
 * Progress, history and the title are polled instead of observed with KVO: one coroutine on the
 * main thread reading four properties is simpler than a key-path observer per property, and the
 * screen only needs them for its thin line and its bottom bar.
 *
 * Links behave as they did on Android: inside a free-browsing page they open in place; in the
 * project preview only the local dev server stays inside, everything else goes to Safari.
 */
actual val platformBrowser: Boolean = true

@Composable
actual fun PlatformBrowser(url: String, state: BrowserState, commands: BrowserCommands, modifier: Modifier) {
    val delegate = remember(state) {
        object : NSObject(), WKNavigationDelegateProtocol {
            // Four of these selectors collapse onto two Kotlin signatures: every one of the
            // clashing declarations carries the annotation, as Kotlin/Native requires.
            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
                state.loading = true
                state.progress = 0
                state.error = null
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                state.loading = false
                state.progress = 100
                state.title = webView.title
                state.currentUrl = webView.URL?.absoluteString
            }

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) =
                failed(withError)

            @ObjCSignatureOverride
            override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) =
                failed(withError)

            private fun failed(error: NSError) {
                // a navigation we cancelled on purpose is not a failure
                if (error.code == NSURLErrorCancelled) return
                state.loading = false
                state.error = error.localizedDescription
            }

            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit,
            ) {
                val target = decidePolicyForNavigationAction.request.URL
                val scheme = target?.scheme
                // mail, phone, app links: their own apps
                if (scheme != null && scheme != "http" && scheme != "https") {
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                    target?.absoluteString?.let { PlatformMedia.openUrl(it) }
                    return
                }
                val host = target?.host
                val local = host == "127.0.0.1" || host == "localhost" || host == "0.0.0.0"
                // the project preview keeps only its own dev server inside
                if (!state.freeBrowsing && host != null && !local) {
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                    target?.absoluteString?.let { PlatformMedia.openUrl(it) }
                    return
                }
                decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
            }
        }
    }

    val webView = remember {
        val configuration = WKWebViewConfiguration().apply {
            websiteDataStore = WKWebsiteDataStore.defaultDataStore()
        }
        WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
            navigationDelegate = delegate
            // WKWebView's own inset handling fights Compose's insets and breaks scrolling
            scrollView.contentInsetAdjustmentBehavior =
                UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
        }
    }

    // a new address: load it (a reload of the same one is a command, below)
    LaunchedEffect(url) {
        NSURL.URLWithString(url)?.let { webView.loadRequest(NSURLRequest(it)) }
    }

    // buttons: reload, back, forward
    LaunchedEffect(commands.reload) { if (commands.reload > 0) webView.reload() }
    LaunchedEffect(commands.back) { if (commands.back > 0 && webView.canGoBack) webView.goBack() }
    LaunchedEffect(commands.forward) { if (commands.forward > 0 && webView.canGoForward) webView.goForward() }

    // the thin line, the history buttons and the address: read while the screen is up
    LaunchedEffect(webView) {
        while (true) {
            state.progress = (webView.estimatedProgress * 100).toInt()
            state.canBack = webView.canGoBack
            state.canForward = webView.canGoForward
            state.currentUrl = webView.URL?.absoluteString ?: state.currentUrl
            delay(120)
        }
    }

    UIKitView(factory = { webView }, modifier = modifier)
}
