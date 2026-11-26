package app.ocmobile.ui.screens

import app.ocmobile.ui.theme.screenBackdrop
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.runtime.RuntimeState
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.MiniCloudIcon
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.Segmented
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.normalizeServerUrl
import app.ocmobile.ui.theme.AppIcons
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * First launch, as a client of a computer. The mascot's five pages come first, then: about you and
 * memory, your settings, where the computer is, and the account. The Android original also installed
 * the environment while the tour ran and asked for background/notification permissions; iOS has
 * neither, so that step became "where is your computer" and nothing waits for a server to come up.
 *
 * TODO(lead): mount this in Root.kt like Android does — on top of the shell, taking every touch,
 * while `!Graph.prefs.onboarded`.
 */

private val STEPS = listOf("Hi", "Chat", "Computer", "Team", "Play", "You", "Setup", "Connect", "Account")
private const val TOUR = 5      // the first five steps are the mascot's tour

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Onboarding() {
    val c = Oc.c
    val prefs = Graph.prefs
    val rt by Graph.runtime.state.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var icon by remember { mutableStateOf<String?>(null) }
    var entering by remember { mutableStateOf<String?>(null) }

    fun go(to: Int) { forward = to > step; step = to.coerceIn(0, STEPS.lastIndex) }
    fun finish(acc: String) { if (entering == null) entering = acc }
    fun finishNow(acc: String) {
        val chosen = icon ?: AppIcons.DEFAULT
        if (chosen != prefs.appIcon) { prefs.appIcon = chosen; AppIcons.apply(chosen) }
        prefs.loginSeen = true
        prefs.onboarded = true
        when (acc) {
            "github" -> Graph.chats.openRequest.value = "route:connect:github-copilot"
            "google" -> Graph.chats.openRequest.value = "route:connect:google"
            "opencode" -> Graph.chats.openRequest.value = "route:console:"
        }
    }

    BackHandler(step > 0) { go(step - 1) }

    Column(Modifier.fillMaxSize().screenBackdrop("Onboarding1").statusBarsPadding().navigationBarsPadding().imePadding()) {
        // top: back, step bars, "Skip"
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (step > 0) Box(Modifier.size(44.dp).clip(CircleShape).tap { go(step - 1) }, contentAlignment = Alignment.Center) { Ico(Ic.Back, size = 22.dp) }
            }
            Row(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                STEPS.indices.forEach { i ->
                    val k by animateFloatAsState(if (i <= step) 1f else 0f, tween(260), label = "seg")
                    Box(Modifier.weight(1f).height(3.dp).clip(CircleShape).background(c.border)) {
                        Box(Modifier.fillMaxWidth(k).height(3.dp).clip(CircleShape).background(c.text))
                    }
                }
            }
            Box(Modifier.size(width = 56.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                if (step < STEPS.lastIndex) Txt("Skip", Modifier.clip(CircleShape).tap { go(if (step < TOUR) TOUR else STEPS.lastIndex) }.padding(8.dp), size = 15.sp, color = c.textDim)
            }
        }

        AnimatedContent(
            step, Modifier.weight(1f).fillMaxWidth(), label = "onboarding",
            transitionSpec = {
                val d = if (forward) 1 else -1
                (fadeIn(tween(220, delayMillis = 60)) + slideInHorizontally(tween(280)) { it / 6 * d }) togetherWith
                    (fadeOut(tween(140)) + slideOutHorizontally(tween(240)) { -it / 6 * d })
            },
        ) { s ->
            when (s) {
                in 0 until TOUR -> TourStep(s)
                TOUR -> YouStep()
                TOUR + 1 -> SetupStep(icon ?: AppIcons.DEFAULT) { icon = it }
                TOUR + 2 -> ConnectStep()
                else -> AccountStep(rt) { a -> finish(a) }
            }
        }

        // bottom: the main button and the connection line
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
            if (step < STEPS.lastIndex) {
                Box(
                    Modifier.fillMaxWidth().height(56.dp).clip(Oc.pill).background(c.text).tap { go(step + 1) },
                    contentAlignment = Alignment.Center,
                ) { Txt(when { step == 0 -> "Show me around"; step < TOUR - 1 -> "Next"; step == TOUR - 1 -> "Let's set up"; else -> "Continue" }, size = 17.sp, color = c.bg, weight = FontWeight.Medium) }
                // the short way: no tour and no setup, a free model, and the chat right away
                if (step == 0) Box(
                    Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp).clip(Oc.pill).tap { finish("free") },
                    contentAlignment = Alignment.Center,
                ) { Txt("Start chatting right away", size = 16.sp, color = c.textDim) }
            }
            Spacer(Modifier.height(12.dp))
            SetupLine(rt)
        }
    }
    entering?.let { acc -> EnterCloud { finishNow(acc) } }
}

