package app.ocmobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.ui.theme.Oc

/**
 * The agent's replies, as the app renders them: 18 sp with a generous line
 * height in the reply face (serif in the warm edition, Inter in cl0udly), fenced
 * code in its own block, and `**bold**`, `*italic*` and `` `code` `` inline.
 *
 * A full CommonMark parser would be a library the iOS target does not have;
 * this covers what models actually write.
 */
private sealed interface Block {
    data class Paragraph(val text: String) : Block
    data class Code(val language: String, val text: String) : Block
}

private fun blocks(text: String): List<Block> {
    val out = mutableListOf<Block>()
    var code: StringBuilder? = null
    var language = ""
    text.lines().forEach { line ->
        val fence = line.trimStart()
        if (fence.startsWith("```")) {
            val open = code
            if (open == null) {
                code = StringBuilder()
                language = fence.removePrefix("```").trim()
            } else {
                out += Block.Code(language, open.toString().trimEnd())
                code = null
                language = ""
            }
        } else {
            val inner = code
            if (inner != null) inner.appendLine(line) else if (line.isNotBlank()) out += Block.Paragraph(line)
        }
    }
    code?.let { out += Block.Code(language, it.toString().trimEnd()) }
    return out
}

/** Inline `code`, **bold** and *italic*, without a real Markdown parser. */
fun inlineMarkdown(text: String, codeBackground: Color, codeColor: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val buf = StringBuilder()
    fun flush() { if (buf.isNotEmpty()) { append(buf.toString()); buf.setLength(0) } }
    while (i < text.length) {
        val ch = text[i]
        when {
            ch == '`' && text.indexOf('`', i + 1) > i -> {
                flush()
                val end = text.indexOf('`', i + 1)
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, color = codeColor)) {
                    append(text.substring(i + 1, end))
                }
                i = end + 1
            }
            ch == '*' && text.startsWith("**", i) && text.indexOf("**", i + 2) > i -> {
                flush()
                val end = text.indexOf("**", i + 2)
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(i + 2, end)) }
                i = end + 2
            }
            ch == '*' && text.indexOf('*', i + 1) > i -> {
                flush()
                val end = text.indexOf('*', i + 1)
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                i = end + 1
            }
            else -> { buf.append(ch); i++ }
        }
    }
    flush()
}

@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val c = Oc.c
    val parsed = remember(text) { blocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        parsed.forEach { block ->
            when (block) {
                is Block.Paragraph -> Text(
                    inlineMarkdown(block.text, c.surfaceHigh, c.text),
                    color = c.text,
                    fontSize = 18.sp,
                    lineHeight = 30.sp,
                    fontFamily = Oc.reply,
                )
                is Block.Code -> Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(c.codeBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        block.text,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        fontFamily = Oc.mono,
                        color = c.text,
                    )
                }
            }
        }
    }
}