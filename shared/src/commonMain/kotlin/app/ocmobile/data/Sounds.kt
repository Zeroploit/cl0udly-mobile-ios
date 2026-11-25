package app.ocmobile.data

import app.ocmobile.Graph
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The app's own little sounds: soft sine notes made right here (no files), quiet and short, like a
 * glass being touched. The same notes play in cl0udly on the computer.
 *
 * Ported from the Android `Sounds.kt`. The synthesis is plain math and stays identical; playback
 * needs a platform audio track, which commonMain has no API for, so [play] is a no-op until the
 * iOS audio host is wired up. Callers are unchanged, so sound starts working the moment that lands.
 */
object Sounds {
    enum class S { Send, Done, Error, Pop, Pet, Tap }

    private const val RATE = 44100

    /** Notes as (hertz, start in s, length in s, loudness). */
    private fun notes(s: S): List<FloatArray> = when (s) {
        S.Send -> listOf(floatArrayOf(660f, 0f, 0.09f, 0.5f), floatArrayOf(990f, 0.045f, 0.12f, 0.4f))
        S.Done -> listOf(floatArrayOf(784f, 0f, 0.35f, 0.45f), floatArrayOf(1175f, 0.09f, 0.45f, 0.35f))
        S.Error -> listOf(floatArrayOf(330f, 0f, 0.18f, 0.5f), floatArrayOf(247f, 0.1f, 0.28f, 0.45f))
        S.Pop -> listOf(floatArrayOf(1320f, 0f, 0.07f, 0.35f), floatArrayOf(1760f, 0.03f, 0.1f, 0.25f))
        S.Pet -> listOf(floatArrayOf(523f, 0f, 0.14f, 0.4f), floatArrayOf(659f, 0.07f, 0.14f, 0.35f), floatArrayOf(784f, 0.14f, 0.22f, 0.3f))
        S.Tap -> listOf(floatArrayOf(880f, 0f, 0.06f, 0.35f))
    }

    /** 16-bit mono PCM; also used by the desktop copy, which is why it's plain math. */
    fun pcm(s: S, volume: Float = 0.22f): ShortArray {
        val ns = notes(s)
        val len = ((ns.maxOf { it[1] + it[2] } + 0.02f) * RATE).toInt()
        val out = FloatArray(len)
        for ((hz, at, dur, amp) in ns.map { listOf(it[0], it[1], it[2], it[3]) }) {
            val from = (at * RATE).toInt(); val n = (dur * RATE).toInt()
            for (i in 0 until n) {
                val t = i.toFloat() / RATE
                // 4 ms fade-in so it never clicks, then a bell-like fall
                val env = (t / 0.004f).coerceAtMost(1f) * exp(-t * 5f / dur)
                val v = sin(2 * PI * hz * t).toFloat() + 0.18f * sin(4 * PI * hz * t).toFloat()
                if (from + i < len) out[from + i] += v * env * amp
            }
        }
        return ShortArray(len) { (out[it] * volume * 32767).coerceIn(-32767f, 32767f).toInt().toShort() }
    }

    private val cache = HashMap<S, ShortArray>()
    private var last = 0L

    fun play(s: S) {
        if (!Graph.isPrefsReady() || !Graph.prefs.sounds) return
        // two sounds in the same instant would only clash
        val now = app.ocmobile.platform.nowMillis()
        if (now - last < 60) return
        last = now
        // No audio track in commonMain yet: the notes are synthesised above, playback waits for the
        // iOS audio host. Nothing else changes for the callers.
    }
}
