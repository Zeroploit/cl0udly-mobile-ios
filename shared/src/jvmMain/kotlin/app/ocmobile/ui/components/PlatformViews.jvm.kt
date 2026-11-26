package app.ocmobile.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Desktop preview stubs: the viewer shows its note instead. */
@Composable
actual fun PlatformPdfView(bytes: ByteArray, name: String, modifier: Modifier) { Box(modifier) }

@Composable
actual fun PlatformVideoView(bytes: ByteArray, mime: String, modifier: Modifier) { Box(modifier) }

@Composable
actual fun PlatformHtmlView(bytes: ByteArray, name: String, modifier: Modifier) { Box(modifier) }
