package app.ocmobile.platform

import java.util.Locale

/** The desktop's language, as a bare code. */
actual fun systemLanguage(): String = Locale.getDefault().language
