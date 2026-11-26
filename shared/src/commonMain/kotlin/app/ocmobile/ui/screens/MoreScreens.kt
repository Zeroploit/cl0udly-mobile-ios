package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.OcJson
import app.ocmobile.api.str
import app.ocmobile.platform.PlatformMedia
import app.ocmobile.platform.PlatformProfile
import app.ocmobile.platform.appVersion
import app.ocmobile.runtime.RuntimeState
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.Segmented
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

// ---------- Connectors (MCP) ----------

@Composable
fun ConnectorsScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<JsonObject?>(null) }
    var add by remember { mutableStateOf(false) }
    suspend fun load() { status = runCatching { Graph.api.mcpStatus() }.getOrDefault(JsonObject(emptyMap())) }
    LaunchedEffect(Unit) { load() }
    Box(Modifier.fillMaxSize().screenBackdrop("MoreScreens1")) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Connectors", left = { IconBtn(Ic.Back, { nav.pop() }) })
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 120.dp)) {
                val s = status
                if (s == null) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Spinner(28.dp) }
                else if (s.isEmpty()) Txt("No MCP servers configured. Connectors give the agent extra tools — databases, browsers, docs.", Modifier.padding(24.dp), size = 16.sp, color = c.textDim, lineHeight = 23.sp)
                else SettingsGroup {
                    s.forEach { (name, v) ->
                        val st = (v as? JsonObject)?.str("status") ?: "unknown"
                        val on = st == "connected"
                        SettingsRow(name, subtitle = (v as? JsonObject)?.str("error") ?: st.replace('_', ' '), icon = Ic.Puzzle, trailing = {
                            OcSwitch(on) { want ->
                                scope.launch { runCatching { if (want) Graph.api.mcpConnect(name) else Graph.api.mcpDisconnect(name) }; load() }
                            }
                        })
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) { Pill("Add connector", { add = true }, icon = Ic.Plus) }
    }
    if (add) AddConnectorDialog({ add = false; scope.launch { load() } })
}

@Composable
private fun AddConnectorDialog(onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("remote") }
    var target by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    OcDialog("Add connector", onDone, "Add", {
        val cfg = if (kind == "remote") buildJsonObject { put("type", "remote"); put("url", target.trim()); put("enabled", true) }
        else buildJsonObject {
            put("type", "local"); put("enabled", true)
            putJsonArray("command") { target.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { add(JsonPrimitive(it)) } }
        }
        scope.launch {
            runCatching { Graph.api.patchConfig(buildJsonObject { put("mcp", buildJsonObject { put(name.trim(), cfg) }) }); Graph.api.mcpAdd(name.trim(), cfg) }
                .onSuccess { onDone() }.onFailure { error = it.message }
        }
    }) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OcTextField(name, { name = it }, placeholder = "Name (e.g. github)")
            Segmented(listOf("remote" to "Remote URL", "local" to "Local command"), kind, { kind = it })
            OcTextField(target, { target = it }, placeholder = if (kind == "remote") "https://…/mcp" else "npx -y @modelcontextprotocol/server-everything", mono = true)
            error?.let { Txt(it, size = 14.sp, color = Oc.c.danger) }
        }
    }
}

// ---------- Server ----------

/**
 * The server this phone talks to.
 *
 * Android ran OpenCode on the phone and this screen started, stopped and reset it. iOS is a client:
 * the server is the one on the computer, so the screen shows the saved address and password, the
 * state of the connection and nothing that pretends to control a process here.
 */
