package app.ocmobile.ui.screens

import app.ocmobile.ui.theme.screenBackdrop
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ocmobile.Graph
import app.ocmobile.data.CHAT_DIR
import app.ocmobile.ui.components.Ico
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.components.tap
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private enum class Talk { Idle, Listening, Thinking, Speaking }

/**
 * Voice mode, like in Claude: a conversation screen. Listen, send to the chat,
 * wait for the agent's reply and read it aloud, then listen again.
 * [sessionId] = null: a new chat is created with the first phrase; [onClose] gets the chat id if one appeared.
 *
 * Common code has no speech recognition or text-to-speech yet, so listening and speaking are
 * quiet no-ops: the ring, the state machine and all the controls stay as they are.
 */
@Composable
fun VoiceMode(sessionId: String?, forceCloud: Boolean = false, onClose: (String?) -> Unit) {
    val c = Oc.c
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf(sessionId) }
    var talk by remember { mutableStateOf(Talk.Idle) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(true) }
    var caption by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    // in the cloud look the conversation is with the mascot: it sits in the ring and speaks a little higher
    val cloud = Oc.look.id == "cloud" || forceCloud

    var listenNext by remember { mutableStateOf<() -> Unit>({}) }

    fun speak(text: String) {
        // no platform voice here: settle back and listen again when one lands
        if (!speaker || text.isBlank()) { talk = Talk.Idle; listenNext(); return }
        talk = Talk.Idle
        listenNext()
    }

    /** Send a phrase and wait for the agent's reply. */
    fun ask(text: String) {
        talk = Talk.Thinking; caption = text
        scope.launch {
            try {
                val id = session ?: Graph.chats.createSession(CHAT_DIR).id.also { session = it }
                val chat = Graph.chats.chat(id)
                if (chat.directory == null) chat.directory = Graph.chats.session(id)?.directory
                runCatching { chat.load() }
                val before = chat.messages.size
                Graph.chats.send(id, text)
                // wait for the agent to start and finish (the session status disappears when it's idle)
                withTimeoutOrNull(15_000) { Graph.chats.statuses.first { it[id] != null } }
                withTimeoutOrNull(10 * 60_000) { Graph.chats.statuses.first { it[id] == null } }
                delay(300)
                val reply = chat.messages.drop(before).lastOrNull { !it.info.isUser }
                val answer = reply?.let { assistantText(it) }.orEmpty().ifBlank { reply?.info?.errorMessage.orEmpty() }
                caption = answer
                speak(answer)
            } catch (e: Exception) {
                caption = e.message ?: "Something went wrong"
                talk = Talk.Idle
            }
        }
    }

    fun listen() {
        // no speech recognition on this platform: the button stays, listening is a no-op
        if (muted) { talk = Talk.Idle; return }
        talk = Talk.Listening; caption = ""
    }
    listenNext = { listen() }

    fun close() { onClose(session) }

    Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().screenBackdrop("VoiceMode1")) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    VoiceRing(talk, level, Modifier.fillMaxSize().tap(scale = 0.97f) {
                        when (talk) {
                            Talk.Speaking -> { talk = Talk.Idle; listen() } // interrupt, like in Claude
                            Talk.Idle -> listen()
                            Talk.Listening -> {}
                            Talk.Thinking -> {}
                        }
                    })
                    // the cloud talks with you: it watches while you speak, thinks while the agent works, smiles while it answers
                    if (cloud) app.ocmobile.ui.components.PixelCloud(
                        Modifier.size(width = 104.dp, height = 72.dp), thinking = talk == Talk.Thinking, interactive = false,
                        watching = talk == Talk.Listening, cue = talk,
                    )
                }
                Spacer(Modifier.height(28.dp))
                AnimatedContent(talk, label = "talk", transitionSpec = { fadeIn(tween(120)) togetherWith fadeOut(tween(80)) }) { t ->
                    Txt(
                        when (t) {
                            Talk.Idle -> if (session == null) "Let's talk" + Graph.prefs.userName.takeIf { it.isNotBlank() }?.let { ", $it" }.orEmpty() else "Tap to talk"
                            Talk.Listening -> "Listening…"
                            Talk.Thinking -> "Thinking…"
                            Talk.Speaking -> "Tap to interrupt"
                        },
                        size = 24.sp, family = Oc.heading,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Box(Modifier.height(96.dp).padding(horizontal = 32.dp), contentAlignment = Alignment.TopCenter) {
                    Txt(caption.takeLast(240), size = 16.sp, color = c.textDim, lineHeight = 22.sp, maxLines = 4, align = TextAlign.Center)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundBtn(if (speaker) Ic.Volume else Ic.VolumeOff, false) { speaker = !speaker }
                    Spacer(Modifier.weight(1f))
                    // microphone: off/on
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(if (muted) c.danger.copy(alpha = .18f) else c.surfaceHigh).tap {
                            muted = !muted
                            if (muted) { if (talk == Talk.Listening) talk = Talk.Idle } else if (talk == Talk.Idle) listen()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Ico(if (muted) Ic.MicOff else Ic.Mic, tint = if (muted) c.danger else c.text, size = 26.dp) }
                    Spacer(Modifier.weight(1f))
                    RoundBtn(Ic.Close, true) { close() }
                }
            }
        }
    }
}

