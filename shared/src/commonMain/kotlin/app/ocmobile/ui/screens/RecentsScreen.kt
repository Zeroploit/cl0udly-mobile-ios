package app.ocmobile.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.Search
import app.ocmobile.platform.nowMillis
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.EmptyState
import app.ocmobile.ui.components.FloatItem
import app.ocmobile.ui.components.FloatMenu
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.MenuBtn
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SectionLabel
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.kimiLike
import app.ocmobile.ui.theme.screenBackdrop
import kotlinx.coroutines.delay

fun ago(ms: Long): String {
    val d = (nowMillis() - ms) / 1000
    return when {
        d < 60 -> "just now"
        d < 3600 -> "${d / 60} minute${if (d / 60 == 1L) "" else "s"} ago"
        d < 86400 -> "${d / 3600} hour${if (d / 3600 == 1L) "" else "s"} ago"
        d < 172800 -> "Yesterday"
        else -> "${d / 86400} days ago"
    }
}

@Composable
fun RecentsScreen(nav: Nav, onMenu: () -> Unit) {
    val c = Oc.c
    val sessions = Graph.chats.chatsOnly(Graph.chats.sessions.collectAsState().value)
    val statuses by Graph.chats.statuses.collectAsState()
    val questions by Graph.chats.questions.collectAsState()
    val perms by Graph.chats.permissions.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var filterMenu by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var confirm by remember { mutableStateOf(false) }
    val pinned = Graph.prefs.pinnedSet()

    LaunchedEffect(Unit) { Graph.chats.refreshSessions() }
    // search in chat text and memory, with a pause after typing so the mirror isn't hit on every letter
    var hits by remember { mutableStateOf<List<Search.Hit>>(emptyList()) }
    var memHits by remember { mutableStateOf<List<Search.MemoryHit>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { hits = emptyList(); memHits = emptyList(); return@LaunchedEffect }
        delay(300)
        hits = Search.chats(query.trim())
        memHits = Search.memory(query.trim())
    }

    val needInput = (questions.map { it.sessionID } + perms.map { it.sessionID }).toSet()
    val list = sessions.filter { s ->
        (query.isBlank() || s.displayTitle.contains(query, true) || s.directory.contains(query, true)) && when (filter) {
            "pinned" -> s.id in pinned
            "working" -> statuses[s.id] != null
            "input" -> s.id in needInput
            else -> true
        }
    }

    Box(Modifier.fillMaxSize().screenBackdrop("RecentsScreen1")) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                "Recents",
                left = { if (selecting) IconBtn(Ic.Back, { selecting = false; selected.clear() }) else MenuBtn(onMenu) },
                right = {
                    if (selecting) IconBtn(Ic.Trash, { if (selected.isNotEmpty()) confirm = true }, tint = if (selected.isEmpty()) c.textDim else c.text)
                    else {
                        Box {
                            IconBtn(Ic.Sliders, { filterMenu = true })
                            if (filterMenu) FloatMenu({ filterMenu = false },
                                listOf("all" to "All", "pinned" to "Pinned", "working" to "Working", "input" to "Needs input").map { (k, l) ->
                                    FloatItem(l, selected = filter == k) { filter = k }
                                })
                        }
                        IconBtn(Ic.ListCheck, { selecting = true })
                    }
                },
            )
            // from the drawer: the search bar rises from the bottom to its place, the keyboard opens by itself
            val fromDrawer = remember { SearchHandoff.pending.also { SearchHandoff.pending = false } }
            val fly = remember { Animatable(if (fromDrawer) 1f else 0f) }
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (fromDrawer) {
                    fly.animateTo(0f, spring(0.82f, 260f))
                    runCatching { focus.requestFocus() }
                }
            }
            val pill = Oc.look.id.kimiLike
            val barShape = if (pill) CircleShape else Oc.round(22.dp)
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .graphicsLayer { translationY = fly.value * size.height * 14f; alpha = 1f - fly.value * 0.3f }
                    .clip(barShape)
                    .then(if (pill) Modifier.background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, barShape) else Modifier.border(1.5.dp, c.border, barShape))
                    .padding(horizontal = 18.dp, vertical = if (pill) 15.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.Search, tint = c.textDim, size = 24.dp); Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Search chats and memory", size = 18.sp, color = c.textDim)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 18.sp), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
            }
            if (list.isEmpty() && hits.isEmpty() && memHits.isEmpty()) EmptyState(Ic.Code, "Nothing here", if (sessions.isEmpty()) "Start a session and it will show up here." else "No sessions match this filter.", Modifier.padding(top = 60.dp))
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp)) {
                items(list, key = { it.id }) { s ->
                    var actions by remember { mutableStateOf(false) }
                    Row(
                        Modifier.fillMaxWidth().tap(scale = 1f, onLongClick = { if (!selecting) actions = true }) { if (selecting) { if (s.id in selected) selected.remove(s.id) else selected.add(s.id) } else nav.openChat(s.id) }
                            .padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SessionActionsMenu(s, actions) { actions = false }
                        if (selecting) {
                            val on = s.id in selected
                            Box(Modifier.size(26.dp).clip(Oc.round(8.dp)).border(1.5.dp, if (on) c.link else c.border, Oc.round(8.dp)).background(if (on) c.link else Color.Transparent), contentAlignment = Alignment.Center) {
                                if (on) Ico(Ic.Check, tint = c.bg, size = 18.dp)
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                        Ico(if (statuses[s.id] != null) Ic.Zap else Ic.Terminal, tint = if (statuses[s.id] != null) c.accent else c.textDim, size = 22.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(marked(s.displayTitle, query.trim(), c.accent), color = c.text, fontSize = 19.sp, maxLines = 1, fontFamily = Oc.body)
                            Txt(ago(s.time.updated) + " · " + s.directory.substringAfterLast('/').ifBlank { "/" }, size = 15.sp, color = c.textDim, maxLines = 1)
                        }
                        if (s.id in needInput) Box(Modifier.size(10.dp).clip(CircleShape).background(c.link))
                    }
                }
                if (hits.isNotEmpty()) {
                    item(key = "h-msg") { SectionLabel("In messages") }
                    items(hits.size, key = { "m$it" }) { i ->
                        val h = hits[i]
                        Column(Modifier.fillMaxWidth().tap(scale = 1f) { nav.openChat(h.sessionId) }.padding(horizontal = 20.dp, vertical = 11.dp)) {
                            Txt(h.title.ifBlank { "Untitled" } + " · " + ago(h.time), size = 14.sp, color = c.textDim, maxLines = 1)
                            Text(marked((if (h.user) "You: " else "") + h.snippet, query.trim(), c.accent), color = c.text, fontSize = 16.sp, maxLines = 3, lineHeight = 22.sp, fontFamily = Oc.body)
                        }
                    }
                }
                if (memHits.isNotEmpty()) {
                    item(key = "h-mem") { SectionLabel("In memory") }
                    items(memHits.size, key = { "k$it" }) { i ->
                        val h = memHits[i]
                        Column(Modifier.fillMaxWidth().tap(scale = 1f) { nav.push(Route.Memory) }.padding(horizontal = 20.dp, vertical = 11.dp)) {
                            Txt(h.shelf, size = 14.sp, color = c.textDim, maxLines = 1)
                            Text(marked(h.line, query.trim(), c.accent), color = c.text, fontSize = 16.sp, maxLines = 3, lineHeight = 22.sp, fontFamily = Oc.body)
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) { Pill("New session", { nav.root(Route.Home) }, icon = Ic.Plus) }
    }
    if (confirm) {
        LaunchedEffect(Unit) { val ids = selected.toList(); confirm = false; selecting = false; selected.clear(); Graph.chats.deleteWithUndo(ids) }
    }
}

/** The text with every match of [q] in the accent color and a little bolder, so you see why it was found. */
private fun marked(text: String, q: String, accent: Color) = buildAnnotatedString {
    if (q.length < 2) { append(text); return@buildAnnotatedString }
    var at = 0
    while (true) {
        val i = text.indexOf(q, at, ignoreCase = true)
        if (i < 0) { append(text.substring(at)); break }
        append(text.substring(at, i))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(text.substring(i, i + q.length)) }
        at = i + q.length
    }
}
