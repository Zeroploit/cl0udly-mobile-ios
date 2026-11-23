package app.ocmobile.shared

import androidx.compose.ui.window.ComposeUIViewController
import app.ocmobile.platform.UserDefaultsStore
import platform.UIKit.UIViewController

/**
 * Hosts [App] inside the iOS app. Swift reaches this as
 * `MainViewControllerKt.MainViewController()`.
 *
 * The store is built outside the composition on purpose: a new instance on
 * every recomposition would reset the screen's state.
 */
fun MainViewController(): UIViewController {
    val store = UserDefaultsStore()
    return ComposeUIViewController { App(store) }
}
