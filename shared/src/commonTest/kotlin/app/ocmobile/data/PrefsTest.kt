package app.ocmobile.data

import app.ocmobile.platform.MemorySettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [Prefs] moved to commonMain on top of [MemorySettingsStore]. What has to hold
 * after the move: the keys and defaults are unchanged (an Android install keeps
 * its settings), writes reach the store immediately, and the per-chat and
 * per-device helpers keep their behaviour.
 */
class PrefsTest {
    @Test
    fun `defaults are the ones the app always had`() {
        val prefs = Prefs(MemorySettingsStore())
        assertEquals("cloud", prefs.edition)
        assertEquals("system", prefs.colorMode)
        assertEquals("medium", prefs.textSize)
        assertEquals("auto", prefs.permissionMode)
        assertEquals("/root/work", prefs.currentDir)
        assertEquals("build", prefs.agent)
        assertEquals("[]", prefs.devicesJson)
        // The limits the app shows before it knows better.
        assertEquals(12.0, prefs.limit5h)
        assertEquals(30.0, prefs.limitWeek)
        assertEquals(60.0, prefs.limitMonth)
    }

    @Test
    fun `the local server port follows the build type`() {
        assertEquals("4196", Prefs(MemorySettingsStore(), debugBuild = true).serverPort)
        assertEquals("4096", Prefs(MemorySettingsStore(), debugBuild = false).serverPort)
        // Set by the user once: the build type no longer matters.
        val store = MemorySettingsStore(mapOf("server_port" to "8080"))
        assertEquals("8080", Prefs(store, debugBuild = true).serverPort)
    }

    @Test
    fun `an existing installation keeps its settings`() {
        // The keys are the ones SharedPreferences has been holding since before
        // the move, which is the whole point of the store abstraction.
        val store = MemorySettingsStore(
            mapOf(
                "user_name" to "Luna",
                "wrap_lines" to true,
                "ui_scale" to "120",
                "update_checked_at" to 1_700_000_000_000L,
                "limit_week" to 25.5f,
                "onboarded" to true,
            ),
        )
        val prefs = Prefs(store)
        assertEquals("Luna", prefs.userName)
        assertTrue(prefs.wrapLines)
        assertEquals("120", prefs.uiScale)
        assertEquals(1_700_000_000_000L, prefs.updateCheckedAt)
        assertEquals(25.5, prefs.limitWeek)
        assertTrue(prefs.onboarded)
    }

    @Test
    fun `writes reach the store at once`() {
        val store = MemorySettingsStore()
        val prefs = Prefs(store)
        prefs.userName = "Luna"
        prefs.mascotOn = false
        prefs.limitMonth = 99.0
        assertEquals("Luna", store.getString("user_name"))
        assertFalse(store.getBoolean("mascot_on", true))
        // Doubles are kept as floats, exactly as SharedPreferences did.
        assertEquals(99f, store.getFloat("limit_month", 0f))
    }

    @Test
    fun `drafts are per chat and a blank draft removes the key`() {
        val store = MemorySettingsStore()
        val prefs = Prefs(store)
        assertEquals("", prefs.draft("ses_1"))
        prefs.setDraft("ses_1", "half a sentence")
        prefs.setDraft("ses_2", "another")
        assertEquals("half a sentence", prefs.draft("ses_1"))
        assertEquals("another", prefs.draft("ses_2"))
        prefs.setDraft("ses_1", "   ")
        assertEquals("", prefs.draft("ses_1"))
        assertNull(store.getString("draft_ses_1"))
    }

    @Test
    fun `each device remembers its own directory`() {
        val prefs = Prefs(MemorySettingsStore())
        assertNull(prefs.getDir("phone"))
        prefs.setDir("phone", "/root/work/cl0udly")
        prefs.setDir("laptop", "/home/luna/code")
        assertEquals("/root/work/cl0udly", prefs.getDir("phone"))
        assertEquals("/home/luna/code", prefs.getDir("laptop"))
    }

    @Test
    fun `pinning toggles one chat at a time`() {
        val prefs = Prefs(MemorySettingsStore())
        assertTrue(prefs.pinnedSet().isEmpty())
        prefs.togglePin("a")
        prefs.togglePin("b")
        assertEquals(setOf("a", "b"), prefs.pinnedSet())
        prefs.togglePin("a")
        assertEquals(setOf("b"), prefs.pinnedSet())
    }

    @Test
    fun `memory can be off for one chat while on for the rest`() {
        val prefs = Prefs(MemorySettingsStore())
        assertTrue(prefs.memoryOn("ses_1"))
        prefs.setChatMemory("ses_1", false)
        assertFalse(prefs.memoryOn("ses_1"))
        assertTrue(prefs.memoryOn("ses_2"))
        prefs.setChatMemory("ses_1", true)
        assertTrue(prefs.memoryOn("ses_1"))
        // Turning it off globally wins over the per-chat list.
        prefs.memoryEnabled = false
        assertFalse(prefs.memoryOn("ses_2"))
    }

    @Test
    fun `the server password is generated once and kept`() {
        val store = MemorySettingsStore()
        val prefs = Prefs(store)
        val first = prefs.serverPassword
        assertEquals(32, first.length)
        assertTrue(first.all { it.isLetterOrDigit() })
        assertEquals(first, prefs.serverPassword)
        assertEquals(first, Prefs(store).serverPassword)
        assertFalse(prefs.serverPassword.all { it == first[0] })
    }

    @Test
    fun `memory answers keep the newest answer per call`() {
        val prefs = Prefs(MemorySettingsStore())
        assertNull(prefs.memoryAnswer("call_1"))
        prefs.answerMemory("call_1", true)
        assertEquals("y", prefs.memoryAnswer("call_1"))
        prefs.answerMemory("call_1", false)
        assertEquals("n", prefs.memoryAnswer("call_1"))
        assertEquals("call_1:n", prefs.memoryAnswers)
        prefs.answerMemory("call_2", true)
        assertEquals("call_1:n,call_2:y", prefs.memoryAnswers)
    }
}
