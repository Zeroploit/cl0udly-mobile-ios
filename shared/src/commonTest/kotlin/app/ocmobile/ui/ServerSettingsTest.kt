package app.ocmobile.ui

import app.ocmobile.platform.MemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerSettingsTest {
    @Test
    fun `people type an address without a scheme`() {
        assertEquals("http://192.168.1.10:4096", normalizeServerUrl("192.168.1.10:4096"))
        assertEquals("http://192.168.1.10:4096", normalizeServerUrl("  http://192.168.1.10:4096  "))
        assertEquals("https://oc.example.com", normalizeServerUrl("https://oc.example.com/"))
        // A trailing slash would turn "/global/health" into "//global/health".
        assertEquals("http://host:4096", normalizeServerUrl("host:4096//"))
        assertEquals("", normalizeServerUrl("   "))
    }

    @Test
    fun `the address and password survive a restart`() {
        val store = MemorySettingsStore()
        val settings = ServerSettings(store)
        assertFalse(settings.configured)

        settings.url = normalizeServerUrl("192.168.1.10:4096")
        settings.password = "s3cret"

        val reopened = ServerSettings(store)
        assertTrue(reopened.configured)
        assertEquals("http://192.168.1.10:4096", reopened.url)
        assertEquals("s3cret", reopened.password)
    }

    @Test
    fun `forgetting clears both`() {
        val store = MemorySettingsStore(mapOf("server_url" to "http://h:4096", "server_password" to "p"))
        val settings = ServerSettings(store)
        settings.forget()
        assertFalse(settings.configured)
        assertEquals("", store.getString("server_url").orEmpty())
        assertEquals("", store.getString("server_password").orEmpty())
    }
}
