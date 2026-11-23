package app.ocmobile.platform

import platform.Foundation.NSUUID

/**
 * Hexadecimal characters from the system UUID generator, which draws on the
 * platform random source. 32 of them carry 128 bits, which is what the app
 * asks for; iOS never runs a local server anyway.
 */
actual fun randomToken(length: Int): String {
    if (length <= 0) return ""
    val out = StringBuilder(length)
    while (out.length < length) {
        out.append(NSUUID().UUIDString.replace("-", ""))
    }
    return out.substring(0, length)
}
