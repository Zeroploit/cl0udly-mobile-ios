package app.ocmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The desktop preview has no WebView: the preview screen shows its note instead. */
actual val platformBrowser: Boolean = false

@Composable
actual fun PlatformBrowser(url: String, state: BrowserState, commands: BrowserCommands, modifier: Modifier) {
    Box(modifier)
}
