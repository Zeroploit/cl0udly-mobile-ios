import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
                .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
                .onOpenURL { url in
                    deliverOpenUrl(url)
                }
    }
}

/// Hands a `cl0udly://` link to Kotlin; the function returns false for links it does not know.
private func deliverOpenUrl(_ url: URL) {
    _ = DeepLinksKt.handleOpenUrl(url: url.absoluteString)
}
