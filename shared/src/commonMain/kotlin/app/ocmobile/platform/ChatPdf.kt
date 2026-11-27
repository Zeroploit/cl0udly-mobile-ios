package app.ocmobile.platform

/**
 * Renders a simple multi-page A4 text PDF: a title header on the first page and the plain
 * text lines below it, wrapped and paginated. Delivers the PDF bytes to [onResult]; null
 * when the platform cannot render PDFs.
 */
expect fun chatPdf(title: String, lines: List<String>, onResult: (ByteArray?) -> Unit)
