package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.data.PhoneTools
import app.ocmobile.platform.PlatformMedia
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.screenBackdrop
import kotlinx.coroutines.launch

/**
 * What the agent can do on the phone.
 *
 * Android ran a small MCP server on 127.0.0.1 and the OpenCode server, living on the same phone,
 * called it. Here the server is somebody else's computer: it cannot reach this device, so the
 * tools that need the server to call back stay off and the screen says so. The list, the switches
 * and the copy are the Android ones; what changes is only where the tools run.
 */
@Composable
fun PhoneToolsScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    LaunchedEffect(prefs.phoneTools, rev) {
        status = runCatching { Graph.api.mcpStatus().obj(PhoneTools.NAME)?.str("status") }.getOrNull()
    }
    val off = prefs.phoneToolsOff.split(',').filter { it.isNotBlank() }.toSet()

    Column(Modifier.fillMaxSize().screenBackdrop("PhoneToolsScreen1")) {
        TopBar("Phone tools", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Txt(
                if (Graph.devices.isRemote)
                    "These tools act on the phone itself: remind you, set an alarm, read or fill the clipboard, take a photo, find where you are, put files into Downloads and show the sites it builds. They need the OpenCode server to run on this same phone, so a client of a computer keeps them off."
                else
                    "OpenCode runs on this phone, so it can also act on it: remind you, set an alarm, read or fill the clipboard, take a photo, find where you are, put files into Downloads and show the sites it builds.",
                Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp,
            )
            SettingsGroup {
                SettingsRow(
                    "Let OpenCode use this phone", icon = Ic.Phone,
                    subtitle = when {
                        Graph.devices.isRemote -> "Only for the server on this phone"
                        !prefs.phoneTools -> "Off"
                        status == "connected" -> "Connected · ${PhoneTools.enabled().size} tools"
                        status == null -> "Starts with the server"
                        else -> "Status: $status"
                    },
                    trailing = { OcSwitch(prefs.phoneTools) { prefs.phoneTools = it; scope.launch { PhoneTools.register(); rev++ } } },
                )
                // iOS asks for the camera and the microphone the first time a tool needs them, with the
                // strings already in Info.plist: there is no permission screen to send the person to.
                if (prefs.phoneTools) SettingsRow(
                    "Camera and microphone", subtitle = "Asked the first time a tool needs them", icon = Ic.Camera,
                    onClick = { PlatformMedia.openUrl("app-settings:") },
                )
            }
            if (prefs.phoneTools) {
                SectionLabel("Tools")
                SettingsGroup {
                    PhoneTools.all.forEach { t ->
                        SettingsRow(t.title, subtitle = t.desc.substringBefore(". ").removeSuffix("."), trailing = {
                            OcSwitch(t.name !in off) { on -> prefs.phoneToolsOff = (if (on) off - t.name else off + t.name).joinToString(",") }
                        })
                    }
                }
                Txt(
                    "Try: \u201cremind me in 20 minutes to stretch\u201d, \u201ctake a photo of the board and turn it into notes\u201d, \u201csave the report to Downloads\u201d.\n\n" +
                        "Camera, alarm, clipboard and links work while the app is open: iOS doesn't let apps do that from the background.",
                    Modifier.padding(20.dp), size = 13.sp, color = c.textDim, lineHeight = 19.sp,
                )
            }
        }
    }
}
