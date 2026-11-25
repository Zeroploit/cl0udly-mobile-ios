package app.ocmobile.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.api.OpenCodeApi
import app.ocmobile.api.Part
import app.ocmobile.api.PermissionReq
import app.ocmobile.api.QuestionReq
import app.ocmobile.api.Session
import app.ocmobile.api.obj
import app.ocmobile.api.str
import app.ocmobile.data.ChatMessage
import app.ocmobile.data.Chats
import app.ocmobile.data.Prefs
import app.ocmobile.net.ServerEndpoint
import app.ocmobile.api.EventHub
import app.ocmobile.ui.components.CircleBtn
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.ModelPill
import app.ocmobile.ui.components.RoundBtn
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Everything the connected app needs, in one place: the SSE connection, the
 * session list and the open chats.
 *
 * iOS has no server of its own, so the connection target never changes for the
 * life of a client: when the address or password changes, a new client is built
 * and the old one is closed.
 */
class MobileClient(val api: OpenCodeApi, prefs: Prefs) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val events = EventHub(api)
    val chats = Chats(api, events, prefs, scope)

    init {
        events.start(scope, flowOf("remote"))
        chats.start()
        scope.launch { chats.refresh() }
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) { scope.launch(block = block) }

    fun close() { scope.cancel() }
}

@Composable
fun ConnectedApp(settings: ServerSettings, prefs: Prefs, initialSessionId: String? = null) {
    val api = remember(settings.url, settings.password) {
        OpenCodeApi(endpoint = { ServerEndpoint(settings.url, settings.password) })
    }
    val client = remember(api) { MobileClient(api, prefs) }
    DisposableEffect(client) { onDispose { client.close() } }

    var open by remember { mutableStateOf<Session?>(null) }
    var drawer by remember { mutableStateOf(false) }
    val sessions by client.chats.sessions.collectAsState()

    LaunchedEffect(initialSessionId, sessions) {
        if (initialSessionId != null && open == null) open = sessions.firstOrNull { it.id == initialSessionId }
    }

    Box(Modifier.fillMaxSize().background(Oc.c.bg)) {
        CloudSky()
        val session = open
        if (session == null) {
            Home(client, prefs, sessions, initialSessionId, onMenu = { drawer = true }, onOpen = { open = it })
        } else {
            ChatScreen(client, prefs, session, onMenu = { drawer = true }, onBack = { open = null })
        }
        if (drawer) {
            Drawer(
                client = client,
                current = session,
                onChangeServer = { drawer = false; settings.forget() },
                onOpen = { drawer = false; open = it },
                onNew = { drawer = false; open = null },
                onClose = { drawer = false },
            )
        }
    }
}

// ---------- home ----------

@Composable
private fun Home(
    client: MobileClient,
    prefs: Prefs,
    sessions: List<Session>,
    initialSessionId: String?,
    onMenu: () -> Unit,
    onOpen: (Session) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleBtn(Ic.Menu, onMenu)
            ModelPill(prefs.modelId)
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelCloud()
            Spacer(Modifier.height(28.dp))
            Txt(
                "Up, up and away,\nfriend. Where to?",
                size = 30.sp,
                weight = FontWeight.SemiBold,
                align = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 38.sp,
            )
            Spacer(Modifier.height(10.dp))
            Txt(
                client.api.baseUrl.removePrefix("http://").removePrefix("https://"),
                size = 14.sp,
                color = Oc.c.textDim,
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Txt(it, size = 14.sp, color = Oc.c.danger)
            }
        }

        Composer(
            text = text,
            onText = { text = it },
            placeholder = "Ask me. Give me a task.",
            busy = creating,
            onSend = {
                val body = text.trim()
                if (body.isEmpty() || creating) return@Composer
                creating = true
                error = null
                scope.launch {
                    runCatching {
                        val session = client.chats.createSession(null)
                        client.chats.draft(session.id, body)
                        session
                    }.onSuccess { onOpen(it) }.onFailure { error = it.message ?: "Could not start a chat" }
                    creating = false
                }
            },
            onStop = {},
        )
    }
}

// ---------- chat ----------

