package app.ocmobile.platform

/**
 * Key-value settings, the one thing [app.ocmobile.data.Prefs] actually needs from
 * the platform. Android backs it with `SharedPreferences`, iOS with
 * `NSUserDefaults`; keeping it behind this interface is what lets `Prefs` live in
 * `commonMain` without touching any of the 44 files that read it.
 */
interface SettingsStore {
    fun getString(key: String, def: String? = null): String?
    fun getBoolean(key: String, def: Boolean): Boolean
    fun getLong(key: String, def: Long): Long
    fun getFloat(key: String, def: Float): Float

    /** A null [value] removes the key. */
    fun putString(key: String, value: String?)
    fun putBoolean(key: String, value: Boolean)
    fun putLong(key: String, value: Long)
    fun putFloat(key: String, value: Float)
    fun remove(key: String)
}

/** In-memory store for previews and tests. Not persisted, not thread safe. */
class MemorySettingsStore(initial: Map<String, Any> = emptyMap()) : SettingsStore {
    private val values = initial.toMutableMap()

    override fun getString(key: String, def: String?): String? = values[key] as? String ?: def
    override fun getBoolean(key: String, def: Boolean): Boolean = values[key] as? Boolean ?: def
    override fun getLong(key: String, def: Long): Long = values[key] as? Long ?: def
    override fun getFloat(key: String, def: Float): Float = values[key] as? Float ?: def

    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun putLong(key: String, value: Long) {
        values[key] = value
    }

    override fun putFloat(key: String, value: Float) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    /** Everything currently stored, for tests. */
    fun snapshot(): Map<String, Any> = values.toMap()
}
