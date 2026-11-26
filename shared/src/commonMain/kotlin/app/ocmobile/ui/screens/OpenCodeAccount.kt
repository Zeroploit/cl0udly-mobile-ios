package app.ocmobile.ui.screens

import app.ocmobile.ui.theme.screenBackdrop
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.platform.PlatformMedia
import app.ocmobile.ui.Nav
import app.ocmobile.ui.Route
import app.ocmobile.ui.components.BrowserCommands
import app.ocmobile.ui.components.BrowserState
import app.ocmobile.ui.components.Heading
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.PlatformBrowser
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.platformBrowser
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val CONSOLE_LOGIN = "https://opencode.ai/console/login"
private const val CONSOLE = "https://opencode.ai/console"
private val KEY_RE = Regex("sk-[A-Za-z0-9_-]{20,}")

/*
 * Connecting an OpenCode account: one API key works for both Zen (pay as you go) and Go (subscription).
 * Instead of a bare console page: a choice of method, the console in the app's own browser, and a
 * "Done" screen.
 *
 * Android drove the console with injected JavaScript that read the key off the page and handed it
 * back through a JavascriptInterface. commonMain has no such bridge, so here the browser is a plain
 * [PlatformBrowser]: the person signs in, creates the key and comes back to paste it. The clipboard
 * is read once on return, which covers the usual "tap copy and switch back" case.
 */

@Composable
fun ConsoleLoginScreen(method: String, nav: Nav) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    // choose | web | browser | paste | saving | done | error
    var stage by remember { mutableStateOf(if (method.isBlank()) "choose" else if (method == "google") "browser" else "web") }
    var via by remember { mutableStateOf(method.ifBlank { "github" }) }
    var error by remember { mutableStateOf<String?>(null) }
    var handled by remember { mutableStateOf(false) }
    val browser = remember { BrowserState() }
    val commands = remember { BrowserCommands() }
    // Android knew the console stage from its injected script; here the address is the only hint,
    // so the steps advance when the API keys page opens.
    val step = if (browser.currentUrl?.contains("key", ignoreCase = true) == true) 1 else 0

    // TODO(lead): capture the OAuth redirect on iOS with a custom scheme (ASWebAuthenticationSession
    // or an opened URL handler) so the console can hand the key back on its own, as Android did.

    fun accept(key: String) {
        if (handled) return
        handled = true; stage = "saving"
        scope.launch {
            try { connectAccount(key, via); delay(400); stage = "done" }
            catch (e: Exception) { error = e.message ?: "Could not save the key"; stage = "error"; handled = false }
        }
    }
    fun openBrowser() = PlatformMedia.openUrl(CONSOLE_LOGIN)

    Column(Modifier.fillMaxSize().screenBackdrop("OpenCodeAccount1").statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(if (stage == "choose" || stage == "done" || method.isNotBlank()) Ic.Close else Ic.Back, {
                if (stage == "choose" || stage == "done" || method.isNotBlank()) { if (!nav.pop()) nav.root(Route.Home) } else { stage = "choose"; handled = false }
            })
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { if (stage == "web" || stage == "browser") Steps(step) }
            Spacer(Modifier.width(48.dp))
        }
        AnimatedContent(stage, Modifier.weight(1f), label = "acct", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { s ->
            when (s) {
                "choose" -> Choose(
                    onMethod = { m -> via = m; stage = if (m == "google") "browser".also { openBrowser() } else "web" },
                    onPaste = { stage = "paste" },
                )
                "web" -> Box(Modifier.fillMaxSize()) {
                    if (platformBrowser) {
                        PlatformBrowser(CONSOLE_LOGIN, browser, commands, Modifier.fillMaxSize())
                    } else {
                        Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Heading("Sign in to OpenCode", size = 26.sp)
                            Spacer(Modifier.height(10.dp))
                            Txt("This platform has no browser inside the app. Sign in on opencode.ai/console, create a key and paste it here.", size = 16.sp, color = c.textDim, align = TextAlign.Center, lineHeight = 23.sp)
                            Spacer(Modifier.height(20.dp))
                            BigButton("Open the console", filled = false) { openBrowser() }
                        }
                    }
                    // hint at the bottom: what to do in the page
                    Column(
                        Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(14.dp).fillMaxWidth().clip(Oc.round(22.dp)).background(c.surfaceHigh)
                            .border(1.dp, c.border, Oc.round(22.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spinner(18.dp, stroke = 2.dp); Spacer(Modifier.width(12.dp))
                            Txt(
                                "Sign in, open API Keys and create a key, then come back here.",
                                Modifier.weight(1f), size = 14.sp, lineHeight = 20.sp,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        BigButton("I finished signing in") { stage = "paste" }
                    }
                }
                "browser" -> Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.Center) {
                    Heading("Finish in your browser", size = 28.sp)
                    Spacer(Modifier.height(10.dp))
                    Txt("Google doesn't allow signing in inside apps, so this one takes a short detour.", size = 16.sp, color = c.textDim, lineHeight = 23.sp)
                    Spacer(Modifier.height(26.dp))
                    listOf("Sign in with Google", "Open API Keys and create a key", "Come back and paste the key").forEachIndexed { i, t ->
                        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background(c.surfaceHigh), contentAlignment = Alignment.Center) { Txt("${i + 1}", size = 14.sp, weight = FontWeight.Medium) }
                            Spacer(Modifier.width(14.dp)); Txt(t, size = 16.sp)
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                    BigButton("Open the browser again", filled = false) { openBrowser() }
                    Spacer(Modifier.height(10.dp))
                    BigButton("I have the key") { stage = "paste" }
                }
                "paste" -> PasteKey { accept(it) }
                "saving" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Spinner(40.dp); Spacer(Modifier.height(20.dp)); Txt("Connecting Zen and Go…", size = 18.sp, color = c.textDim)
                }
                "done" -> Done { nav.root(Route.Home) }
                else -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Txt(error ?: "Something went wrong", size = 17.sp, color = c.danger, align = TextAlign.Center, lineHeight = 24.sp)
                    Spacer(Modifier.height(20.dp))
                    BigButton("Try again", filled = false) { stage = if (method.isBlank()) "choose" else "web" }
                }
            }
        }
    }
}

