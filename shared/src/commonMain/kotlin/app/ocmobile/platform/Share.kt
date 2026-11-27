package app.ocmobile.platform

/**
 * A file to hand to the system share sheet: bytes in memory, its name and MIME type.
 */
data class ShareFile(val name: String, val mime: String, val bytes: ByteArray)

/**
 * The system share sheet for several files at once, with an optional accompanying text.
 * [onDone] runs once the sheet has been presented (or skipped); it makes no promise about
 * what the person did with the items.
 */
expect fun shareFiles(files: List<ShareFile>, text: String, onDone: () -> Unit)
