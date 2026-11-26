package app.ocmobile.ui.screens

import app.ocmobile.ui.theme.screenBackdrop
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.data.Backup
import app.ocmobile.platform.PlatformMedia
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.Fmt
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.launch

/**
 * Backup and moving to another build or phone.
 *
 * The screen is the same as on Android; what changed is underneath: the archive is made from the
 * computer's home folder, and a client cannot reach that filesystem. Both rows stay in place and
 * say so, instead of failing silently.
 */
@Composable
fun BackupScreen(nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    val st by Backup.state.collectAsState()
    // The picked archive never leaves the phone: only its name is needed for the message.
    var pending by remember { mutableStateOf<String?>(null) }
    val busy = st is Backup.State.Working

    Column(Modifier.fillMaxSize().screenBackdrop("BackupScreen1")) {
        TopBar("Backup & move", left = { IconBtn(Ic.Back, { if (!busy) nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 32.dp)) {
            Txt(
                "One file with everything: chats, sign-ins, memory, skills, projects, GitHub and your settings. Restore it on another phone or after reinstalling.",
                Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp,
            )
            SettingsGroup {
                SettingsRow(
                    "Back up everything", subtitle = "Made on the computer that hosts the server", icon = Ic.Download,
                    onClick = { if (!busy) scope.launch { Backup.export() } },
                )
                SettingsRow(
                    "Restore from a backup", subtitle = "Replaces what is on the computer now", icon = Ic.Undo,
                    onClick = {
                        if (busy) return@SettingsRow
                        PlatformMedia.pickFiles { files -> pending = files.firstOrNull()?.name }
                    },
                )
            }
            Txt(
                "The chats and projects live on your computer, not on this phone: a backup is made and restored there.",
                Modifier.padding(20.dp), size = 14.sp, color = c.textDim, lineHeight = 19.sp,
            )
            when (val s = st) {
                is Backup.State.Working -> Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spinner(18.dp); Spacer(Modifier.width(12.dp)); Txt(s.step + "…", size = 15.sp)
                }
                is Backup.State.Saved -> {
                    SectionLabel("Done")
                    Txt("${s.where} · ${Fmt.fixed(s.bytes / 1048576.0, 1)} MB", Modifier.padding(horizontal = 20.dp), size = 15.sp, family = Oc.mono)
                    Spacer(Modifier.height(6.dp))
                    Txt("Keep it private: it holds your sign-ins.", Modifier.padding(horizontal = 20.dp), size = 14.sp, color = c.textDim)
                }
                is Backup.State.Restored -> Txt("Restored. Restarting…", Modifier.padding(20.dp), size = 15.sp)
                is Backup.State.Failed -> Txt(s.message, Modifier.padding(20.dp), size = 14.sp, color = c.danger, lineHeight = 19.sp)
                else -> {}
            }
        }
    }
    pending?.let { name ->
        OcDialog("Restore this backup?", { pending = null }, confirmText = "Restore", danger = true, onConfirm = {
            pending = null; scope.launch { Backup.restore(name) }
        }) { Txt("Chats, memory, skills and settings from the file replace the ones here. The app restarts when it is done.", size = 15.sp, color = c.textDim, lineHeight = 21.sp) }
    }
}