@Composable
private fun RoundBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, onClick: () -> Unit) {
    val c = Oc.c
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(if (filled) c.text else c.surfaceHigh).border(1.dp, if (filled) c.text else c.border, CircleShape).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = if (filled) c.bg else c.text, size = 22.dp) }
}

/**
 * A ring of rays, no glow, only lines. Listening: rays stretch with the voice volume;
 * thinking: a light wave runs around; speaking: the ring gently "breathes" in waves.
 */
@Composable
private fun VoiceRing(talk: Talk, level: Float, modifier: Modifier) {
    val c = Oc.c
    val t = rememberInfiniteTransition(label = "ring")
    val ph by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "ph")
    val lv by animateFloatAsState(level, tween(90), label = "lv")
    val accent = if (Oc.edition == "claude" || Oc.edition == "notebook") c.accent else c.link
    Canvas(modifier) {
        val n = 56
        val cx = size.width / 2; val cy = size.height / 2
        val r0 = size.minDimension * 0.26f
        val maxLen = size.minDimension * 0.22f
        for (i in 0 until n) {
            val a = i * 2 * PI / n
            val wave = sin(a * 3 + ph * 2 * PI).toFloat()
            val k = when (talk) {
                Talk.Idle -> 0.18f + 0.04f * wave
                Talk.Listening -> 0.16f + lv * (0.55f + 0.45f * ((sin(a * 5 - ph * 4 * PI).toFloat() + 1f) / 2f))
                Talk.Thinking -> 0.14f + 0.3f * ((cos(a - ph * 2 * PI).toFloat() + 1f) / 2f).let { it * it * it }
                Talk.Speaking -> 0.22f + 0.3f * ((sin(a * 4 + ph * 6 * PI).toFloat() + 1f) / 2f) * ((sin(ph * 10 * PI).toFloat() + 1.4f) / 2.4f)
            }
            val len = maxLen * k
            val from = Offset(cx + cos(a).toFloat() * r0, cy + sin(a).toFloat() * r0)
            val to = Offset(cx + cos(a).toFloat() * (r0 + len), cy + sin(a).toFloat() * (r0 + len))
            val col = when (talk) {
                Talk.Thinking -> accent.copy(alpha = 0.35f + 0.65f * ((cos(a - ph * 2 * PI).toFloat() + 1f) / 2f))
                Talk.Idle -> c.textDim
                else -> accent
            }
            drawLine(col, from, to, size.minDimension * 0.014f, StrokeCap.Round)
        }
    }
}