@Composable
private fun ChatScreen(client: MobileClient, prefs: Prefs, session: Session, onMenu: () -> Unit, onBack: () -> Unit) {
    val chat = remember(session.id) { client.chats.chat(session.id) }
    LaunchedEffect(chat) { chat.load() }

    val statuses by client.chats.statuses.collectAsState()
    val permissions by client.chats.permissions.collectAsState()
    val questions by client.chats.questions.collectAsState()
    val busy = statuses[session.id] != null
    val listState = rememberLazyListState()
    var text by remember(session.id) { mutableStateOf(client.chats.drafts[session.id].orEmpty()) }

    val tick = chat.messages.size to (chat.messages.lastOrNull()?.parts?.sumOf { it.text.length } ?: 0)
    LaunchedEffect(tick) {
        if (chat.messages.isNotEmpty()) listState.animateScrollToItem(chat.messages.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleBtn(Ic.Menu, onMenu)
            ModelPill(prefs.modelId, onClick = onBack)
            Spacer(Modifier.weight(1f))
            if (busy) CircleBtn(Ic.Stop, onClick = { client.launch { runCatching { client.chats.abort(session.id) } } })
            else CircleBtn(Ic.Plus, onClick = { client.launch { runCatching { client.chats.createSession(session.directory) } } })
        }

        when {
            chat.loading -> Box(Modifier.fillMaxWidth().weight(1f))
            chat.loadError != null -> Column(Modifier.fillMaxWidth().weight(1f).padding(24.dp)) {
                Txt(chat.loadError!!, color = Oc.c.danger)
                Spacer(Modifier.height(12.dp))
                SmallButton("Try again", { client.launch { chat.load() } }, filled = true)
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(chat.messages, key = { it.id }) { m ->
                    Message(m, running = busy && m == chat.messages.lastOrNull())
                }
            }
        }

        permissions.filter { it.sessionID == session.id }.forEach { r ->
            PermissionCard(r) { kind -> client.launch { runCatching { client.chats.replyPermission(r, kind) } } }
        }
        questions.filter { it.sessionID == session.id }.forEach { q -> QuestionCard(q, client) }

        Composer(
            text = text,
            onText = { text = it; client.chats.draft(session.id, it) },
            placeholder = "Reply to cl0udly…",
            busy = busy,
            onSend = {
                val body = text.trim()
                if (body.isBlank()) return@Composer
                client.chats.drafts.remove(session.id)
                text = ""
                client.launch { runCatching { client.chats.send(session.id, body) } }
            },
            onStop = { client.launch { runCatching { client.chats.abort(session.id) } } },
        )
    }
}

// ---------- one message ----------

@Composable
private fun Message(m: ChatMessage, running: Boolean) {
    val c = Oc.c
    if (m.info.isUser) {
        val body = m.parts.filter { it.type == "text" && it.raw.str("synthetic") != "true" }.joinToString("\n") { it.text }
        if (body.isBlank()) return
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(22.dp)).background(c.userBubble)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                Text(body, color = c.text, fontSize = 16.sp, lineHeight = 23.sp, fontFamily = Oc.body)
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        m.parts.forEach { p ->
            when {
                p.type == "text" && p.raw.str("synthetic") != "true" && p.text.isNotBlank() -> Markdown(p.text)
                p.type == "reasoning" && p.text.isNotBlank() -> Txt(
                    p.text, size = 15.sp, color = c.textDim,
                    family = Oc.reply,
                )
                p.type == "tool" -> ToolCard(p)
                p.type == "file" -> Txt("• " + (p.raw.str("filename") ?: "file"), size = 14.sp, color = c.textDim, family = Oc.mono)
            }
        }
        m.info.errorMessage?.let { Txt(it, size = 14.sp, color = c.danger) }

        // The reply's tools row, as in the app: actions plus the model that wrote it.
        if (m.info.time.completed != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Ico(Ic.Copy, tint = c.textDim, size = 19.dp)
                Ico(Ic.Share, tint = c.textDim, size = 19.dp)
                Ico(Ic.Play, tint = c.textDim, size = 19.dp)
                Ico(Ic.Refresh, tint = c.textDim, size = 19.dp)
                Txt(m.info.modelName ?: "", size = 15.sp, color = c.textDim, maxLines = 1)
            }
        }
        if (running) TypingDots()
    }
}

