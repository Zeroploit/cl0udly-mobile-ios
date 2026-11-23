package app.ocmobile.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsStoreTest {

    @Test
    fun missing_keys_fall_back_to_the_default() {
        val store = MemorySettingsStore()
        assertEquals("medium", store.getString("text_size", "medium"))
        assertTrue(store.getBoolean("haptics", true))
        assertEquals(0L, store.getLong("update_checked_at", 0L))
        assertEquals(12.0f, store.getFloat("limit_5h", 12.0f))
        assertNull(store.getString("nothing"))
    }

    @Test
    fun every_type_round_trips() {
        val store = MemorySettingsStore()
        store.putString("edition", "claude")
        store.putBoolean("keep_awake", false)
        store.putLong("sync_last", 1_700_000_000_000L)
        store.putFloat("limit_week", 30.5f)

        assertEquals("claude", store.getString("edition", "cloud"))
        assertEquals(false, store.getBoolean("keep_awake", true))
        assertEquals(1_700_000_000_000L, store.getLong("sync_last", 0L))
        assertEquals(30.5f, store.getFloat("limit_week", 0f))
    }

    @Test
    fun a_null_string_removes_the_key() {
        val store = MemorySettingsStore()
        store.putString("draft_ses_1", "hello")
        store.putString("draft_ses_1", null)
        assertNull(store.getString("draft_ses_1"))
        assertNull(store.snapshot()["draft_ses_1"])
    }

    @Test
    fun remove_drops_the_key() {
        val store = MemorySettingsStore()
        store.putString("active_device", "dev_1")
        store.remove("active_device")
        assertNull(store.snapshot()["active_device"])
    }

    @Test
    fun a_value_of_another_type_is_not_returned() {
        // Prefs stores numbers as Long on one platform and as double on Android;
        // a wrong-typed read must fall back instead of throwing.
        val store = MemorySettingsStore(mapOf("limit_5h" to 12L))
        assertEquals(0f, store.getFloat("limit_5h", 0f))
    }
}
