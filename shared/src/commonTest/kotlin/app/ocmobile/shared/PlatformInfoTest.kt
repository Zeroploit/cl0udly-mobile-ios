package app.ocmobile.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformInfoTest {

    @Test
    fun every_platform_reports_a_name() {
        assertTrue(platformInfo().name.isNotBlank())
    }

    @Test
    fun only_android_can_run_the_local_server() {
        val info = platformInfo()
        assertFalse(info.isIos && info.canRunLocalServer)
        if (info.canRunLocalServer) assertTrue(info.isAndroid)
    }
}