@Composable
private fun ToolCard(p: Part) {
    val c = Oc.c
    val name = p.raw.str("tool") ?: "tool"
    val state = p.raw.obj("state")
    val stat = state?.str("status") ?: "pending"
    val title = state?.str("title")?.takeIf { it.isNotBlank() }
        ?: state?.obj("input")?.let { input ->
            val detail = input.str("command") ?: input.str("filePath") ?: input.str("path") ?: input.str("pattern") ?: input.str("query")
            if (detail.isNullOrBlank()) name else "$name  $detail"
        }
        ?: name

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(if (name in setOf("edit", "write", "patch", "multiedit")) Ic.Edit else Ic.File, tint = c.textDim, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Txt(title.take(160), size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Ico(Ic.ChevronRight, tint = c.textDim, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        when (stat) {
            "running", "pending" -> Spinner()
            "error" -> Ico(Ic.Close, tint = c.danger, size = 18.dp)
            else -> Ico(Ic.Check, tint = c.ok, size = 18.dp)
        }
    }
}

@Composable
private fun TypingDots() {
    val t = rememberInfiniteTransition(label = "typing")
    val phase by t.animateFloat(0f, 3f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val on = phase.toInt() == i
            Box(
                Modifier.size(6.dp).clip(CircleShape)
                    .background(Oc.c.textDim.copy(alpha = if (on) 1f else 0.35f)),
            )
        }
    }
}

@Composable
private fun Spinner() {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    // Read outside the draw scope: drawing code cannot call composables.
    val spinnerColor = Oc.c.textDim
    Canvas(Modifier.size(18.dp)) {
        drawArc(
            color = spinnerColor, startAngle = a, sweepAngle = 250f, useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            topLeft = Offset(2f, 2f), size = Size(size.width - 4f, size.height - 4f),
        )
    }
}

// ---------- composer ----------

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    placeholder: String,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val c = Oc.c
    // The frame is one rounded outline, and the buttons live inside it.
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding().imePadding()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(30.dp)).background(c.surface.copy(alpha = 0.9f))
            .border(1.5.dp, c.border, RoundedCornerShape(30.dp))
            .padding(start = 6.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).border(1.dp, c.border, CircleShape), contentAlignment = Alignment.Center) {
            Ico(Ic.Plus, tint = c.textDim, size = 20.dp)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
            if (text.isEmpty()) {
                Text(placeholder, color = c.textDim, fontSize = 17.sp, fontFamily = Oc.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = text,
                onValueChange = onText,
                textStyle = TextStyle(color = c.text, fontSize = 17.sp, fontFamily = Oc.body),
                cursorBrush = SolidColor(c.text),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(8.dp))
        if (busy) RoundBtn(Ic.Stop, onClick = onStop, size = 44.dp)
        else RoundBtn(Ic.ArrowUp, onClick = onSend, size = 44.dp, enabled = text.isNotBlank())
    }
}

// ---------- drawer ----------

@Composable
private fun Drawer(
    client: MobileClient,
    current: Session?,
    onChangeServer: () -> Unit,
    onOpen: (Session) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
) {
    val c = Oc.c
    val sessions by client.chats.sessions.collectAsState()
    val connected by client.chats.connected.collectAsState()

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircleBtn(Ic.Close, onClose)
            Spacer(Modifier.weight(1f))
            CircleBtn(Ic.Plus, onNew)
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (connected) c.ok else c.textDim))
            Spacer(Modifier.width(10.dp))
            Column {
                Txt("cl0udly", size = 20.sp, weight = FontWeight.SemiBold)
                Txt(
                    client.api.baseUrl.removePrefix("http://").removePrefix("https://"),
                    size = 13.sp, color = c.textDim,
                )
            }
            Spacer(Modifier.weight(1f))
            Txt("Change", size = 14.sp, color = c.link, modifier = Modifier.tap { onChangeServer() }.padding(8.dp))
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            items(sessions, key = { it.id }) { s ->
                Column(
                    Modifier.fillMaxWidth()
                        .then(if (s.id == current?.id) Modifier.background(c.surfaceHigh.copy(alpha = 0.5f)) else Modifier)
                        .tap(onClick = { onOpen(s) })
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                ) {
                    Txt(s.displayTitle, size = 16.sp, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Txt(s.directory.ifBlank { "—" }, size = 12.sp, color = c.textDim, family = Oc.mono, maxLines = 1)
                }
            }
            if (sessions.isEmpty()) {
                item { Txt("No chats yet", size = 15.sp, color = c.textDim, modifier = Modifier.padding(20.dp)) }
            }
        }
    }
}

// ---------- requests ----------

