package app.ocmobile.platform

import java.security.SecureRandom

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

/** `SecureRandom`: on Android this is the source the server password always used. */
actual fun randomToken(length: Int): String {
    if (length <= 0) return ""
    val random = SecureRandom()
    return buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
