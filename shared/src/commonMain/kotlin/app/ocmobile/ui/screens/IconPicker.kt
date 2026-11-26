package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.AppIcon
import app.ocmobile.ui.theme.AppIcons
import app.ocmobile.ui.theme.Oc

/**
 * App icon picker: the tiles are the colour each icon is built on.
 *
 * Android switched the launcher icon through an `activity-alias` per entry and drew the drawable
 * over the tile. iOS keeps one icon chosen at build time and has no drawable for the set, so the
 * tile is the [AppIcon.bg] circle with the label's first letter in it and [AppIcons.apply] only
 * remembers the choice.
 */
@Composable
fun IconPickerSheet(onDismiss: () -> Unit) {
    val c = Oc.c
    OcSheet(onDismiss, "App icon", skipHalf = false) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // the set is short and fixed: rows of three, no lazy grid needed
            AppIcons.all.chunked(3).forEach { chunk -> IconRow(chunk) }
        }
        Txt("iOS keeps one icon: the choice here only changes the previews in the app.", size = 13.sp, color = c.textDim, modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp))
    }
}

@Composable
private fun IconRow(icons: List<AppIcon>) {
    val c = Oc.c
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        icons.forEach { icon -> IconTile(icon, Modifier.weight(1f)) }
        // the last row keeps the same column width as the others
        repeat(3 - icons.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun IconTile(icon: AppIcon, modifier: Modifier) {
    val c = Oc.c
    val sel = Graph.prefs.appIcon == icon.id
    Column(modifier.clip(Oc.round(20.dp)).clickable { Graph.prefs.appIcon = icon.id; AppIcons.apply(icon.id) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(icon.bg).border(if (sel) 3.dp else 1.dp, if (sel) c.link else c.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Txt(icon.label.take(1).uppercase(), size = 28.sp, weight = FontWeight.SemiBold, color = if (c.isDark) Color.White else Color.Black)
        }
        Txt(icon.label, Modifier.padding(top = 8.dp), size = 14.sp, color = if (sel) c.link else c.text, align = TextAlign.Center, maxLines = 1)
    }
}
