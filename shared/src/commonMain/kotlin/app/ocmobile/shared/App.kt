package app.ocmobile.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.ocmobile.platform.SettingsStore
import app.ocmobile.ui.ConnectApp
import app.ocmobile.ui.ServerSettings

/**
 * Entry point of the shared UI. iOS hosts it in a `ComposeUIViewController`;
 * Android will host it in a `ComposeView` once the screens move here.
 *
 * Today it is the server connection and the server's session list, which is
 * what an iOS build can honestly do: run the app's own server is not possible
 * on iOS, so the phone is a client of the one on your computer.
 */
@Composable
fun App(store: SettingsStore) {
    val settings = remember(store) { ServerSettings(store) }
    ConnectApp(settings)
}
