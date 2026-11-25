package app.ocmobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.FileViewerHost
import app.ocmobile.ui.components.Spinner
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.trackTouches
import app.ocmobile.ui.screens.ChatScreen
import app.ocmobile.ui.screens.DrawerContent
import app.ocmobile.ui.screens.HomeScreen
import app.ocmobile.ui.screens.MemoryScreen
import app.ocmobile.ui.screens.PreviewScreen
import app.ocmobile.ui.screens.TeamScreen
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DRAWER_WIDTH = 0.94f
private const val DRAWER_SHRINK = 0.08f
private const val DRAWER_CORNER = 28f

/**
 * The app shell: side menu, screen transitions, swipe-back, toasts.
 *
 * Ported from the Android `Root.kt`. The differences are what iOS cannot do:
 * there is no local server (no RuntimeState, no SetupScreen), no welcome tour
 * and no system back button — instead the edge swipe goes back and on the home
 * screens it opens the menu.
 */
@Composable
fun OcRoot() {
    val c = Oc.c
    Box(Modifier.fillMaxSize().background(c.bg).then(Modifier.trackTouches())) {
        Shell()
        FileViewerHost()
        ToastHost()
        UndoHost()
    }
}

@Composable
private fun Shell() {
    val c = Oc.c
    val nav = remember { NavMemory.nav }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val open = { scope.launch { drawer.open() }; Unit }

    // How far the drawer is out, 0..1: the screen behind follows it to the right and shrinks a little.
    var screenW by remember { mutableFloatStateOf(0f) }
    val sheetW = screenW * DRAWER_WIDTH
    fun drawerProgress(): Float {
        val off = drawer.currentOffset
        return if (sheetW <= 0f || off.isNaN()) (if (drawer.isOpen) 1f else 0f) else (1f + off / sheetW).coerceIn(0f, 1f)
    }
    // the keyboard goes away as soon as the drawer starts to come out, not after it's open
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(drawer, sheetW) {
        snapshotFlow { drawerProgress() > 0.02f }.collect { out -> if (out) { keyboard?.hide(); focus.clearFocus(force = true) } }
    }
    // the mascot on the home screen watches the menu come out (rounded so it doesn't redraw every pixel)
    LaunchedEffect(drawer, sheetW) {
        snapshotFlow { (drawerProgress() * 20).toInt() / 20f }.collect { app.ocmobile.ui.components.MascotSignals.drawer = it }
    }
    // the server connection: catalog and sessions reload once the stream is up
    val connected by Graph.events.connected.collectAsState()
    LaunchedEffect(connected) { if (connected) { Graph.catalog.refresh(); Graph.chats.refreshSessions() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        // the swipe opens the drawer only on the home screen and in a chat (elsewhere it would fight with
        // scrolling, pages and back); while a finger is on the mascot it doesn't either: it's being petted and pulled
        gesturesEnabled = drawer.isOpen || (drawerSwipe(nav.current) && !app.ocmobile.ui.components.MascotGrab.held),
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = c.bg, drawerShape = RectangleShape, windowInsets = WindowInsets(0), modifier = Modifier.fillMaxWidth(DRAWER_WIDTH)) {
                DrawerContent(nav) { scope.launch { drawer.close() } }
            }
        },
    ) {
        Box(
            Modifier.fillMaxSize().onSizeChanged { screenW = it.width.toFloat() }.graphicsLayer {
                val p = drawerProgress()
                if (p > 0f) {
                    // pushed right next to the drawer, shrunk from the left edge, with rounded corners
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationX = sheetW * p
                    scaleX = 1f - DRAWER_SHRINK * p; scaleY = scaleX
                    shape = RoundedCornerShape((DRAWER_CORNER * p).dp)
                    clip = true
                }
            },
        ) {
            // back by swiping from the left edge. Only on screens with a back arrow: on home and in
            // chats a swipe from the edge opens the drawer.
            val back = remember { androidx.compose.animation.core.Animatable(0f) }
            val prev = nav.previous
            val swipeable = nav.stack.size > 1 && nav.current !is Route.Chat && !drawer.isOpen
            if (back.value > 0f && prev != null) {
                val k = (back.value / screenW.coerceAtLeast(1f)).coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxSize()
                        .graphicsLayer { translationX = -screenW * 0.25f * (1f - k) }
                        .drawWithContent { drawContent(); drawRect(Color.Black.copy(alpha = 0.22f * (1f - k))) },
                ) { ScreenFrame { RouteContent(prev, nav, open) } }
            }
            AnimatedContent(
                modifier = Modifier.graphicsLayer { translationX = back.value },
                targetState = nav.current,
                transitionSpec = {
                    when {
                        nav.direction == 3 -> androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
                        nav.direction == 2 -> fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        // fast: the screen slides in within ~200 ms with a sharp start and a soft stop;
                        // the one underneath moves a quarter of the way (parallax) and dims
                        nav.direction > 0 -> (slideInHorizontally(tween(210, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) { it / 5 } + fadeIn(tween(150))) togetherWith
                            (slideOutHorizontally(tween(210)) { -it / 4 } + fadeOut(tween(160, delayMillis = 50)))
                        nav.direction < 0 -> (slideInHorizontally(tween(200, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) { -it / 4 } + fadeIn(tween(150))) togetherWith
                            (slideOutHorizontally(tween(200)) { it / 5 } + fadeOut(tween(110)))
                        // from the side menu (chat → settings and back): the new screen rises a little out of
                        // the old one as it fades in, the old one fades and grows slightly away
                        else -> (fadeIn(tween(160)) + androidx.compose.animation.scaleIn(tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing), initialScale = 0.95f) +
                            androidx.compose.animation.slideInVertically(tween(200, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it / 30 }) togetherWith
                            (fadeOut(tween(110)) + androidx.compose.animation.scaleOut(tween(160), targetScale = 1.03f))
                    }
                },
                label = "nav",
            ) { r ->
                // the screen underneath shifts a quarter and dims a little, like a stack of cards
                val dim by transition.animateFloat(transitionSpec = { tween(210) }, label = "dim") { st ->
                    when {
                        st == EnterExitState.PostExit && nav.direction == 1 -> 0.22f
                        st == EnterExitState.PreEnter && nav.direction == -1 -> 0.22f
                        else -> 0f
                    }
                }
                Box(Modifier.fillMaxSize().drawWithContent { drawContent(); if (dim > 0f) drawRect(Color.Black.copy(alpha = dim)) }) {
                    ScreenFrame { RouteContent(r, nav, open) }
                }
            }
            if (swipeable) Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(20.dp).pointerInput(nav.current) {
                    val vt = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { vt.resetTracking() },
                        onDragEnd = {
                            val v = vt.calculateVelocity().x
                            scope.launch {
                                if (back.value > screenW * 0.35f || v > 1200f) {
                                    back.animateTo(screenW, tween(160, easing = androidx.compose.animation.core.LinearOutSlowInEasing))
                                    nav.popAfterSwipe()
                                    back.snapTo(0f)
                                } else back.animateTo(0f, androidx.compose.animation.core.spring(0.85f, androidx.compose.animation.core.Spring.StiffnessMedium))
                            }
                        },
                        onDragCancel = { scope.launch { back.animateTo(0f, tween(160)) } },
                    ) { change, dx ->
                        change.consume()
                        vt.addPosition(change.uptimeMillis, change.position)
                        scope.launch { back.snapTo((back.value + dx).coerceIn(0f, screenW)) }
                    }
                },
            )
            val cur = nav.current
            if (cur == Route.Home || cur == Route.NewCode || cur == Route.Code || cur is Route.Chat) ServerBanner()
        }
    }
}

