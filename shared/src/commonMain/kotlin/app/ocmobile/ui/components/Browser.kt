package app.ocmobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * What the in-app browser reports back to the screen around it: the loading line, the page title,
 * whether back and forward are available, and the address in the bottom bar.
 */
class BrowserState {
    var loading by mutableStateOf(false)
    var progress by mutableStateOf(0)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var title by mutableStateOf<String?>(null)
    var currentUrl by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    /** A page from the web browses freely; the project preview keeps only the local dev server inside. */
    var freeBrowsing by mutableStateOf(false)
}

/**
 * The buttons ask for a reload or a step in history by bumping a counter; the platform view
 * watches them. Unidirectional on purpose: the shared code never touches a WebView.
 */
class BrowserCommands {
    var reload by mutableStateOf(0)
    var back by mutableStateOf(0)
    var forward by mutableStateOf(0)
}

/** Whether this platform draws web pages inside the app (WKWebView on iOS). */
expect val platformBrowser: Boolean

/** The page surface: a WKWebView on iOS; the stubs draw nothing and leave [state] quiet. */
@Composable
expect fun PlatformBrowser(url: String, state: BrowserState, commands: BrowserCommands, modifier: Modifier)
