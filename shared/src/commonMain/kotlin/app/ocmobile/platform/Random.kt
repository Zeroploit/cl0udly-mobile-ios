package app.ocmobile.platform

/**
 * A random token of [length] alphanumeric characters, used for the password of
 * the local OpenCode server.
 *
 * Only the Android build ever runs a local server, so on iOS this is never
 * called; it exists because [app.ocmobile.data.Prefs] is shared. The contract
 * asks for alphanumeric output, not a specific alphabet: iOS produces
 * hexadecimal characters from the system UUID generator (128 bits at the 32
 * characters the app asks for), Android and the JVM use `SecureRandom` over
 * `0-9A-Za-z`.
 */
expect fun randomToken(length: Int): String
