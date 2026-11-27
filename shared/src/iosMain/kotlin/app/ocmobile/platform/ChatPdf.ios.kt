@file:OptIn(ExperimentalForeignApi::class)

package app.ocmobile.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIGraphicsPDFRenderer

/**
 * A simple text PDF, rendered with UIGraphicsPDFRenderer on CoreGraphics: A4 pages, a title
 * header on the first page and the plain lines below it, wrapped to the page width and
 * paginated by line height. iOS can always render, so this never delivers null.
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

    val renderer = UIGraphicsPDFRenderer(
        bounds = CGRectMake(0.0, 0.0, pageWidth, pageHeight),
        formatAttributes = null,
    )
    val data = renderer.pdfData { context ->
        var index = 0
        var page = 0
        while (true) {
            context.beginPage()
            var y = margin
            if (page == 0 && titleBlock > 0.0) {
                title.drawAtPoint(
                    point = CGPointMake(margin, y),
                    withAttributes = mapOf<Any?, Any>(NSFontAttributeName to titleFont),
                )
                y += titleBlock
            }
            val bottom = pageHeight - margin
            while (index < wrapped.size && y + lineHeight <= bottom) {
                wrapped[index].drawAtPoint(
                    point = CGPointMake(margin, y),
                    withAttributes = mapOf<Any?, Any>(NSFontAttributeName to bodyFont),
                )
                y += lineHeight
                index++
            }
            if (index >= wrapped.size) return@pdfData
            page++
        }
    }
    onResult(data.toByteArray())
}
