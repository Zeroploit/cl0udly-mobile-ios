package app.ocmobile.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc

/**
 * A calm hint on home: if the system may put the server to sleep (no battery optimization
 * exemption) or notifications are off, long tasks get cut off and Allow / Deny don't arrive.
 * Tapping leads to the right setting; the cross hides the hint for good.
 *
 * Ported from the Android `AccessBanner.kt`. The conditions it warns about are Android's own
 * (battery optimization and notification permission for the bundled server); on a client the
 * server runs on the user's computer and [Graph.devices.isRemote] is always true, so — exactly
 * like the Android copy when connected to a computer — the banner stays hidden.
 */
@Composable
fun AccessBanner() {
    val c = Oc.c
    val prefs = Graph.prefs
    var hidden by remember { mutableStateOf(prefs.accessBannerHidden) }
    if (hidden || Graph.devices.isRemote) return
    // A device with a local server would check the two system settings here; this client has none.
    val notif = true
    val battery = true
    if (notif && battery) return
    val text = when {
        !battery && !notif -> "Allow background work and notifications so tasks don't stop"
        !battery -> "Allow background work so long tasks don't stop"
        else -> "Turn on notifications for answers and Allow / Deny"
    }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(CircleShape).background(c.surface).border(1.dp, c.border, CircleShape)
            .tap(scale = 0.98f) {
                // opening the system setting is Android-only; the hint still dismisses
                prefs.accessBannerHidden = true
                hidden = true
            }
            .padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(if (!battery) Ic.Zap else Ic.Bell, tint = c.textDim, size = 17.dp); Spacer(Modifier.width(10.dp))
        Txt(text, Modifier.weight(1f), size = 14.sp, maxLines = 2)
        IconBtn(Ic.Close, { prefs.accessBannerHidden = true; hidden = true }, tint = c.textDim, size = 38.dp, iconSize = 18.dp)
    }
}
