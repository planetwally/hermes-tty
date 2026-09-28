package com.planetwally.hermestty.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private sealed interface Block
private class Prose(val text: AnnotatedString) : Block
private class Code(val lang: String, val code: String) : Block

private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET = Regex("""^[-*+]\s+(.*)$""")
private val RULE = Regex("""^(-{3,}|\*{3,}|_{3,})$""")
private val INLINE = Regex(
    """`([^`\n]+)`|\*\*(.+?)\*\*|\[([^\]\n]+)]\((https?://[^)\s]+)\)|(https?://[^\s<>()\[\]]*[^\s<>()\[\].,;:!?'"])"""
)

private val linkStyle = TextLinkStyles(SpanStyle(color = Hx.shell, textDecoration = TextDecoration.Underline))

private fun AnnotatedString.Builder.inline(s: String) {
    var i = 0
    for (m in INLINE.findAll(s)) {
        append(s.substring(i, m.range.first))
        val (code, bold, linkText, linkUrl, bare) = m.destructured
        when {
            code.isNotEmpty() -> withStyle(SpanStyle(color = Hx.accent, background = Hx.panel)) { append(code) }
            bold.isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Hx.title)) { append(bold) }
            linkUrl.isNotEmpty() -> withLink(LinkAnnotation.Url(linkUrl, linkStyle)) { append(linkText) }
            else -> withLink(LinkAnnotation.Url(bare, linkStyle)) { append(bare) }
        }
        i = m.range.last + 1
    }
    append(s.substring(i))
}

private fun parse(src: String): List<Block> {
    val out = mutableListOf<Block>()
    var buf = AnnotatedString.Builder()
    var inCode = false
    var lang = ""
    val code = StringBuilder()

    fun flush() {
        if (buf.length > 0) out += Prose(buf.toAnnotatedString())
        buf = AnnotatedString.Builder()
    }

    for (raw in src.lines()) {
        val t = raw.trimStart()
        if (inCode) {
            if (t.startsWith("```")) {
                out += Code(lang, code.toString().trimEnd('\n'))
                code.clear()
                inCode = false
            } else {
                code.append(raw).append('\n')
            }
            continue
        }
        if (t.startsWith("```")) {
            flush()
            inCode = true
            lang = t.removePrefix("```").trim()
            continue
        }
        if (buf.length > 0) buf.append('\n')
        val heading = HEADING.find(t)
        val bullet = BULLET.find(t)
        when {
            heading != null -> buf.withStyle(SpanStyle(color = Hx.title, fontWeight = FontWeight.Bold)) {
                inline(heading.groupValues[2])
            }
            RULE.matches(t) -> buf.withStyle(SpanStyle(color = Hx.muted)) { append("─".repeat(24)) }
            bullet != null -> {
                buf.append(" ".repeat(raw.length - t.length))
                buf.withStyle(SpanStyle(color = Hx.accent)) { append("• ") }
                buf.inline(bullet.groupValues[1])
            }
            t.startsWith(">") -> buf.withStyle(SpanStyle(color = Hx.muted)) {
                append("│ ")
                inline(t.removePrefix(">").trimStart())
            }
            else -> buf.inline(raw)
        }
    }
    if (inCode) out += Code(lang, code.toString().trimEnd('\n')) // still streaming
    flush()
    return out
}

@Composable
fun MarkdownText(text: String, color: Color = Hx.text, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parse(text) }
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (b in blocks) when (b) {
                is Prose -> T(b.text, color = color)
                is Code -> Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Hx.panel)
                        .border(1.dp, Hx.panelHi)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    if (b.lang.isNotEmpty()) T(b.lang, color = Hx.muted, scale = 0.8f)
                    T(
                        b.code,
                        Modifier.horizontalScroll(rememberScrollState()),
                        color = Hx.text,
                        scale = 0.92f,
                        softWrap = false,
                    )
                }
            }
        }
    }
}
