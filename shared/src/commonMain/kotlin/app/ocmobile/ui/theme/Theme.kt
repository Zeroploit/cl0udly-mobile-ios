package app.ocmobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import app.ocmobile.data.Prefs
import app.ocmobile.platform.rememberMotionEnabled
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import app.ocmobile.resources.Res
import app.ocmobile.resources.inter_medium
import app.ocmobile.resources.inter_regular
import app.ocmobile.resources.inter_semibold
import app.ocmobile.resources.jetbrains_mono
import app.ocmobile.resources.source_serif_display
import app.ocmobile.resources.source_serif_regular
import app.ocmobile.resources.source_serif_semibold

/**
 * The app's palette and typography, ported from the Android theme.
 *
 * The values are the ones in `Looks.kt`: two complete palettes (the cl0udly
 * night sky, which is the default, and the warm Claude one) so the same screens
 * can be looked at either way.
 */
@Immutable
class OcColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val textDim: Color,
    val border: Color,
    val accent: Color,
    val link: Color,
    val toggleOn: Color,
    val progressTrack: Color,
    val progressFill: Color,
    val danger: Color,
    val ok: Color,
    val warn: Color,
    val userBubble: Color,
    val codeBg: Color,
    val scrim: Color,
)

private fun pal(
    dark: Boolean, bg: Long, surface: Long, high: Long, text: Long, dim: Long, border: Long, accent: Long, link: Long,
    user: Long, code: Long, track: Long, danger: Long = if (dark) 0xFFE5645C else 0xFFC8433B,
) = OcColors(
    isDark = dark, bg = Color(bg), surface = Color(surface), surfaceHigh = Color(high), text = Color(text), textDim = Color(dim),
    border = Color(border), accent = Color(accent), link = Color(link), toggleOn = Color(link), progressTrack = Color(track), progressFill = Color(link),
    danger = Color(danger), ok = Color(if (dark) 0xFF5CB885 else 0xFF2E8B57), warn = Color(if (dark) 0xFFE0A030 else 0xFFB87A10),
    userBubble = Color(user), codeBg = Color(code), scrim = Color(if (dark) 0x99000000 else 0x66000000),
)

/** cl0udly: night sky, pixel clouds, neutral grey text. */
val CloudDark = pal(
    true, 0xFF04070C, 0xFF111317, 0xFF1B1C1F, 0xFFE6E8EB, 0xFF8B9199, 0xFF24272C, 0xFFC9CFD8, 0xFFC9CFD8,
    0xFF1B1C20, 0xFF080B10, 0xFF1E2228,
)
val CloudLight = pal(
    false, 0xFFF7F8FA, 0xFFEEF0F3, 0xFFE4E7EB, 0xFF15171A, 0xFF5F656D, 0xFFDDE1E6, 0xFF3E4652, 0xFF3E4652,
    0xFFE9ECF0, 0xFFEEF0F3, 0xFFDCE3EC,
)

/** Warm: the Claude edition. */
val WarmDark = OcColors(
    isDark = true,
    bg = Color(0xFF141414), surface = Color(0xFF202020), surfaceHigh = Color(0xFF2B2B2A),
    text = Color(0xFFF5F4EF), textDim = Color(0xFF9B9A94), border = Color(0xFF3C3B38),
    accent = Color(0xFFD97757), link = Color(0xFF5EA0F2), toggleOn = Color(0xFF2B7BDC),
    progressTrack = Color(0xFF0B2547), progressFill = Color(0xFF2F7DE1),
    danger = Color(0xFFE5645C), ok = Color(0xFF5CB885), warn = Color(0xFFE0A030),
    userBubble = Color(0xFF282827), codeBg = Color(0xFF1A1A19), scrim = Color(0x99000000),
)
val WarmLight = OcColors(
    isDark = false,
    bg = Color(0xFFFAF9F5), surface = Color(0xFFF0EEE6), surfaceHigh = Color(0xFFE6E3D8),
    text = Color(0xFF1F1E1D), textDim = Color(0xFF73726C), border = Color(0xFFD6D3C7),
    accent = Color(0xFFC6613F), link = Color(0xFF1F6FD6), toggleOn = Color(0xFF2B7BDC),
    progressTrack = Color(0xFFD6E4F7), progressFill = Color(0xFF2F7DE1),
    danger = Color(0xFFC8433B), ok = Color(0xFF2E8B57), warn = Color(0xFFB87A10),
    userBubble = Color(0xFFEAE7DC), codeBg = Color(0xFFEFEDE4), scrim = Color(0x66000000),
)

