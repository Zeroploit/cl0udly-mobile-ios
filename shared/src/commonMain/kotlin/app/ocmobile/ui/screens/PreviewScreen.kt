package app.ocmobile.ui.screens

import app.ocmobile.ui.theme.screenBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// The phone MCP bridge that served project files exists on Android only; the
// shared app has no server of its own, so the port and the preview path are
// kept as constants for the same labels and URLs.
private const val PHONE_PORT = 4097
private fun previewUrl(guestPath: String) = "http://127.0.0.1:$PHONE_PORT/preview/" + guestPath.trimStart('/')

// Parsing "scheme://host:port/path" without android.net.Uri
private class UrlParts(u: String) {
    private val rest: String
    val scheme: String
    val host: String
    val port: Int
    val path: String

    init {
        val noScheme = u.substringAfter("://", "")
        scheme = if (noScheme.isEmpty()) "" else u.substringBefore("://")
        val authorityAndPath = noScheme.substringBefore('?').substringBefore('#')
        val authority = authorityAndPath.substringBefore('/')
        rest = authorityAndPath.removePrefix(authority)
        val hostPort = authority.substringBefore('@').substringBefore(':')
        val portStr = authority.substringBefore('/').substringAfter(':', "")
        host = hostPort
        port = portStr.toIntOrNull() ?: if (scheme == "https") 443 else 80
        path = rest
    }
}

/**
 * The app's browser. Two jobs:
 * - live preview of what the agent builds: a dev server from the environment (localhost is shared with the app)
 *   or the project's .html file through the bridge; when the agent edits files, the page reloads by itself;
 * - any web page from a chat link, like Claude's in-app browser: you browse inside, back goes back a page.
 * The side menu can't be pulled out here (Root turns its swipe off), so scrolling the page is never taken over.
 *
 * Common code has no WebView yet, so the page area is a quiet placeholder; the frame
 * (top bar, loading line, browsing bar, "Live" row) is exactly as on Android.
 */
@OptIn(FlowPreview::class, ExperimentalComposeUiApi::class)
@Composable
fun PreviewScreen(url: String, sessionId: String?, nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf(label(url)) }
    var failed by remember { mutableStateOf<String?>(null) }
    var reloads by remember { mutableStateOf(0) }
    // a page from the web (not the project's own server or file): browse freely, no "Live" bar
    val web0 = remember(url) { UrlParts(url) }
    val browsing = remember(url) { web0.host !in setOf("127.0.0.1", "localhost") }

    // the agent edited a file: reload (with a pause, so a series of edits gives one reload)
    if (!browsing) LaunchedEffect(Unit) {
        Graph.events.events.filter { e ->
            e.type == "file.edited" || (e.type == "message.part.updated" && e.props.obj("part")?.let { p ->
                p.str("tool") in setOf("edit", "write", "apply_patch", "patch", "multiedit") && p.obj("state")?.str("status") == "completed"
            } == true)
        }.debounce(700).collect { reloads++ }
    }
    BackHandler(enabled = browsing) { }

    Column(Modifier.fillMaxSize().screenBackdrop("PreviewScreen1")) {
        TopBar(title, left = { IconBtn(if (browsing) Ic.Close else Ic.Back, { nav.pop() }) }, right = {
            IconBtn(Ic.Refresh, { failed = null })
            IconBtn(Ic.External, { })
        })
        // a thin line that fills as the page loads, then becomes the usual divider
        Box(Modifier.fillMaxWidth().height(2.dp).background(c.border))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // no WebView in common code: the page opens in the system browser for now
            Column(Modifier.fillMaxSize().screenBackdrop("PreviewScreen2").padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(label(url), size = 20.sp)
                Spacer(Modifier.height(8.dp))
                Txt("The page opens outside the app for now.", size = 15.sp, color = c.textDim, lineHeight = 21.sp, align = TextAlign.Center)
                Spacer(Modifier.height(18.dp))
            }
            failed?.let { msg ->
                Column(Modifier.fillMaxSize().screenBackdrop("PreviewScreen2").padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt("The page didn't open", size = 20.sp)
                    Spacer(Modifier.height(8.dp))
                    Txt(if (url.contains(":$PHONE_PORT/")) msg else "$msg\n\nIs the dev server still running? Ask OpenCode to start it again.", size = 15.sp, color = c.textDim, lineHeight = 21.sp, align = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Pill("Try again", { failed = null })
                }
            }
        }
        if (browsing) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Ic.Back, { }, tint = c.textDim.copy(alpha = 0.4f))
            IconBtn(Ic.Back, { }, Modifier.graphicsLayer { rotationZ = 180f }, tint = c.textDim.copy(alpha = 0.4f))
            Txt(UrlParts(url).host.removePrefix("www."), Modifier.weight(1f).padding(horizontal = 8.dp), size = 13.sp, color = c.textDim, maxLines = 1, align = TextAlign.Center)
            IconBtn(Ic.Share, { }, iconSize = 21.dp)
        }
        if (sessionId != null && !browsing) Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (reloads > 0) c.ok else c.textDim))
            Spacer(Modifier.size(8.dp))
            Txt(if (reloads > 0) "Live · reloaded $reloads×" else "Live · reloads when OpenCode edits files", Modifier.weight(1f), size = 13.sp, color = c.textDim, maxLines = 1)
            // one button: ask the agent to publish the site and send the link
            Pill("Publish", {
                scope.launch { runCatching { Graph.chats.send(sessionId, "Publish this site to GitHub Pages (create the repo if needed) and send me the link.") } }
                nav.pop()
            })
        }
    }
}

private fun label(u: String): String {
    val uri = UrlParts(u)
    return if (uri.port == PHONE_PORT) uri.path.substringAfterLast('/').ifBlank { "Preview" }
    else "${uri.host}:${uri.port}${uri.path.takeIf { it != "/" }.orEmpty()}"
}

/** What from the chat can be opened in the preview: dev server addresses from output and written .html files. */
fun previewTargets(chat: app.ocmobile.data.Chat): List<Pair<String, String>> {
    val out = LinkedHashMap<String, String>()
    val re = Regex("""https?://(?:localhost|127\.0\.0\.1|0\.0\.0\.0):(\d{2,5})(/[^\s"'`)\]<>]*)?""")
    chat.messages.takeLast(14).asReversed().forEach { m ->
        m.parts.asReversed().forEach { p ->
            val st = p.raw.obj("state")
            val text = when (p.type) { "text" -> p.text; "tool" -> (st?.str("output") ?: "") + " " + (st?.obj("input")?.str("command") ?: ""); else -> "" }
            re.findAll(text).forEach { mt ->
                val port = mt.groupValues[1]
                if (port != "4096" && port != "$PHONE_PORT") out.putIfAbsent("localhost:$port", "http://127.0.0.1:$port" + mt.groupValues[2])
            }
            if (p.type == "tool" && p.raw.str("tool") in setOf("write", "edit", "apply_patch")) {
                st?.obj("input")?.str("filePath")?.takeIf { it.endsWith(".html") || it.endsWith(".htm") }?.let { f ->
                    out.putIfAbsent(f.substringAfterLast('/'), previewUrl(f))
                }
            }
        }
    }
    return out.entries.take(2).map { it.key to it.value }
}
