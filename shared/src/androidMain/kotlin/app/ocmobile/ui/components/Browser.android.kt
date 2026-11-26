package app.ocmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Android app has its own WebView screens in `:app`; the shared module keeps the stubs so it
 * compiles for the Android target too.
 */
actual val platformBrowser: Boolean = false

@Composable
actual fun PlatformBrowser(url: String, state: BrowserState, commands: BrowserCommands, modifier: Modifier) {
    Box(modifier)
}