/**
 * Compose resources load a font lazily and are @Composable, so the families are
 * composable values rather than top-level constants (which is what the Android
 * screen did with `R.font`). `remember` keeps the family stable across
 * recompositions.
 */
@Composable
fun InterFamily(): FontFamily {
    val regular = Font(Res.font.inter_regular, FontWeight.Normal)
    val medium = Font(Res.font.inter_medium, FontWeight.Medium)
    val semibold = Font(Res.font.inter_semibold, FontWeight.SemiBold)
    return remember(regular, medium, semibold) { FontFamily(regular, medium, semibold, semibold) }
}

/** Source Serif 4: the Claude edition's replies and headings. */
@Composable
fun SerifFamily(): FontFamily {
    val regular = Font(Res.font.source_serif_regular, FontWeight.Normal)
    val semibold = Font(Res.font.source_serif_semibold, FontWeight.SemiBold)
    return remember(regular, semibold) { FontFamily(regular, semibold, semibold, semibold) }
}

@Composable
fun SerifDisplay(): FontFamily {
    val regular = Font(Res.font.source_serif_display, FontWeight.Normal)
    val semibold = Font(Res.font.source_serif_semibold, FontWeight.SemiBold)
    return remember(regular, semibold) { FontFamily(regular, semibold) }
}

@Composable
fun JetBrainsMono(): FontFamily {
    val regular = Font(Res.font.jetbrains_mono, FontWeight.Normal)
    val medium = Font(Res.font.jetbrains_mono, FontWeight.Medium)
    return remember(regular, medium) { FontFamily(regular, medium, medium, medium) }
}

val LocalOc = staticCompositionLocalOf { CloudDark }
val LocalPrefs = staticCompositionLocalOf<Prefs?> { null }

/** The "cloud" look is the app's default; "claude" is the warm one. */
val LocalWarm = staticCompositionLocalOf { false }

object Oc {
    val c: OcColors @Composable @ReadOnlyComposable get() = LocalOc.current
    val warm: Boolean @Composable @ReadOnlyComposable get() = LocalWarm.current
    val prefs: Prefs? @Composable @ReadOnlyComposable get() = LocalPrefs.current

    val heading: FontFamily
        @Composable get() = if (warm) SerifDisplay() else InterFamily()

    val body: FontFamily
        @Composable get() = InterFamily()

    /** Reply font: serif in the Claude edition, Inter in cl0udly. */
    val reply: FontFamily
        @Composable get() = if (warm) SerifFamily() else InterFamily()

    val label: FontFamily
        @Composable get() = InterFamily()

    /** Code and paths: always the mono face the app ships. */
    val mono: FontFamily
        @Composable get() = JetBrainsMono()

    @Composable @ReadOnlyComposable
    fun round(r: Dp): Shape = RoundedCornerShape(if (warm) r else r)

    val pill: Shape
        @Composable @ReadOnlyComposable get() = CircleShape
}

@Composable
fun OcTheme(prefs: Prefs? = null, content: @Composable () -> Unit) {
    val warm = prefs?.edition == "claude"
    val mode = prefs?.colorMode ?: "system"
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    val colors = when {
        warm && dark -> WarmDark
        warm -> WarmLight
        dark -> CloudDark
        else -> CloudLight
    }
    // Claude's fonts are noticeably smaller than the originals: a base factor of 0.88 taken from phone screenshots.
    val scale = when (prefs?.textSize) { "small" -> 0.8f; "large" -> 1f; else -> 0.88f }
    val ui = (prefs?.uiScale?.toFloatOrNull() ?: 100f) / 100f
    val d = LocalDensity.current
    val scheme = if (dark) darkColorScheme(
        background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text,
        primary = colors.link, surfaceContainer = colors.bg, surfaceContainerHigh = colors.surface,
    ) else lightColorScheme(
        background = colors.bg, surface = colors.surface, onSurface = colors.text, onBackground = colors.text,
        primary = colors.link, surfaceContainer = colors.bg, surfaceContainerHigh = colors.surface,
    )
    CompositionLocalProvider(
        LocalOc provides colors,
        LocalWarm provides warm,
        LocalPrefs provides prefs,
        LocalDensity provides Density(d.density * ui, d.fontScale * scale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
