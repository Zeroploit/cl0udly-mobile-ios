package app.ocmobile.platform

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

/**
 * "ru" from the device language: the locale identifier is "ru_RU" or "ru-RU" depending on the
 * system, and the part before the separator is the language. It follows a change in Settings
 * without a restart.
 */
actual fun systemLanguage(): String =
    NSLocale.currentLocale.localeIdentifier.substringBefore('_').substringBefore('-').lowercase()
