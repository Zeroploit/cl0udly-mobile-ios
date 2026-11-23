package app.ocmobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.Session
import app.ocmobile.net.ServerEndpoint
import kotlinx.coroutines.launch

/**
 * What the app shows on iOS today: the two things it needs before it can be
 * useful, then the server's own session list.
 *
 * It is deliberately small, but it is real: the address is probed with
 * `/global/health`, the sessions come from `/session`, and every request goes
 * through the same shared client the Android app uses.
 */
@Composable
fun ConnectApp(settings: ServerSettings) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (settings.configured) SessionsScreen(settings) else ConnectScreen(settings)
        }
    }
}

@Composable
private fun ConnectScreen(settings: ServerSettings) {
    var url by remember { mutableStateOf(settings.url) }
    var password by remember { mutableStateOf(settings.password) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { OpenCodeApi({ ServerEndpoint("", "") }) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("cl0udly", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Connect to the OpenCode server on your computer.",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; error = null },
            label = { Text("Address") },
            placeholder = { Text("192.168.1.10:4096") },
            singleLine = true,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            label = { Text("Password") },
            singleLine = true,
            enabled = !busy,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            enabled = !busy,
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
                        // Saves only after the server answers: a typo never
                        // replaces the address that worked.
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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Run `opencode serve` on your computer and open port 4096 on the local network.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private sealed interface Load {
    data object Loading : Load
    data class Failed(val reason: String) : Load
    data class Ready(val version: String?, val sessions: List<Session>) : Load
}

@Composable
private fun SessionsScreen(settings: ServerSettings) {
    val api = remember(settings.url, settings.password) {
        OpenCodeApi({ ServerEndpoint(settings.url, settings.password) })
    }
    var state by remember { mutableStateOf<Load>(Load.Loading) }
    var attempts by remember { mutableStateOf(0) }

    LaunchedEffect(settings.url, settings.password, attempts) {
        state = Load.Loading
        state = try {
            Load.Ready(api.healthVersionOrThrow(), api.listSessions())
        } catch (e: Exception) {
            Load.Failed(e.message ?: "The server did not answer.")
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("cl0udly", style = MaterialTheme.typography.titleLarge)
                Text(
                    settings.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { settings.forget() }) { Text("Change") }
        }

        when (val current = state) {
            Load.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is Load.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(current.reason, color = MaterialTheme.colorScheme.error)
                Button(onClick = { attempts++ }) { Text("Try again") }
            }

            is Load.Ready -> {
                Text(
                    text = "OpenCode ${current.version ?: "?"} · ${current.sessions.size} sessions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(current.sessions, key = { it.id }) { session ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(session.displayTitle, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = session.directory.ifBlank { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