@Composable
private fun PermissionCard(request: PermissionReq, onReply: (String) -> Unit) {
    val c = Oc.c
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Txt("Permission needed", size = 16.sp, weight = FontWeight.SemiBold)
        Txt(request.permission, size = 15.sp, color = c.textDim)
        request.patterns.take(2).forEach {
            Txt(it, size = 14.sp, color = c.textDim, family = Oc.mono, maxLines = 2)
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Deny", { onReply("reject") }, danger = true)
            SmallButton("Allow once", { onReply("once") }, filled = true)
            SmallButton("Always", { onReply("always") })
        }
    }
}

@Composable
private fun QuestionCard(q: QuestionReq, client: MobileClient) {
    val c = Oc.c
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Txt(q.questions.firstOrNull()?.header?.ifBlank { "Question" } ?: "Question", size = 16.sp, weight = FontWeight.SemiBold)
        Txt(q.questions.firstOrNull()?.question.orEmpty(), size = 15.sp)
        q.questions.firstOrNull()?.options?.forEach { o ->
            SmallButton(o.label, { scope.launch { runCatching { client.chats.replyQuestion(q, listOf(listOf(o.label))) } } })
        }
        SmallButton("Skip", { scope.launch { runCatching { client.chats.rejectQuestion(q) } } })
    }
}

// ---------- the night sky ----------

/**
 * Pixel clouds at the top of the screen, like the app's cl0udly backdrop: the
 * sky stays black, the clouds are small blocks of the cloud tones, and they only
 * occupy the top band so the conversation keeps a clean background.
 */
@Composable
private fun CloudSky() {
    val c = Oc.c
    if (!c.isDark) return
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val px = 6.dp.toPx()
        fun block(x: Int, y: Int, tone: Color) {
            drawRect(tone, Offset(x * px, y * px), Size(px, px))
        }
        // Two banks, one on each side, with lit caps: dark stone at night.
        val body = Color(0xFF23262C)
        val cap = Color(0xFF2E323A)
        val base = Color(0xFF1A1D22)
        val shapeL = listOf(
            intArrayOf(0, 12, 5), intArrayOf(2, 9, 8), intArrayOf(4, 7, 9), intArrayOf(7, 6, 8),
            intArrayOf(5, 11, 9), intArrayOf(3, 14, 7), intArrayOf(9, 13, 4),
        )
        for ((i, s) in shapeL.withIndex()) {
            for (k in 0 until s[2]) {
                val tone = when {
                    i == 3 -> cap
                    k >= s[2] - 2 -> body
                    else -> base
                }
                block(s[0] + k, s[1] + i / 2, tone)
            }
        }
        val shapeR = listOf(
            intArrayOf(36, 14, 6), intArrayOf(37, 11, 9), intArrayOf(38, 8, 10), intArrayOf(41, 7, 7),
            intArrayOf(39, 13, 8), intArrayOf(40, 16, 6),
        )
        for ((i, s) in shapeR.withIndex()) {
            for (k in 0 until s[2]) {
                val tone = if (i == 3) cap else if (k >= s[2] - 2) body else base
                block(s[0] + k, s[1] + i / 2, tone)
            }
        }
    }
}

/** The little pixel cloud that greets you on the home screen. */
@Composable
private fun PixelCloud() {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "float")
    val dy by t.animateFloat(-4f, 4f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "dy")
    val tones = if (c.isDark) listOf(Color(0xFFE9E4DA), Color(0xFFB9B2A5), Color(0xFF8B8579)) else listOf(Color(0xFF3E4652), Color(0xFF5F656D), Color(0xFF7C838C))
    Canvas(Modifier.size(width = 132.dp, height = 96.dp)) {
        val px = 6.dp.toPx()
        val y0 = dy.dp.toPx()
        fun block(x: Int, y: Int, tone: Color) = drawRect(tone, Offset(x * px, y * px + y0), Size(px, px))
        val rows = listOf(
            "....XXXX.....",
            "..XXXXXXXX...",
            ".XXXXXXXXXX..",
            "XXXXXXXXXXXX.",
            "XXXXXXXXXXXX.",
            "XXXXXXXXXXXX.",
        )
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == 'X') {
                    val tone = when {
                        y == 0 -> tones[0]
                        y == 1 -> if (x in 3..8) tones[0] else tones[1]
                        y < 4 -> tones[1]
                        else -> tones[2]
                    }
                    block(x + 2, y + 3, tone)
                }
            }
        }
        // the two square eyes
        block(7, 8, Color(0xFF04070C))
        block(10, 8, Color(0xFF04070C))
    }
}
