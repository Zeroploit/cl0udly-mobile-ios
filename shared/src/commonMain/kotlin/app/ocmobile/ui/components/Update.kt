package app.ocmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Oc

/**
 * The "what's new" sheet and the "OpenCode updated" chip, taken from the Android `Update.kt`.
 *
 * The rest of that file (`UpdateHost`, `UpdateBar`, the download and the APK installer) is not
 * ported: iOS installs through the App Store, so there is nothing for the app to check or run.
 */

/**
 * After an update: "Updated to v…" with what's new in it, once per version. The lines come from
 * [WhatsNew]; a version without lines shows nothing.
 */
@Composable
fun WhatsNewHost() {
    val p = Graph.prefs
    // appVersion() is "1.4.2 (312)": the notes are keyed by the short version only.
    val now = app.ocmobile.platform.appVersion().substringBefore(" (")
    if (!p.onboarded || p.seenVersion == now) return
    val lines = WhatsNew.notes[now]
    if (lines.isNullOrEmpty()) { LaunchedEffect(now) { p.seenVersion = now }; return }
    val c = Oc.c
    MagnetSheet(onDismiss = { p.seenVersion = now }, half = false, color = c.surface, corner = 28.dp, handle = false) {
        Column(Modifier.padding(24.dp)) {
            Txt("Updated to v$now", size = 22.sp, weight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Txt("What's new", size = 15.sp, color = c.textDim)
            Spacer(Modifier.height(16.dp))
            lines.forEach { l ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(c.accent))
                    Spacer(Modifier.width(12.dp))
                    Txt(l, size = 15.sp, lineHeight = 21.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            val close = LocalSheetClose.current
            Pill("Nice", { close { p.seenVersion = now } }, Modifier.fillMaxWidth())
        }
    }
}

/** What each version brought, said simply. The newest goes on top; add a line per feature when it lands. */
object WhatsNew {
    val notes: Map<String, List<String>> = mapOf(
        "0.0.2 beta-2" to listOf(
            "New look: a night sky with pixel clouds, and a new launch screen",
            "Menus and windows open right where you tap, as floating cards",
            "The input has a thin frame and one button, grows smoothly and keeps a draft in every chat",
            "Code in replies: line numbers, highlighting, copy, save and preview",
            "Search finds words inside messages, whatever the letter case",
            "A deleted chat can be brought back, and errors say what to do",
            "Fixed: the \"OK\" error and the used-up free limit with GitHub Copilot, broken replies from GPT-OSS",
            "The chat stays where you left it when you scroll up a little",
        ),
        "0.0.2 beta-1" to listOf(
            "Chats and memory move between the phone and the computer by themselves when you're home",
            "Remember this? cards: the agent offers what it heard about you, you decide",
            "Sub-agents work as a team: add them in the model picker and in +, they know the whole conversation",
            "Memory as a graph, plus import and export (a memory zip from Claude works too)",
            "Photos, videos and audio from the agent show as small tiles; code and headings look like in Claude",
            "OpenCode updates itself, and you can go back to the previous version in Server settings",
            "A dot on the menu breathes while the agent works and turns blue when the task is done",
            "Russian and English, voice talk with the cloud, a task timeline and limit warnings",
            "Settings and every section are in every look now",
        ),
        "0.0.1 beta-5" to listOf(
            "The app speaks Russian and English: pick it in Settings → Language",
            "Sub-agents right in the model picker and in +: name them, give each a role and a model",
            "Memory as a graph, plus import and export (a memory zip from Claude works too)",
            "New games with the cloud: Catch the rain, Cups and Echo",
            "A button to jump down in a chat, and a bar when an update is out",
        ),
    )
}

/** "OpenCode updated to …" for a few seconds after the server switched to a new build. */
@Composable
fun OcNotice() {
    val p = Graph.prefs
    if (p.ocNotice.isBlank()) return
    val c = Oc.c
    LaunchedEffect(p.ocNotice) { kotlinx.coroutines.delay(8000); p.ocNotice = "" }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 52.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.clip(CircleShape).background(c.surfaceHigh).border(1.dp, c.border, CircleShape).tap(scale = 0.96f) { p.ocNotice = "" }.padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.ok))
            Spacer(Modifier.width(10.dp))
            Txt("OpenCode updated to ${p.ocNotice}", size = 14.sp)
        }
    }
}