/** Connection line, visible on every step: says whether the computer answers. */
@Composable
private fun SetupLine(rt: RuntimeState) {
    val c = Oc.c
    val (text, dot) = when (rt) {
        is RuntimeState.Running -> "Your computer is ready" to c.ok
        is RuntimeState.Stopped -> "Your computer is not answering" to c.danger
        is RuntimeState.Crashed -> "Your computer is not answering" to c.danger
        else -> "Connecting to your computer…" to c.accent
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Txt(text, size = 13.sp, color = c.textDim, maxLines = 1)
    }
}

@Composable
private fun StepTitle(title: String, sub: String) {
    val c = Oc.c
    Heading(title, Modifier.padding(horizontal = 24.dp), size = 30.sp)
    Spacer(Modifier.height(8.dp))
    Txt(sub, Modifier.padding(horizontal = 24.dp), size = 16.sp, color = c.textDim, lineHeight = 23.sp)
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun Scroll(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 12.dp, bottom = 16.dp), content = content)

@Composable
private fun Appear(shown: Boolean, i: Int, content: @Composable () -> Unit) =
    AnimatedVisibility(shown, enter = fadeIn(tween(160, delayMillis = 50 * i)) + slideInVertically(tween(200, delayMillis = 50 * i)) { it / 4 }) { content() }

// ---------- about you and memory ----------

@Composable
private fun YouStep() {
    val c = Oc.c
    val prefs = Graph.prefs
    Scroll {
        StepTitle("A bit about you", "So I can greet you and, if you want, remember what matters.")
        Txt("What should it call you?", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            OcTextField(prefs.userName, { prefs.userName = it.take(40) }, placeholder = "Your name")
        }
        Spacer(Modifier.height(26.dp))
        Txt("Memory", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Choice(prefs.memoryEnabled, Ic.Scroll, "Remember me", "Keeps short notes about you and your projects on this phone, so you don't repeat yourself. You can see and delete everything.") { prefs.memoryEnabled = true; prefs.memoryAuto = true }
            Choice(!prefs.memoryEnabled, Ic.Ghost, "Start fresh every chat", "Nothing is saved between chats. You can turn memory on later.") { prefs.memoryEnabled = false }
        }
    }
}

@Composable
private fun Choice(on: Boolean, icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(if (on) 2.dp else 1.dp, if (on) c.link else c.border, Oc.round(18.dp))
            .tap(scale = 0.98f, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Ico(icon, tint = if (on) c.link else c.textDim, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 16.sp, weight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Txt(sub, size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (on) c.link else c.border, CircleShape).padding(5.dp).then(if (on) Modifier.background(c.link, CircleShape) else Modifier))
    }
}

// ---------- where your computer is ----------

/**
 * Replaces Android's permissions step. There is nothing to allow on iOS: what the app needs is the
 * address and password of the computer running `opencode serve`. Both are checked before they are
 * saved, and the step can be skipped: the app keeps working with whatever was configured before.
 */