/** Steps at the top of the console: sign in → key → done. */
@Composable
private fun Steps(at: Int) {
    val c = Oc.c
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("Sign in", "API key", "Done").forEachIndexed { i, t ->
            if (i > 0) Box(Modifier.padding(horizontal = 8.dp).width(14.dp).height(1.dp).background(c.border))
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (i <= at) c.text else c.border))
            Spacer(Modifier.width(6.dp))
            Txt(t, size = 13.sp, color = if (i <= at) c.text else c.textDim)
        }
    }
}

/** The OpenCode mark: Android used a desktop drawable, which commonMain has no access to. */
@Composable
private fun Mark(size: androidx.compose.ui.unit.Dp) = Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF1E1E1E)), contentAlignment = Alignment.Center) {
    Ico(Ic.Cloud, tint = Color.White, size = size * 0.6f)
}

@Composable
private fun Choose(onMethod: (String) -> Unit, onPaste: () -> Unit) {
    val c = Oc.c
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 8.dp)) {
        Mark(64.dp)
        Spacer(Modifier.height(20.dp))
        Heading("Connect OpenCode", size = 30.sp)
        Spacer(Modifier.height(8.dp))
        Txt("One account, one key — it unlocks both plans. You pay OpenCode directly; the key stays on this phone.", size = 16.sp, color = c.textDim, lineHeight = 23.sp)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Plan(Modifier.weight(1f), "Zen", "Pay as you go", "Hand-picked top models, checked to work well with the agent.", Ic.Sparkle)
            Plan(Modifier.weight(1f), "Go", "\$10 / month", "Generous limits on strong open models for everyday coding.", Ic.Zap)
        }
        Spacer(Modifier.height(26.dp))
        Txt("Sign in with", size = 14.sp, color = c.textDim)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Method(Ic.GitHub, "GitHub", "Fastest — done inside the app") { onMethod("github") }
            Method(Ic.Mail, "Email", "A code arrives in your inbox") { onMethod("email") }
            Method(Ic.Globe, "Google", "Opens your browser for a moment") { onMethod("google") }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().clip(Oc.pill).tap(scale = 0.98f, onClick = onPaste).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Ico(Ic.Key, tint = c.textDim, size = 18.dp); Spacer(Modifier.width(8.dp))
            Txt("I already have an API key", size = 15.sp, color = c.textDim)
        }
    }
}

@Composable
private fun Plan(modifier: Modifier, name: String, price: String, text: String, icon: ImageVector) {
    val c = Oc.c
    Column(modifier.clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ico(icon, tint = c.accent, size = 18.dp); Spacer(Modifier.width(8.dp))
            Txt(name, size = 18.sp, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Txt(price, size = 14.sp, color = c.accent)
        Spacer(Modifier.height(8.dp))
        Txt(text, size = 13.sp, color = c.textDim, lineHeight = 18.sp)
    }
}

@Composable
private fun Method(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    val c = Oc.c
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(Oc.pill).background(c.surface).border(1.dp, c.border, Oc.pill).tap(scale = 0.98f, onClick = onClick).padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(icon, size = 24.dp); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Txt(title, size = 17.sp, weight = FontWeight.Medium); Txt(sub, size = 13.sp, color = c.textDim) }
        Ico(Ic.ChevronRight, tint = c.textDim, size = 18.dp)
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean = true, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Oc.c
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(Oc.pill).then(if (filled) Modifier.background(if (enabled) c.text else c.surfaceHigh) else Modifier.border(1.dp, c.border, Oc.pill))
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 16.sp, color = if (filled) (if (enabled) c.bg else c.textDim) else c.text, weight = FontWeight.Medium) }
}

