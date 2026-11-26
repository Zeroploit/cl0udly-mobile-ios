package app.ocmobile.platform

/** The app's version as the user sees it ("1.4.2 (312)"), for the About screen and bug reports. */
expect fun appVersion(): String

/** The phone and system ("iPhone 17, iOS 26.1"), for bug reports. */
expect fun deviceLabel(): String