@Composable
private fun ConnectStep() {
    val c = Oc.c
    val server = Graph.server
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(server.url) }
    var password by remember { mutableStateOf(server.password) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    Scroll {
        StepTitle("Where is your computer?", "cl0udly works on the computer that runs OpenCode. Put its address here, or change it later in Settings.")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OcTextField(url, { url = it; error = null; saved = false }, placeholder = "192.168.1.10:4096")
            OcTextField(password, { password = it; error = null; saved = false }, placeholder = "Password", password = true)
            error?.let { Txt(it, size = 14.sp, color = c.danger, lineHeight = 19.sp) }
            if (saved) Row(verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.Check, tint = c.ok, size = 17.dp); Spacer(Modifier.width(6.dp))
                Txt("Connected", size = 14.sp, color = c.ok)
            }
            Spacer(Modifier.height(4.dp))
            Pill(if (busy) "Checking…" else "Check connection", onClick = {
                if (busy) return@Pill
                val clean = normalizeServerUrl(url)
                if (clean.isEmpty()) { error = "Enter the address of your computer."; return@Pill }
                busy = true; error = null; saved = false
                scope.launch {
                    try {
                        Graph.api.probe(clean, password)
                        server.url = clean
                        server.password = password
                        saved = true
                    } catch (e: Exception) {
                        error = e.message?.takeIf { it.isNotBlank() } ?: "Could not connect."
                    } finally {
                        busy = false
                    }
                }
            }, Modifier.fillMaxWidth(), filled = false, enabled = !busy)
        }
        Spacer(Modifier.height(18.dp))
        Txt(
            "Run `opencode serve --hostname 0.0.0.0` on your computer, on the same network, and open port 4096.",
            Modifier.padding(horizontal = 24.dp), size = 13.sp, color = c.textDim, lineHeight = 19.sp, family = Oc.mono,
        )
    }
}

// ---------- account ----------

@Composable
private fun AccountStep(rt: RuntimeState, onPick: (String) -> Unit) {
    val c = Oc.c
    val ready = rt is RuntimeState.Running
    Scroll {
        StepTitle("Connect an account", "Free models work without one. Sign in to unlock more — keys stay on the computer.")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AccountOption("free", null, true, Ic.Sparkle, "Start free", "No account — free models right away", primary = true, onPick = onPick)
            AccountOption("github", null, true, Ic.GitHub, "Continue with GitHub", "Copilot's free models", onPick = onPick)
            AccountOption("google", null, true, Ic.Globe, "Continue with Google", "Gemini's free tier", onPick = onPick)
            AccountOption("opencode", null, true, Ic.Key, "OpenCode account", "Zen and Go plans", onPick = onPick)
        }
        if (!ready) Txt(
            "Your computer is not answering. You can pick an account now — the chat opens and connects when it does.",
            Modifier.padding(24.dp), size = 14.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 19.sp,
        )
    }
}

@Composable
private fun AccountOption(id: String, chosen: String?, ready: Boolean, icon: ImageVector, title: String, sub: String, primary: Boolean = false, onPick: (String) -> Unit) {
    val c = Oc.c
    val waiting = chosen == id && !ready
    val fg = if (primary) c.bg else c.text
    Row(
        Modifier.fillMaxWidth().height(68.dp).clip(Oc.pill)
            .then(if (primary) Modifier.background(c.text) else Modifier.background(c.surface).border(if (chosen == id) 2.dp else 1.dp, if (chosen == id) c.link else c.border, Oc.pill))
            .tap(enabled = chosen == null || chosen == id) { onPick(id) }.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = fg, size = 24.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 17.sp, color = fg, weight = FontWeight.Medium)
            Txt(if (waiting) "Waiting for the computer…" else sub, size = 13.sp, color = if (primary) c.bg.copy(alpha = 0.7f) else c.textDim)
        }
        if (waiting) Spinner(18.dp, color = fg, stroke = 2.dp)
    }
}

// ---------- tour: the mascot explains ----------

private class TourPage(val say: String, val note: String)

