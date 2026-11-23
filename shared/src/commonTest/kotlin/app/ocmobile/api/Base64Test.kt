package app.ocmobile.api

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Basic header is built by hand, so it is pinned against the vectors in RFC
 * 4648 plus the one the app actually sends.
 */
class Base64Test {
    @Test
    fun `rfc 4648 vectors`() {
        assertEquals("", base64(""))
        assertEquals("Zg==", base64("f"))
        assertEquals("Zm8=", base64("fo"))
        assertEquals("Zm9v", base64("foo"))
        assertEquals("Zm9vYg==", base64("foob"))
        assertEquals("Zm9vYmE=", base64("fooba"))
        assertEquals("Zm9vYmFy", base64("foobar"))
    }

    @Test
    fun `the header the server expects`() {
        assertEquals("b3BlbmNvZGU6czNjcmV0", base64("opencode:s3cret"))
        // Passwords are generated from letters and digits, and this is the shape
        // the "oc" settings file holds.
        assertEquals("b3BlbmNvZGU6YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY3OA==", base64("opencode:abcdefghijklmnopqrstuvwxyz12345678"))
    }

    @Test
    fun `non-ascii is utf-8, not one byte per character`() {
        assertEquals("w7E=", base64("ñ"))
        assertEquals("8J+Zgg==", base64("🙂"))
    }
}
