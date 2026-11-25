package app.ocmobile.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.composed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.ui.theme.LocalPrefs
import app.ocmobile.ui.theme.Oc
import app.ocmobile.ui.theme.iconLabel
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.minimumInteractiveComponentSize

/**
 * The app's own text: Inter (or Source Serif in the warm edition), 16 sp unless
 * told otherwise. Every screen goes through it so the typography stays one.
 */
@Composable
fun Txt(
    text: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    color: Color = Oc.c.text,
    weight: FontWeight? = null,
    family: FontFamily? = null,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier = modifier, color = color, fontSize = size, fontWeight = weight,
        fontFamily = family ?: Oc.body, maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        textAlign = align, lineHeight = lineHeight, style = TextStyle.Default,
    )
}

/** Serif heading (warm edition) or Inter, depending on the look. */
@Composable
fun Heading(text: String, modifier: Modifier = Modifier, size: TextUnit = 26.sp, color: Color = Oc.c.text, align: TextAlign? = null) =
    Txt(text, modifier, size, color, FontWeight.Normal, Oc.heading, align = align, lineHeight = size * 1.22f)

@Composable
fun Ico(v: ImageVector, modifier: Modifier = Modifier, tint: Color = Oc.c.text, size: Dp = 24.dp, contentDescription: String? = null) {
    androidx.compose.foundation.Image(
        imageVector = v, contentDescription = contentDescription, modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    val enabled = LocalPrefs.current?.haptics ?: true
    return { if (enabled) h.performHapticFeedback(HapticFeedbackType.LongPress) }
}

/**
 * A press sinks the element and lets it spring back, like it is held by a
 * magnet. Copied from the Android Gestures.kt: it is the app's only "tap".
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.tap(
    enabled: Boolean = true,
    scale: Float = 0.96f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(
        if (pressed && enabled) scale else 1f,
        if (pressed) spring(Spring.DampingRatioNoBouncy, Spring.StiffnessHigh) else spring(0.45f, 900f),
        label = "press",
    )
    val a by animateFloatAsState(if (pressed && enabled) 0.72f else 1f, androidx.compose.animation.core.tween(70), label = "pressAlpha")
    val haptic = LocalHapticFeedback.current
    val hapticsOn = LocalPrefs.current?.haptics ?: true
    this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = a }
        .combinedClickable(
            interactionSource = source, indication = null, enabled = enabled,
            onLongClick = onLongClick?.let { l -> { if (hapticsOn) haptic.performHapticFeedback(HapticFeedbackType.LongPress); l() } },
            onClick = onClick,
        )
}

/** The outlined circle the cl0udly frame uses for every top-bar button. */
@Composable
fun CircleBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tint: Color = Oc.c.text,
    contentDescription: String? = iconLabel(icon),
) {
    val c = Oc.c
    Box(
        modifier.size(size).clip(CircleShape).background(c.surfaceHigh.copy(alpha = 0.7f))
            .border(1.dp, c.border, CircleShape).tap(onClick = onClick)
            .semantics { role = Role.Button; if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = tint, size = 22.dp) }
}

/** A filled round button, for send and stop. */
@Composable
fun RoundBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = Oc.c.bg,
    bg: Color = Oc.c.text,
    enabled: Boolean = true,
    contentDescription: String? = iconLabel(icon),
) {
    Box(
        modifier.size(size).clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = 0.3f)).tap(enabled = enabled, onClick = onClick)
            .semantics { role = Role.Button; if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = tint, size = 21.dp) }
}

@Composable
fun IconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Oc.c.text,
    size: Dp = 44.dp,
    iconSize: Dp = 24.dp,
    bg: Color? = null,
    contentDescription: String? = iconLabel(icon),
) {
    Box(
        modifier.minimumInteractiveComponentSize().size(size).clip(Oc.pill)
            .then(if (bg != null) Modifier.background(bg) else Modifier).tap(onClick = onClick)
            .semantics { role = Role.Button; if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) { Ico(icon, tint = tint, size = iconSize) }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, danger: Boolean = false) {
    val c = Oc.c
    val shape = Oc.pill
    val fg = if (filled && danger) Color.White else if (filled) c.bg else if (danger) c.danger else c.text
    Box(
        modifier.clip(shape)
            .then(if (filled) Modifier.background(if (danger) c.danger else c.text) else Modifier.border(1.dp, if (danger) c.danger.copy(alpha = .5f) else c.border, shape))
            .tap(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, size = 14.sp, color = fg, weight = FontWeight.Medium) }
}

/** The top bar: ☰ on the left, the model in a pill, an action on the right. */
@Composable
fun TopBar(
    model: String,
    onMenu: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircleBtn(app.ocmobile.ui.theme.Ic.Menu, onMenu)
        ModelPill(model)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** "OpenCode" in the text colour, the model in grey, in one outlined pill. */
@Composable
fun ModelPill(model: String, onClick: (() -> Unit)? = null) {
    val c = Oc.c
    Row(
        Modifier.height(48.dp).widthIn(max = 220.dp).clip(CircleShape)
            .background(c.surfaceHigh.copy(alpha = 0.7f)).border(1.dp, c.border, CircleShape)
            .then(if (onClick != null) Modifier.tap(scale = 0.97f, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Txt("OpenCode", size = 16.sp, maxLines = 1)
        if (model.isNotBlank()) {
            Spacer(Modifier.width(6.dp))
            Txt(model, size = 16.sp, color = c.textDim, maxLines = 1)
        }
    }
}
