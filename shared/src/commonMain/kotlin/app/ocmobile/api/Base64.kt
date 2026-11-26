package app.ocmobile.api

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * Base64 for the `Authorization: Basic` header, without line breaks.
 *
 * Hand-rolled on purpose: the standard library's encoder was still behind an
 * opt-in experiment, and this is 20 lines that a test can pin against known
 * vectors. The server password is Latin-1 today, but UTF-8 is what the header
 * is defined to carry.
 */
internal fun base64(input: String): String {
    val bytes = input.encodeToByteArray()
    return base64Bytes(bytes)
}

/** The same encoder for raw bytes (an image from the camera or the photo picker). */
internal fun base64Bytes(bytes: ByteArray): String {
    val out = StringBuilder((bytes.size + 2) / 3 * 4)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xff
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xff else -1
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xff else -1

        out.append(BASE64_ALPHABET[b0 shr 2])
        out.append(BASE64_ALPHABET[((b0 and 0x03) shl 4) or (if (b1 >= 0) b1 shr 4 else 0)])
        if (b1 >= 0) out.append(BASE64_ALPHABET[((b1 and 0x0f) shl 2) or (if (b2 >= 0) b2 shr 6 else 0)]) else out.append('=')
        if (b2 >= 0) out.append(BASE64_ALPHABET[b2 and 0x3f]) else out.append('=')

        i += 3
    }
    return out.toString()
}