/** A screen on its background, no wider than 720 dp on tablets and in landscape (phones are unchanged). */
@Composable
private fun ScreenFrame(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Oc.c.bg), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 720.dp).fillMaxSize()) { content() }
    }
}

/** Screens where a swipe from the left pulls out the side menu. */
private fun drawerSwipe(r: Route) = r == Route.Home || r == Route.NewCode || r is Route.Chat

@Composable
private fun RouteContent(r: Route, nav: Nav, open: () -> Unit) {
    when (r) {
        Route.Home -> HomeScreen(nav, open)
        Route.NewCode -> HomeScreen(nav, open, code = true)
        is Route.Chat -> ChatScreen(r.id, nav, open)
        Route.Team -> TeamScreen(nav)
        Route.Memory -> MemoryScreen(nav)
        is Route.Preview -> PreviewScreen(r.url, r.sessionId, nav)
        // the rest of the sections exist on Android only for now
        else -> IosSoon(nav)
    }
}

/** The not-ported-yet screen: same frame as the real sections, one line of text. */
@Composable
private fun IosSoon(nav: Nav) {
    val c = Oc.c
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        app.ocmobile.ui.components.TopBar(
            title = nav.current.toString(),
            left = { app.ocmobile.ui.components.IconBtn(Ic.Back, onClick = { nav.pop() }) },
        )
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt("This section is on the way for iOS.", size = 15.sp, color = c.textDim)
        }
    }
}

@Composable
private fun ServerBanner() {
    val c = Oc.c
    val connected by Graph.events.connected.collectAsState()
    // the event stream dropped: say so only if it doesn't come back at once
    var lost by remember { mutableStateOf(false) }
    LaunchedEffect(connected) { lost = false; if (!connected) { delay(2500); lost = true } }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 60.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(lost || !connected) {
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = 16.dp).clip(Oc.pill).background(c.surfaceHigh).border(1.dp, c.border, Oc.pill)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spinner(16.dp, stroke = 2.dp)
                Spacer(Modifier.width(10.dp))
                Ico(Ic.Globe, tint = c.textDim, size = 16.dp)
                Spacer(Modifier.width(10.dp))
                Txt(if (lost) "Reconnecting to your computer…" else "Connecting to your computer…", size = 14.sp, color = c.textDim)
            }
        }
    }
}

/** "Chat deleted · Undo" at the bottom for a few seconds after a delete. */
@Composable
private fun UndoHost() {
    val c = Oc.c
    val u by Graph.chats.undo.collectAsState()
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(u != null, enter = androidx.compose.animation.fadeIn(tween(120)) + androidx.compose.animation.slideInVertically(tween(160)) { it / 2 },
            exit = androidx.compose.animation.fadeOut(tween(100))) {
            var last by remember { mutableStateOf(u) }
            if (u != null) last = u
            androidx.compose.foundation.layout.Row(
                Modifier.padding(horizontal = 24.dp).clip(Oc.round(20.dp)).background(c.text).padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Txt(last?.text.orEmpty(), size = 15.sp, color = c.bg)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.clip(Oc.round(14.dp)).clickable { last?.undo?.invoke(); Graph.chats.undo.value = null }.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Txt("Undo", size = 15.sp, color = c.bg, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ToastHost() {
    val c = Oc.c
    var msg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Graph.chats.toasts.collect { msg = it; delay(3500); msg = null }
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 120.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(msg != null) {
            Box(Modifier.padding(horizontal = 24.dp).clip(Oc.round(20.dp)).background(c.text).padding(horizontal = 18.dp, vertical = 12.dp)) {
                Txt(msg.orEmpty(), size = 15.sp, color = c.bg, maxLines = 4)
            }
        }
    }
}
