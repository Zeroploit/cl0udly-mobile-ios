package app.ocmobile.platform

import java.util.Locale

/** The phone's language, as a bare code. */
actual fun systemLanguage(): String = Locale.getDefault().language