@Composable
private fun PasteKey(onKey: (String) -> Unit) {
    val c = Oc.c
    val clip = LocalClipboardManager.current
    var key by remember { mutableStateOf("") }
    val valid = KEY_RE.matches(key.trim())
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Heading("Paste your key", size = 28.sp)
        Spacer(Modifier.height(8.dp))
        Txt("Find it in the OpenCode console under API Keys. It starts with “sk-”.", size = 16.sp, color = c.textDim, lineHeight = 23.sp)
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.fillMaxWidth().clip(Oc.round(16.dp)).background(c.surface).border(1.dp, if (key.isNotBlank() && !valid) c.danger else c.border, Oc.round(16.dp)).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 16.dp)) {
                if (key.isEmpty()) Txt("sk-…", size = 16.sp, color = c.textDim, family = Oc.mono)
                BasicTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), singleLine = true, textStyle = TextStyle(color = c.text, fontSize = 15.sp, fontFamily = Oc.mono), cursorBrush = SolidColor(c.text))
            }
            Box(Modifier.clip(Oc.pill).background(c.surfaceHigh).tap {
                KEY_RE.find(clip.getText()?.text.orEmpty())?.let { key = it.value }
            }.padding(horizontal = 14.dp, vertical = 9.dp)) { Txt("Paste", size = 14.sp) }
        }
        if (key.isNotBlank() && !valid) Txt("That doesn't look like an OpenCode key.", Modifier.padding(top = 8.dp), size = 13.sp, color = c.danger)
        Spacer(Modifier.weight(1f))
        BigButton("Connect", enabled = valid) { onKey(key.trim()) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(Oc.pill).tap(scale = 0.98f) {
            PlatformMedia.openUrl(CONSOLE)
        }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center) { Txt("Open the console", size = 15.sp, color = c.textDim) }
        Spacer(Modifier.height(8.dp))
    }
}

/** Done: the checkmark "grows", below it what's available now. */
@Composable
private fun Done(onStart: () -> Unit) {
    val c = Oc.c
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) }
    val providers = Graph.catalog.providers.value
    val zen = providers.firstOrNull { it.id == "opencode" }?.models?.size ?: 0
    val go = providers.firstOrNull { it.id == "opencode-go" }?.models?.size ?: 0
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Spacer(Modifier.weight(0.6f))
        Box(Modifier.size(84.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(c.ok), contentAlignment = Alignment.Center) {
            Ico(Ic.Check, tint = c.bg, size = 44.dp)
        }
        Spacer(Modifier.height(24.dp))
        Heading("You're connected", size = 30.sp)
        Spacer(Modifier.height(8.dp))
        Txt("Your OpenCode key is saved on this phone.", size = 16.sp, color = c.textDim)
        Spacer(Modifier.height(22.dp))
        Column(Modifier.fillMaxWidth().clip(Oc.round(20.dp)).background(c.surface).border(1.dp, c.border, Oc.round(20.dp))) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.Sparkle, tint = c.accent, size = 20.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Txt("Zen", size = 16.sp, weight = FontWeight.Medium); Txt(if (zen > 0) "$zen models · pay as you go" else "Pay as you go", size = 13.sp, color = c.textDim) }
                Ico(Ic.Check, tint = c.ok, size = 18.dp)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Ico(Ic.Zap, tint = c.accent, size = 20.dp); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Txt("Go", size = 16.sp, weight = FontWeight.Medium); Txt(if (go > 0) "$go models · with the Go plan" else "Works with the Go plan", size = 13.sp, color = c.textDim) }
                Txt("Plan", Modifier.clip(Oc.pill).tap { PlatformMedia.openUrl("https://opencode.ai/go") }.padding(horizontal = 10.dp, vertical = 6.dp), size = 14.sp, color = c.accent)
            }
        }
        Spacer(Modifier.height(10.dp))
        Txt("Usage limits for Go show up in the circle at the top of a chat.", size = 13.sp, color = c.textDim, lineHeight = 19.sp)
        Spacer(Modifier.weight(1f))
        BigButton("Start chatting", onClick = onStart)
        Spacer(Modifier.height(12.dp))
    }
}