private val TOUR_PAGES = listOf(
    TourPage("Hi! I'm cl0udly.", "A little cloud that lives on your phone. I write code, run it and chat with you — all right here, no computer needed."),
    TourPage("Just ask.", "While I work, tap the summary line to see every step: the commands I run, the files I edit, the live terminal."),
    TourPage("Got a computer?", "Run one command on it and scan the QR code in Remote devices — then I work there too, right from this phone."),
    TourPage("I don't work alone.", "For big tasks I can call helpers and run them in parallel. And I can remember what matters to you — only if you want."),
    TourPage("Let's be friends.", "Tap me, hold me, pet me. Waiting for a task? Visit my room — I'm undefeated at ping-pong. Mostly."),
)

@Composable
private fun TourStep(i: Int) {
    val c = Oc.c
    val page = TOUR_PAGES[i]
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // a speech bubble above the mascot
        Appear(shown, 0) {
            Box(Modifier.clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Txt(page.say, size = 20.sp, weight = FontWeight.SemiBold, align = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(26.dp))
        app.ocmobile.ui.components.PixelCloud(Modifier.size(width = 190.dp, height = 130.dp), cue = i)
        Spacer(Modifier.height(30.dp))
        Appear(shown, 2) { Txt(page.note, size = 16.sp, color = c.textDim, lineHeight = 23.sp, align = TextAlign.Center) }
        Spacer(Modifier.height(22.dp))
        Appear(shown, 3) { TourPicture(i) }
    }
}

/** A small picture for the page: what it looks like in the app. */
@Composable
private fun TourPicture(i: Int) {
    val c = Oc.c
    val mono = Oc.mono
    when (i) {
        1 -> Column(Modifier.fillMaxWidth().clip(Oc.round(14.dp)).border(1.dp, c.border, Oc.round(14.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Ran 2 commands, edited ", size = 14.sp, color = c.textDim); Txt("app.py", size = 14.sp)
                Txt(" +12", size = 14.sp, color = c.ok); Txt(" −3", size = 14.sp, color = c.danger)
                Spacer(Modifier.width(4.dp)); Ico(Ic.ChevronRight, tint = c.textDim, size = 14.dp)
            }
            Txt("$ python app.py", Modifier.fillMaxWidth().clip(Oc.round(8.dp)).background(c.codeBg).padding(horizontal = 10.dp, vertical = 8.dp), size = 13.sp, color = c.link, family = mono)
        }
        2 -> Row(verticalAlignment = Alignment.CenterVertically) {
            // a pixel "QR": just a pattern
            Canvas(Modifier.size(56.dp)) {
                val n = 9; val px = size.width / n
                val bits = "111010111100101001101111011000101100011101101001001110111010100101011110110010111"
                for (y in 0 until n) for (x in 0 until n) if (bits[(y * n + x) % bits.length] == '1') drawRect(c.text, Offset(x * px, y * px), Size(px, px))
            }
            Spacer(Modifier.width(14.dp))
            Txt("Remote devices → Add device\n→ Scan QR code", size = 14.sp, color = c.textDim, lineHeight = 20.sp)
        }
        3 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TourChip(Ic.Users, "Team · 2 working")
            TourChip(Ic.Scroll, "Memory")
        }
        4 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TourChip(Ic.Sparkle, "Pet me")
            TourChip(Ic.Play, "Ping-pong")
        }
        else -> Unit
    }
}

@Composable
private fun TourChip(icon: ImageVector, text: String) {
    val c = Oc.c
    Row(Modifier.clip(CircleShape).border(1.dp, c.border, CircleShape).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ico(icon, tint = c.accent, size = 16.dp); Spacer(Modifier.width(8.dp)); Txt(text, size = 14.sp)
    }
}

// ---------- your settings ----------