@Composable
fun ServerScreen(nav: Nav) {
    val c = Oc.c
    val rt by Graph.runtime.state.collectAsState()
    val log by Graph.runtime.log.collectAsState()
    val server = Graph.server
    var showLog by remember { mutableStateOf(false) }
    var forget by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().screenBackdrop("MoreScreens2")) {
        TopBar("Server", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(Oc.round(24.dp)).background(c.surface).padding(20.dp)) {
                val (label, color) = when (val s = rt) {
                    is RuntimeState.Running -> connectionText(s, server.configured) to c.ok
                    is RuntimeState.Starting -> connectionText(s, server.configured) to c.warn
                    is RuntimeState.Crashed -> connectionText(s, server.configured) to c.danger
                    else -> connectionText(s, server.configured) to c.textDim
                }
                Txt(label, size = 19.sp, color = color)
                Txt(server.url.ifBlank { "No address yet" }, size = 15.sp, color = c.textDim, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallButton("Log", { showLog = true })
                    if (server.configured) SmallButton("Forget", { forget = true }, danger = true)
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Address", subtitle = server.url.ifBlank { "Not set" }, icon = Ic.Globe)
                SettingsRow("Password", subtitle = if (server.password.isBlank()) "Not set" else "•".repeat(8), icon = Ic.Key)
            }
            Spacer(Modifier.height(16.dp))
            Txt(
                "OpenCode runs on your computer: this phone only talks to it. Start it there with `opencode serve`, then enter its address here or on the connect screen.",
                size = 13.sp, color = c.textDim, modifier = Modifier.padding(20.dp), lineHeight = 19.sp,
            )
        }
    }

    if (showLog) LogSheet(log) { showLog = false }
    if (forget) OcDialog("Forget this server?", { forget = false }, "Forget", {
        forget = false
        Graph.server.forget()
    }, danger = true) { Txt("The address and password are removed from this phone. Nothing on your computer changes.", size = 16.sp, color = Oc.c.textDim) }
}

/** A sheet of log lines; iOS has no local server, so it usually says there is nothing to show. */
@Composable
private fun LogSheet(lines: List<String>, title: String = "Server log", onDismiss: () -> Unit) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    OcSheet(onDismiss, title, skipHalf = false) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SmallButton("Copy", { clip.setText(AnnotatedString(lines.joinToString("\n"))) })
        }
        Column(Modifier.fillMaxWidth().height(480.dp).verticalScroll(rememberScrollState(), reverseScrolling = true).padding(16.dp)) {
            if (lines.isEmpty()) Txt("No server log on this device.", size = 12.sp, family = Oc.mono, color = c.textDim, lineHeight = 16.sp)
            else Txt(lines.joinToString("\n"), size = 11.sp, family = Oc.mono, color = c.textDim, lineHeight = 15.sp)
        }
    }
}

// ---------- Config editor ----------

