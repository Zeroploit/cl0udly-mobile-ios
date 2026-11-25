package app.ocmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.net.ServerEndpoint
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.MiniCloudIcon
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.lookBackdrop
import kotlinx.coroutines.launch

/**
 * The first screen on a device that has no server of its own: the address and
 * password of the `opencode serve` running on the user's computer.
 *
 * The address is probed against `/global/health` and only saved after the
 * server answers, so a typo never replaces the address that worked.
 */
@Composable
fun ServerConnectScreen(settings: ServerSettings) {
    val c = Oc.c
    var url by remember { mutableStateOf(settings.url) }
    var password by remember { mutableStateOf(settings.password) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { OpenCodeApi(endpoint = { ServerEndpoint("", "") }) }

    Column(
        modifier = Modifier.fillMaxSize().lookBackdrop(home = true).statusBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiniCloudIcon(72.dp)
        Spacer(Modifier.height(20.dp))
        Heading("cl0udly", align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Txt(
            "Connect to OpenCode on your computer.",
            size = 15.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 21.sp,
        )
        Spacer(Modifier.height(32.dp))

        OcTextField(
            value = url,
            onChange = { url = it; error = null },
            placeholder = "192.168.1.10:4096",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(10.dp))
        OcTextField(
            value = password,
            onChange = { password = it; error = null },
            placeholder = "Password",
            modifier = Modifier.fillMaxWidth(),
            password = true,
            keyboardType = KeyboardType.Password,
        )
        error?.let {
            Spacer(Modifier.height(10.dp))
            Txt(it, size = 14.sp, color = c.danger, align = TextAlign.Center)
        }
        Spacer(Modifier.height(20.dp))

        Box(contentAlignment = Alignment.Center) {
            Pill(if (busy) "Connecting…" else "Connect", onClick = {
                if (busy) return@Pill
                val clean = normalizeServerUrl(url)
                if (clean.isEmpty()) { error = "Enter the address of your computer."; return@Pill }
                busy = true
                error = null
                scope.launch {
                    try {
                        api.probe(clean, password)
                        settings.url = clean
                        settings.password = password
                    } catch (e: Exception) {
                        error = e.message?.takeIf { it.isNotBlank() } ?: "Could not connect."
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy)
            if (busy) Spinner(18.dp, color = c.bg, stroke = 2.dp)
        }

        Spacer(Modifier.height(28.dp))
        Txt(
            "Run `opencode serve --hostname 0.0.0.0` on your computer, on the same network, and open port 4096.",
            size = 13.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 19.sp, family = Oc.mono,
        )
        Spacer(Modifier.height(40.dp))
    }
}
