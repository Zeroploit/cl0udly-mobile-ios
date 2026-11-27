package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.data.SessionSearch
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.kimiLike
import app.ocmobile.ui.theme.screenBackdrop

/**
 * Global search: one box over every chat, titles and message text, the whole
 * history — not only what the drawer lists. [SessionSearch] does the looking;
 * typing is debounced so the server isn't hit on every letter.
 */
@Composable
fun SearchScreen(nav: Nav) {
    val c = Oc.c
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SessionSearch.SearchHit>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // the keyboard opens by itself on entry
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) { busy = false; hits = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        busy = true
        try { hits = SessionSearch.sessions(q) } finally { busy = false }
    }

    Box(Modifier.fillMaxSize().screenBackdrop("SearchScreen1")) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Search", left = { IconBtn(Ic.Back, { nav.pop() }) })
            // the same search bar the recents list has: a pill in Kimi and cl0udly, a rounded box elsewhere
            val pill = Oc.look.id.kimiLike
            val barShape = if (pill) CircleShape else Oc.round(22.dp)
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                    .clip(barShape)
                    .then(if (pill) Modifier.background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, barShape) else Modifier.border(1.5.dp, c.border, barShape))
                    .padding(horizontal = 18.dp, vertical = if (pill) 15.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Ic.Search, tint = c.textDim, size = 24.dp); Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Txt("Search", size = 18.sp, color = c.textDim)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 18.sp), cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                if (busy) { Spacer(Modifier.width(12.dp)); Spinner(size = 20.dp) }
            }
            val q = query.trim()
            when {
                q.length < 2 -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Txt("Search all your chats", size = 16.sp, color = c.textDim)
                }
                hits.isEmpty() -> Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Txt("Nothing found", size = 16.sp, color = c.textDim)
                }
                else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)) {
                    items(hits, key = { it.sessionId }) { h ->
                        Column(Modifier.fillMaxWidth().tap(scale = 1f) { nav.openChat(h.sessionId) }.padding(horizontal = 20.dp, vertical = 13.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Text(
                                    h.title.ifBlank { "Untitled" },
                                    color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, fontFamily = Oc.body,
                                    modifier = Modifier.weight(1f),
                                )
                                if (h.matchCount > 1) { Spacer(Modifier.width(10.dp)); Txt("${h.matchCount}", size = 12.sp, color = c.textDim) }
                            }
                            androidx.compose.material3.Text(
                                h.snippet,
                                color = c.textDim, fontSize = 15.sp, maxLines = 2, lineHeight = 20.sp, fontFamily = Oc.body,
                            )
                        }
                    }
                }
            }
        }
    }
}