@Composable
private fun SetupStep(icon: String, onIcon: (String) -> Unit) {
    val c = Oc.c
    val p = Graph.prefs
    var mode by remember { mutableStateOf(p.colorMode) }
    var haptics by remember { mutableStateOf(p.haptics) }
    var mascot by remember { mutableStateOf(p.mascotOn) }
    var persona by remember { mutableStateOf(p.mascotPersona) }
    var outfits by remember { mutableStateOf(p.mascotOutfit != "off") }
    Scroll {
        StepTitle("Make it yours", "All of this lives in Settings too — change it whenever you like.")
        Txt("Theme", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = 20.dp)) {
            Segmented(listOf("system" to "System", "dark" to "Dark", "light" to "Light"), mode, { mode = it; p.colorMode = it })
        }
        Spacer(Modifier.height(22.dp))
        Txt("Icon on your home screen", Modifier.padding(horizontal = 24.dp), size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            // iOS keeps one icon: the tiles only remember the choice and preview its colour.
            AppIcons.all.filter { it.id == "look_cloud" || it.id == "cloud_light" }.forEach { ic ->
                val sel = icon == ic.id
                Column(Modifier.clip(Oc.round(16.dp)).tap(scale = 0.95f) { onIcon(ic.id) }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(ic.bg).border(if (sel) 3.dp else 1.dp, if (sel) c.link else c.border, CircleShape), contentAlignment = Alignment.Center) {
                        MiniCloudIcon(44.dp)
                    }
                    Txt(if (ic.id == "cloud_light") "Light" else "Dark", Modifier.padding(top = 6.dp), size = 13.sp, color = if (sel) c.link else c.text)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupSwitch(Ic.Vibrate, "Vibration", "A light tap when you press things", haptics) { haptics = it; p.haptics = it }
            SetupSwitch(Ic.Cloud, "cl0udly on the home screen", "The cloud that greets you", mascot) { mascot = it; p.mascotOn = it }
            SetupSwitch(Ic.Chat, "The assistant is cl0udly", "It knows its name and where it lives; names its real model when asked", persona) { persona = it; p.mascotPersona = it }
            SetupSwitch(Ic.Sparkle, "Holiday outfits", "Hats, snow and confetti on the right days", outfits) { outfits = it; p.mascotOutfit = if (it) "auto" else "off" }
        }
    }
}

@Composable
private fun SetupSwitch(icon: ImageVector, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).tap(scale = 0.98f) { onChange(!on) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, tint = if (on) c.link else c.textDim, size = 22.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Txt(title, size = 16.sp, weight = FontWeight.Medium)
            Txt(sub, size = 13.sp, color = c.textDim, lineHeight = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        OcSwitch(on, onChange)
    }
}

// ---------- entering: "welcome in" ----------

/**
 * After "Start": the cloud hops and calls you in, then rushes closer; the screen
 * "dives" into its eye and melts away, and the home screen opens right away.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun EnterCloud(onDone: () -> Unit) {
    val c = Oc.c
    val zoom = remember { Animatable(1f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        delay(1300)
        zoom.animateTo(14f, tween(380, easing = FastOutLinearInEasing))
        onDone()
    }
    BackHandler { }
    Box(Modifier.fillMaxSize().background(c.bg).tap { }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Appear(shown, 0) {
                Box(Modifier.graphicsLayer { alpha = (2f - zoom.value).coerceIn(0f, 1f) }.clip(Oc.round(18.dp)).background(c.surface).border(1.dp, c.border, Oc.round(18.dp)).padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Txt("Come on in —\nwelcome to my dev environment!", size = 19.sp, weight = FontWeight.SemiBold, align = TextAlign.Center, lineHeight = 26.sp)
                }
            }
            Spacer(Modifier.height(28.dp))
            Box(Modifier.graphicsLayer {
                scaleX = zoom.value; scaleY = zoom.value
                // the white body fades before it fills the screen: only the eye and the background remain
                alpha = (1f - (zoom.value - 1f) / 5f).coerceIn(0f, 1f)
                // dive into the left eye
                transformOrigin = TransformOrigin(0.39f, 0.52f)
            }) {
                app.ocmobile.ui.components.PixelCloud(Modifier.size(width = 220.dp, height = 150.dp), interactive = false, cue = "enter")
            }
        }
    }
}
