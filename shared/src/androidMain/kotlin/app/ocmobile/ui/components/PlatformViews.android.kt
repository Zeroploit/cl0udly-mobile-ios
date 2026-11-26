package app.ocmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Android stub actuals: the shared UI runs on iOS; the Android app has its own PdfRenderer and
 * VideoView screens in `:app`. The stubs keep the module compiling.
 */
@Composable
actual fun PlatformPdfView(bytes: ByteArray, name: String, modifier: Modifier) { Box(modifier) }

@Composable
actual fun PlatformVideoView(bytes: ByteArray, mime: String, modifier: Modifier) { Box(modifier) }
