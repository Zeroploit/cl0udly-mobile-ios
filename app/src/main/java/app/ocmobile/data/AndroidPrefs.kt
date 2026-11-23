package app.ocmobile.data

import android.content.Context
import app.ocmobile.BuildConfig
import app.ocmobile.platform.SharedPreferencesStore

/**
 * The Android wiring for the shared [Prefs]: the same "oc" `SharedPreferences`
 * file the app has always written, so settings survive the move to commonMain,
 * plus the debug flag that picks the default local server port.
 */
fun androidPrefs(ctx: Context): Prefs = Prefs(
    SharedPreferencesStore(ctx.getSharedPreferences("oc", Context.MODE_PRIVATE)),
    debugBuild = BuildConfig.DEBUG,
)
