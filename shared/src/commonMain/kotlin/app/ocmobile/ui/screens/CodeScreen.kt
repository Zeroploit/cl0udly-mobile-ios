package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.Session
import app.ocmobile.data.ConnectLink
import app.ocmobile.platform.nowMillis
import app.ocmobile.platform.scanQrCode
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.FloatItem
import app.ocmobile.ui.components.FloatMenu
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.MenuBtn
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop
import app.ocmobile.ui.normalizeServerUrl
import kotlinx.coroutines.launch

/**
 * The Code tab like in Claude: the computer the phone is connected to and a list of sessions as cards.
 *
 * Android listed several devices and switched between them. iOS cannot run the server, so there is
 * always exactly one computer: the chip shows it and the sheet edits its address and password.
 */
@Composable
fun CodeScreen(nav: Nav, onMenu: () -> Unit) {
    val c = Oc.c
    val sessions = Graph.chats.codeOnly(Graph.chats.sessions.collectAsState().value)
    val statuses by Graph.chats.statuses.collectAsState()
    val questions by Graph.chats.questions.collectAsState()
    val perms by Graph.chats.permissions.collectAsState()
    val connected by Graph.events.connected.collectAsState()
    var filter by remember { mutableStateOf("all") }
    var filterMenu by remember { mutableStateOf(false) }
    var editServer by remember { mutableStateOf(false) }
    val pinned = Graph.prefs.pinnedSet()
    // Android showed the picked device's name here; iOS has one computer and no device to pick.
    // `active()` is null on this platform, so the card decides on `isRemote` instead.
    val remote = Graph.devices.isRemote || Graph.devices.active() != null
    val activeId by Graph.devices.activeId.collectAsState()

    LaunchedEffect(activeId) { Graph.chats.refreshSessions() }

    val needInput = (questions.map { it.sessionID } + perms.map { it.sessionID }).toSet()
    val list = sessions.filter {
        when (filter) {
            "pinned" -> it.id in pinned
            "working" -> statuses[it.id] != null
            "input" -> it.id in needInput
            else -> true
        }
    }

    Box(Modifier.fillMaxSize().screenBackdrop("CodeScreen1")) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Remote devices", left = { MenuBtn(onMenu) }, right = { IconBtn(Ic.Clock2, { nav.push(Route.Recents) }) })
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Txt("Devices", Modifier.padding(start = 8.dp, top = 8.dp), size = 16.sp, color = c.textDim) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DeviceChip(computerName(), connected, connected, Ic.Laptop, { editServer = true }, { editServer = true })
                        Row(
                            Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).tap(onClick = { editServer = true }).height(52.dp).padding(horizontal = 20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Ico(Ic.Plus, size = 22.dp); Spacer(Modifier.width(10.dp)); Txt("Add device", size = 18.sp) }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Txt("Sessions", Modifier.weight(1f), size = 16.sp, color = c.textDim)
                        Box {
                            Row(Modifier.clip(CircleShape).clickable { filterMenu = true }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Txt(mapOf("all" to "All", "pinned" to "Pinned", "working" to "Working", "input" to "Needs input")[filter] ?: "All", size = 16.sp, color = c.textDim)
                                Spacer(Modifier.width(4.dp)); Ico(Ic.ChevronDown, tint = c.textDim, size = 18.dp)
                            }
                            if (filterMenu) FloatMenu({ filterMenu = false },
                                listOf("all" to "All", "pinned" to "Pinned", "working" to "Working", "input" to "Needs input").map { (k, l) ->
                                    FloatItem(l, selected = filter == k) { filter = k }
                                })
                        }
                    }
                }
                if (list.isEmpty()) item {
                    Txt(
                        if (!connected && Graph.server.configured) "Can't reach ${computerName()}. Check that it is on and that opencode serve is running." else "No code sessions yet. Chats are in the menu; pick a project to start coding.",
                        Modifier.padding(horizontal = 8.dp, vertical = 24.dp), size = 16.sp, color = c.textDim,
                    )
                }
                items(list, key = { it.id }) { s -> SessionCard(s, statuses[s.id], needInput, questions.firstOrNull { it.sessionID == s.id }?.let { q -> q.questions.firstOrNull()?.question }, remote, connected, nav) }
            }
        }
        Pill("New session", { nav.push(Route.NewCode) }, Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 24.dp), icon = Ic.Plus)
    }
    if (editServer) ServerSheet { editServer = false }
}

/** The computer's address as the chip shows it: without the scheme, or a plain name when none is set. */
private fun computerName(): String {
    if (!Graph.isPrefsReady()) return "Your computer"
    val url = Graph.server.url.removePrefix("http://").removePrefix("https://").trimEnd('/')
    return url.ifBlank { "Your computer" }
}

@Composable
private fun DeviceChip(name: String, online: Boolean, active: Boolean, icon: ImageVector, onClick: () -> Unit, onLong: () -> Unit, busy: Boolean = false) {
    val c = Oc.c
    Row(
        Modifier.clip(CircleShape).background(if (active) c.surfaceHigh else Color.Transparent)
            .border(1.dp, if (active) c.text.copy(alpha = .5f) else c.border, CircleShape)
            .tap(onLongClick = onLong, onClick = onClick).height(52.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = if (active) c.text else c.textDim, size = 22.dp); Spacer(Modifier.width(10.dp))
        Txt(name, size = 18.sp, maxLines = 1)
        if (busy) { Spacer(Modifier.width(10.dp)); Spinner(14.dp, stroke = 2.dp) }
        else if (active) {
            Spacer(Modifier.width(10.dp)); Box(Modifier.size(8.dp).clip(CircleShape).background(if (online) c.ok else c.danger))
            if (!online) { Spacer(Modifier.width(6.dp)); Txt("Offline", size = 14.sp, color = c.danger) }
        }
    }
}

