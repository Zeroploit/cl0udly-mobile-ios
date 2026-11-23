package app.ocmobile.platform

import java.security.SecureRandom

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

/** `SecureRandom`, the same source `Prefs` used before it moved to commonMain. */
actual fun randomToken(length: Int): String {
    if (length <= 0) return ""
    val random = SecureRandom()
    return buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