@Composable
fun ConfigEditorScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val pretty = remember { Json { prettyPrint = true } }
    suspend fun load() {
        text = runCatching { pretty.encodeToString(JsonObject.serializer(), Graph.api.config()) }.getOrElse { "{}" }; loaded = true
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().screenBackdrop("MoreScreens3")) {
        TopBar("Config", left = { IconBtn(Ic.Back, { nav.pop() }) }, right = { IconBtn(Ic.Refresh, { scope.launch { load() } }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            Txt("Everything OpenCode supports in opencode.json: models, agents, MCP, permissions, formatters, instructions, keybinds. Changes are merged and applied at once.", size = 14.sp, color = c.textDim, modifier = Modifier.padding(bottom = 12.dp), lineHeight = 20.sp)
            if (!loaded) Spinner(26.dp) else OcTextField(text, { text = it; msg = null }, singleLine = false, minLines = 16, mono = true)
            Spacer(Modifier.height(14.dp))
            Pill("Save", {
                val o = runCatching { OcJson.parseToJsonElement(text) as JsonObject }.getOrNull()
                if (o == null) msg = "Not valid JSON object"
                else scope.launch { msg = runCatching { Graph.api.patchConfig(o); "Saved" }.getOrElse { it.message } }
            }, modifier = Modifier.fillMaxWidth())
            msg?.let { Txt(it, size = 15.sp, color = if (it == "Saved") c.ok else c.danger, modifier = Modifier.padding(top = 12.dp)) }
        }
    }
}

// ---------- Profile ----------

@Composable
fun ProfileScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    var name by remember { mutableStateOf(prefs.userName) }
    var rules by remember { mutableStateOf(app.ocmobile.data.Memory.userRules()) }
    var saved by remember { mutableStateOf(false) }
    // the picker hands the bytes over; the platform cuts and stores the photo
    fun pickPhoto() = PlatformMedia.pickImages(multi = false) { files ->
        val bytes = files.firstOrNull()?.bytes ?: return@pickImages
        runCatching {
            val path = PlatformProfile.saveAvatar(bytes) ?: return@runCatching
            prefs.avatarPath.takeIf { it.isNotBlank() }?.let { PlatformProfile.deleteAvatar(it) }
            prefs.avatarPath = path
        }
    }
    Column(Modifier.fillMaxSize().screenBackdrop("MoreScreens4")) {
        TopBar("Profile", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(112.dp) { pickPhoto() }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton(if (prefs.avatarPath.isBlank()) "Add photo" else "Change photo", { pickPhoto() })
                if (prefs.avatarPath.isNotBlank()) SmallButton("Remove", {
                    PlatformProfile.deleteAvatar(prefs.avatarPath)
                    prefs.avatarPath = ""
                })
            }
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt("What should we call you?", size = 17.sp, modifier = Modifier.padding(start = 4.dp))
                OcTextField(name, { name = it })
                Pill("Update profile", { prefs.userName = name.trim() }, enabled = name.trim() != prefs.userName, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Txt("What personal preferences should OpenCode consider in responses?", size = 19.sp, modifier = Modifier.padding(start = 4.dp))
                Txt("Saved as global AGENTS.md — applies to every project.", size = 14.sp, color = c.textDim, modifier = Modifier.padding(start = 4.dp))
                OcTextField(rules, { rules = it; saved = false }, singleLine = false, minLines = 6, placeholder = "Reply in Russian. Prefer small diffs. Ask before big refactors.")
                Pill(if (saved) "Saved" else "Save preferences", {
                    runCatching { app.ocmobile.data.Memory.saveUserRules(rules) }.onSuccess { saved = true }
                }, enabled = !saved, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ---------- About ----------

@Composable
fun AboutScreen(nav: Nav) {
    val c = Oc.c
    val rt by Graph.runtime.state.collectAsState()
    fun open(u: String) = PlatformMedia.openUrl(u)
    val opencode = when (val s = rt) {
        is RuntimeState.Running -> s.version.ifBlank { Graph.prefs.opencodeVersion.ifBlank { "connected" } }
        else -> Graph.prefs.opencodeVersion.ifBlank { "not reported" }
    }
    Column(Modifier.fillMaxSize().screenBackdrop("MoreScreens5")) {
        TopBar("About", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                SettingsRow("OpenCode mobile", subtitle = "v${appVersion()}", icon = Ic.Info)
                SettingsRow("cl0udly", subtitle = Graph.prefs.petCount.let { "Petted $it ${if (it == "1") "time" else "times"}" }, icon = Ic.Cloud, chevron = true, onClick = { nav.push(Route.MascotSettings) })
                SettingsRow("Source on GitHub", subtitle = "mistqkw/cl0udly-mobile", icon = Ic.GitHub, onClick = { open("https://github.com/mistqkw/cl0udly-mobile") })
                SettingsRow("OpenCode (on the computer)", subtitle = opencode, icon = Ic.Terminal)
                SettingsRow("OpenCode documentation", icon = Ic.External, onClick = { open("https://opencode.ai/docs") })
                SettingsRow("OpenCode Go console", icon = Ic.External, onClick = { open("https://opencode.ai/console") })
            }
            // Updates come with the build: the App Store and TestFlight install them, not the app
            Spacer(Modifier.height(16.dp))
            SettingsGroup {
                SettingsRow("Check for updates", subtitle = "Updates come from TestFlight / a new build", icon = Ic.Download)
                SettingsRow("Check automatically", subtitle = "No effect here: the App Store and TestFlight update the app", icon = Ic.Refresh,
                    trailing = { OcSwitch(Graph.prefs.updateAuto) { Graph.prefs.updateAuto = it } })
            }
            Txt(
                "Unofficial client. The OpenCode server, its projects and its files live on your computer; this app only talks to it.",
                size = 13.sp, color = c.textDim, modifier = Modifier.padding(20.dp), lineHeight = 19.sp,
            )
        }
    }
}
