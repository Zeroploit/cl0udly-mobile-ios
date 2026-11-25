package app.ocmobile.platform

import androidx.compose.runtime.Composable

/**
 * Whether endless motion is welcome: the app turns off breathing, blinking,
 * shimmer and long transitions when the platform asks for less movement.
 *
 * Android reads the system animator scale (and battery saver, in the full app);
 * iOS and the desktop preview answer true.
 */
@Composable
expect fun rememberMotionEnabled(): Boolean
