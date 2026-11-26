package app.ocmobile.platform

/**
 * The language the system is set to, as a bare code ("ru", "en"): the app follows it when the
 * language setting is "system". Android and the desktop preview read `Locale`, iOS reads
 * `NSLocale.preferredLanguages`.
 */
expect fun systemLanguage(): String
