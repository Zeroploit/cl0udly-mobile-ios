package app.ocmobile.platform

import platform.Foundation.NSUserDefaults

/**
 * iOS side of [SettingsStore]. Keys keep their names, so a settings backup taken
 * on Android still means the same thing here.
 */
class UserDefaultsStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SettingsStore {

    override fun getString(key: String, def: String?): String? =
        defaults.stringForKey(key) ?: def

    override fun getBoolean(key: String, def: Boolean): Boolean =
        if (defaults.objectForKey(key) == null) def else defaults.boolForKey(key)

    override fun getLong(key: String, def: Long): Long =
        if (defaults.objectForKey(key) == null) def else defaults.integerForKey(key)

    override fun getFloat(key: String, def: Float): Float =
        if (defaults.objectForKey(key) == null) def else defaults.floatForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, key)
    }

    override fun putBoolean(key: String, value: Boolean) {
        defaults.setBool(value, forKey = key)
    }

    override fun putLong(key: String, value: Long) {
        defaults.setInteger(value, forKey = key)
    }

    override fun putFloat(key: String, value: Float) {
        defaults.setFloat(value, forKey = key)
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey(key)
    }
}
