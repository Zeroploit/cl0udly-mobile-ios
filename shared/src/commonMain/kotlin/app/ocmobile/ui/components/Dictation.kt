package app.ocmobile.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.platform.nowMillis
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import kotlinx.coroutines.delay

/**
 * Dictation right into the input, like in Claude: instead of the system window, a recording bar with a live wave;
 * the text appears as you speak. The recognizer stops by itself after a pause, so we restart it
 * until the user taps "done" or "send".
 *
 * This platform has no speech engine: the recording bar, the wave and the controls are all here,
 * but nothing is recognized — [start] opens the bar and [text] stays empty.
 */
class Dictation() {
    var active by mutableStateOf(false)
        private set
    /** Already recognized text and the current chunk. */
    var done by mutableStateOf("")
        private set
    var partial by mutableStateOf("")
        private set
    val levels = mutableStateListOf<Float>().apply { repeat(BARS) { add(0f) } }
    var startedAt by mutableLongStateOf(0L)
        private set
    private var lastPush = 0L

    val text get() = listOf(done, partial).filter { it.isNotBlank() }.joinToString(" ")

    fun start() {
        if (active) return
        done = ""; partial = ""; active = true; startedAt = nowMillis()
        for (i in levels.indices) levels[i] = 0f
        // no recognizer to create here: the bar runs, but no text or level ever arrives
    }

    /** Russian ↔ English on the fly: what was said so far stays, the recognizer starts over in the other one. */
    fun switchLang() {
        Graph.prefs.speechLang = if (speechTag().startsWith("en")) "ru-RU" else "en-US"
        if (partial.isNotBlank()) { done = listOf(done, partial).filter { it.isNotBlank() }.joinToString(" "); partial = "" }
    }

    /** Stop and return everything recognized. */
    fun finish(): String { val t = text; stop(); return t }

    fun stop() {
        active = false
    }

    /** Level push from the recognizer's loudness, kept for the real engine that may come back. */
    private fun push(v: Float) {
        val now = nowMillis()
        if (now - lastPush < 55) { levels[levels.lastIndex] = maxOf(levels.last(), v); return }
        lastPush = now
        levels.removeAt(0); levels.add(v)
    }

    companion object { const val BARS = 36 }
}

/** The language being listened for: the one picked in Settings, "en-US" while unset ("auto" needs a recognizer). */
private fun speechTag(): String = Graph.prefs.speechLang.ifBlank { "en-US" }

@Composable
fun rememberDictation(): Pair<Dictation, () -> Unit> {
    val d = remember { Dictation() }
    DisposableEffect(Unit) { onDispose { d.stop() } }
    // no RECORD_AUDIO permission flow here: there is nothing to record with, so starting is direct
    return d to { d.start() }
}

/** Recording bar: cancel, wave, time, "insert" and "send". */
@Composable
fun DictationBar(d: Dictation, onCancel: () -> Unit, onDone: () -> Unit, onSend: () -> Unit) {
    val c = Oc.c
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(d.active) { while (d.active) { now = nowMillis(); delay(250) } }
    val secs = ((now - d.startedAt) / 1000).coerceAtLeast(0)
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBtn(Ic.Close, onCancel, size = 40.dp, bg = c.surfaceHigh, tint = c.textDim)
        Spacer(Modifier.width(12.dp))
        val bar = c.text
        Canvas(Modifier.weight(1f).height(32.dp)) {
            val n = d.levels.size
            val step = size.width / n
            val w = (step * 0.5f).coerceAtMost(4.dp.toPx())
            d.levels.forEachIndexed { i, v ->
                val h = (size.height * v).coerceAtLeast(w)
                drawRoundRect(bar.copy(alpha = 0.35f + 0.65f * (i.toFloat() / n)), Offset(i * step + (step - w) / 2, (size.height - h) / 2), Size(w, h), CornerRadius(w / 2))
            }
        }
        Spacer(Modifier.width(10.dp))
        // the language being listened for; a tap switches it
        Box(Modifier.clip(Oc.pill).background(c.surfaceHigh).tap(onClick = { d.switchLang() }).padding(horizontal = 9.dp, vertical = 5.dp)) {
            Txt(speechTag().take(2).uppercase(), size = 12.sp, color = c.text, family = Oc.mono)
        }
        Spacer(Modifier.width(8.dp))
        Txt("%d:%02d".format(secs / 60, secs % 60), size = 14.sp, color = c.textDim, family = Oc.mono)
        Spacer(Modifier.width(10.dp))
        IconBtn(Ic.Check, onDone, size = 40.dp, bg = c.surfaceHigh)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp).clip(Oc.pill).background(c.text).tap(onClick = onSend), contentAlignment = Alignment.Center) {
            Ico(Ic.ArrowUp, tint = c.bg, size = 20.dp)
        }
    }
}
