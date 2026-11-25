package app.ocmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.net.ServerEndpoint
import kotlinx.coroutines.launch

/**
 * The first screen on a device that has no server of its own: the address and
 * password of the `opencode serve` running on the user's computer.
 *
 * The address is probed against `/global/health` and only saved after the
 * server answers, so a typo never replaces the address that worked.
 */
@Composable
fun ConnectScreen(settings: ServerSettings) {
    var url by remember { mutableStateOf(settings.url) }
    var password by remember { mutableStateOf(settings.password) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { OpenCodeApi(endpoint = { ServerEndpoint("", "") }) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("cl0udly", style = MaterialTheme.typography.displaySmall)
        Text(
            "Connect to the OpenCode server on your computer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("Address") },
            placeholder = { Text("192.168.1.10:4096") },
            singleLine = true,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            onClick = {
                val clean = normalizeServerUrl(url)
                if (clean.isEmpty()) {
                    error = "Enter the address of your computer."
                    return@Button
                }
                busy = true
                error = null
                scope.launch {
                    try {
                        api.probe(clean, password)
                        settings.url = clean
                        settings.password = password
                    } catch (e: Exception) {
                        error = e.message ?: "Could not connect."
                    } finally {
                        busy = false
                    }
                }
            },
        ) {
            Text(if (busy) "Connecting…" else "Connect")
        }

        if (busy) CircularProgressIndicator()
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "Run `opencode serve --hostname 0.0.0.0` on your computer, on the same network, and open port 4096.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}