@Composable
private fun SessionCard(s: Session, status: String?, needInput: Set<String>, question: String?, remote: Boolean, connected: Boolean, nav: Nav) {
    val c = Oc.c
    var actions by remember { mutableStateOf(false) }
    val dir = s.directory.trimEnd('/').substringAfterLast('/').ifBlank { "/" }
    Column(
        Modifier.fillMaxWidth().clip(Oc.round(30.dp)).background(Color.Black.copy(alpha = .22f)).border(1.dp, c.border.copy(alpha = .6f), Oc.round(30.dp))
            .tap(scale = 0.985f, onLongClick = { actions = true }) { nav.openChat(s.id) }.padding(16.dp),
    ) {
        SessionActionsMenu(s, actions) { actions = false }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(Oc.round(16.dp)).background(c.surface), contentAlignment = Alignment.Center) {
                if (status != null) Spinner(22.dp) else Ico(Ic.Code, tint = c.textDim, size = 24.dp)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt(s.displayTitle, Modifier.weight(1f), size = 19.sp, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Txt(shortAgo(s.time.updated), size = 15.sp, color = c.textDim)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (remote) {
                        Ico(Ic.Laptop, tint = if (connected) c.ok else c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp))
                        Txt(if (connected) "Connected" else "Disconnected", size = 16.sp, color = if (connected) c.ok else c.textDim)
                        Txt("  •  Remote control", size = 16.sp, color = c.textDim)
                    } else {
                        Ico(Ic.Folder, tint = c.textDim, size = 20.dp); Spacer(Modifier.width(8.dp))
                        Txt(dir, size = 16.sp, color = c.textDim, maxLines = 1)
                    }
                }
            }
        }
        if (s.id in needInput) {
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().clip(Oc.round(16.dp)).background(c.surface).padding(16.dp)) {
                Txt(question ?: "Waiting for your permission", size = 16.sp, maxLines = 3, lineHeight = 22.sp)
            }
        }
    }
}

/** "now", "5m", "3h", "12d", then a short date ("24 Nov"). */
fun shortAgo(ms: Long): String {
    if (ms <= 0) return ""
    val d = (nowMillis() - ms) / 1000
    return when {
        d < 60 -> "now"
        d < 3600 -> "${d / 60}m"
        d < 86400 -> "${d / 3600}h"
        d < 30 * 86400 -> "${d / 86400}d"
        // Android used SimpleDateFormat("d MMM"); the date line already has the day and the month name
        else -> englishDate(ms).substringAfter(", ").split(' ').let { p -> if (p.size >= 2) p[0] + " " + p[1].take(3) else p[0] }
    }
}

/**
 * The connection to the computer. Android added devices from a QR code or by hand and switched
 * between them; iOS has one server, so this sheet shows the current one and edits its address.
 */
@Composable
private fun ServerSheet(onDismiss: () -> Unit) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(Graph.server.url) }
    var pass by remember { mutableStateOf(Graph.server.password) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    OcSheet(onDismiss, "Add device", skipHalf = false) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("The phone is a client: OpenCode runs on your computer and this app talks to it. On the computer, run this — it starts OpenCode and shows a QR code:", size = 16.sp, color = c.textDim, lineHeight = 22.sp)
            Txt(QR_COMMAND, Modifier.clip(Oc.round(14.dp)).background(c.codeBg).padding(12.dp), size = 13.sp, family = Oc.mono)
            // the camera reads the same link the phone can also open from its own camera app
            SmallButton("Scan QR code", {
                error = null
                scanQrCode { r ->
                    if (r == null) return@scanQrCode
                    if (ConnectLink.offer(r)) onDismiss() else error = "That's not a cl0udly connection code"
                }
            })
            Txt("Or enter it by hand:", size = 14.sp, color = c.textDim, modifier = Modifier.padding(top = 6.dp))
            OcTextField(url, { url = it }, placeholder = "Address, e.g. 192.168.0.5:4096")
            OcTextField(pass, { pass = it }, placeholder = "Server password", password = true)
            error?.let { Txt(it, size = 15.sp, color = c.danger) }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                if (busy) Spinner(28.dp) else SmallButton("Connect", {
                    if (url.isBlank()) { error = "Enter the address"; return@SmallButton }
                    busy = true; error = null
                    val full = normalizeServerUrl(url)
                    scope.launch {
                        try {
                            Graph.api.probe(full, pass)
                            Graph.server.url = full
                            Graph.server.password = pass
                            onDismiss()
                        } catch (e: Exception) {
                            error = e.message?.takeIf { it.isNotBlank() } ?: "Can't reach the server"
                        } finally { busy = false }
                    }
                }, filled = true)
            }
        }
    }
}

/** Command for the computer: starts OpenCode with a password and shows a QR code for the phone. */
private const val QR_COMMAND = "curl -fsSL https://raw.githubusercontent.com/mistqkw/cl0udly-mobile/main/tools/connect-qr.sh | sh"
