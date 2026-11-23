package app.ocmobile.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Entry point of the shared UI. The iOS app hosts this in a
 * `ComposeUIViewController`; Android will host it in a `ComposeView` once the
 * screens move here.
 *
 * It exists to prove the whole iOS chain (Gradle → framework → Xcode → app)
 * before the rest of the UI is migrated.
 */
@Composable
fun App() {
    val info = platformInfo()
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.CenterVertically,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "cl0udly", style = MaterialTheme.typography.headlineLarge)
                Text(text = "shared UI on ${info.name}")
                Text(
                    text = if (info.canRunLocalServer) {
                        "This device can run the local server."
                    } else {
                        "This device connects to a server on your computer."
                    },
                )
            }
        }
    }
}
