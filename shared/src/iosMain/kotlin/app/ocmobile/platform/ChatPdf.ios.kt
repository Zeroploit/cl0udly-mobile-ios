@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSMutableData
import platform.Foundation.NSString
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIGraphicsBeginPDFContextToData
import platform.UIKit.UIGraphicsBeginPDFPage
import platform.UIKit.UIGraphicsEndPDFContext
import platform.UIKit.drawAtPoint

/**
 * A simple text PDF, rendered with the plain CoreGraphics PDF context functions: A4 pages, a
 * title header on the first page and the plain lines below it, wrapped to the page width and
 * paginated by line height. iOS can always render, so this never delivers null.
 *
 * The begin/end context functions are used instead of UIGraphicsPDFRenderer: their bindings are
 * flat C calls, while the renderer's block-based `pdfData` API did not compile in this
 * toolchain (verified by the CI build of 2026-11-27). Text is drawn through NSString's
 * `drawAtPoint` — the UIKit String extension does not exist on Kotlin String, hence the cast.
 */
actual fun chatPdf(title: String, lines: List<String>, onResult: (ByteArray?) -> Unit) {
    val pageWidth = 612.0
    val pageHeight = 792.0
    val margin = 54.0
    val bodyFont = UIFont.systemFontOfSize(11.0)
    val titleFont = UIFont.systemFontOfSize(18.0, weight = UIFontWeightSemibold)
    val lineHeight = bodyFont.lineHeight
    val textWidth = pageWidth - margin * 2
    // average glyph width of the system font is about 0.6 of the point size
    val charsPerLine = (textWidth / (bodyFont.pointSize * 0.6)).toInt().coerceAtLeast(10)
    val titleBlock = if (title.isBlank()) 0.0 else titleFont.lineHeight * 1.6

    val wrapped = mutableListOf<String>()
    for (line in lines) {
        if (line.length <= charsPerLine) {
            wrapped += line
            continue
        }
        var rest = line
        while (rest.length > charsPerLine) {
            var cut = rest.lastIndexOf(' ', charsPerLine)
            if (cut <= 0) cut = charsPerLine
            wrapped += rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        wrapped += rest
    }

    val data = NSMutableData()
    UIGraphicsBeginPDFContextToData(data, CGRectMake(0.0, 0.0, pageWidth, pageHeight), emptyMap<Any?, Any>())
    var index = 0
    var page = 0
    while (true) {
        UIGraphicsBeginPDFPage()
        var y = margin
        if (page == 0 && titleBlock > 0.0) {
            (title as NSString).drawAtPoint(
                CGPointMake(margin, y),
                mapOf<Any?, Any>(NSFontAttributeName to titleFont),
            )
            y += titleBlock
        }
        val bottom = pageHeight - margin
        while (index < wrapped.size && y + lineHeight <= bottom) {
            (wrapped[index] as NSString).drawAtPoint(
                CGPointMake(margin, y),
                mapOf<Any?, Any>(NSFontAttributeName to bodyFont),
            )
            y += lineHeight
            index++
        }
        if (index >= wrapped.size) break
        page++
    }
    UIGraphicsEndPDFContext()
    onResult(data.toByteArray())
}
