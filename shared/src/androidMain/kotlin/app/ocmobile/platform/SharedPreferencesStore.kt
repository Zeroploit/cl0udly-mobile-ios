package app.ocmobile.platform

import android.content.SharedPreferences

/**
 * Android side of [SettingsStore]: the same `SharedPreferences` file the app uses
 * today ("oc"), so existing settings survive the move untouched.
 */
class SharedPreferencesStore(private val sp: SharedPreferences) : SettingsStore {

    override fun getString(key: String, def: String?): String? = sp.getString(key, def)

    override fun getBoolean(key: String, def: Boolean): Boolean = sp.getBoolean(key, def)

    override fun getLong(key: String, def: Long): Long = sp.getLong(key, def)

    override fun getFloat(key: String, def: Float): Float = sp.getFloat(key, def)

    override fun putString(key: String, value: String?) {
        sp.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    override fun putBoolean(key: String, value: Boolean) {
        sp.edit().putBoolean(key, value).apply()
    }

    override fun putLong(key: String, value: Long) {
        sp.edit().putLong(key, value).apply()
    }

    override fun putFloat(key: String, value: Float) {
        sp.edit().putFloat(key, value).apply()
    }

    override fun remove(key: String) {
        sp.edit().remove(key).apply()
    }
}
