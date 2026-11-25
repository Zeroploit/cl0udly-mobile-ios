package app.ocmobile.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import android.provider.Settings

@Composable
actual fun rememberMotionEnabled(): Boolean {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) > 0f
    }
}
