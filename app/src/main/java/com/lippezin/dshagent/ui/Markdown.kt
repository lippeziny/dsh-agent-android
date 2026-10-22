package com.lippezin.dshagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MdBlock {
    data class Para(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Bullet(val marker: String, val text: String, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
    data class Table(val text: String) : MdBlock
    object Rule : MdBlock
}

private val headingRegex = Regex("^(#{1,4})\\s+(.*)$")
private val bulletRegex = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val ruleRegex = Regex("^(-{3,}|\\*{3,}|_{3,})$")

private fun parseMarkdown(src: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val lines = src.replace("\r\n", "\n").split('\n')
    val para = StringBuilder()
    fun flushPara() {
        if (para.isNotBlank()) out += MdBlock.Para(para.toString().trimEnd())
        para.clear()
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            flushPara()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = ArrayList<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines += lines[i]
                i++
            }
            out += MdBlock.Code(lang, codeLines.joinToString("\n"))
        } else if (trimmed.isEmpty()) {
            flushPara()
        } else if (headingRegex.matches(trimmed)) {
            flushPara()
            val m = headingRegex.matchEntire(trimmed)!!
            out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
        } else if (ruleRegex.matches(trimmed)) {
            flushPara()
            out += MdBlock.Rule
        } else if (trimmed.startsWith("|")) {
            flushPara()
            val tableLines = ArrayList<String>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                tableLines += lines[i].trim()
                i++
            }
            i--
            out += MdBlock.Table(tableLines.joinToString("\n"))
        } else if (trimmed.startsWith(">")) {
            flushPara()
            out += MdBlock.Quote(trimmed.removePrefix(">").trim())
        } else if (bulletRegex.matches(line)) {
            flushPara()
            val m = bulletRegex.matchEntire(line)!!
            val raw = m.groupValues[2]
            val marker = if (raw.first().isDigit()) raw else "•"
            out += MdBlock.Bullet(marker, m.groupValues[3], m.groupValues[1].length / 2)
        } else {
            if (para.isNotEmpty()) para.append('\n')
            para.append(line.trimEnd())
        }
        i++
    }
    flushPara()
    return out
}

private fun inlineMarkdown(src: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < src.length) {
        val ch = src[i]
        if (src.startsWith("**", i)) {
            val end = src.indexOf("**", i + 2)
            if (end > i + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(src.substring(i + 2, end)) }
                i = end + 2
                continue
            }
        } else if (ch == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i + 1) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF1E212B), color = Color(0xFFD8E1FF))) {
                    append(src.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        } else if (ch == '[') {
            val close = src.indexOf("](", i)
            val end = if (close > i) src.indexOf(')', close + 2) else -1
            if (close > i && end > close) {
                withStyle(SpanStyle(color = C.Accent, textDecoration = TextDecoration.Underline)) {
                    append(src.substring(i + 1, close))
                }
                i = end + 1
                continue
            }
        }
        append(ch)
        i++
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = C.Text2) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Para -> Text(
                    inlineMarkdown(block.text), color = color,
                    style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp
                )
                is MdBlock.Heading -> Text(
                    inlineMarkdown(block.text), color = C.Text, fontWeight = FontWeight.SemiBold,
                    fontSize = when (block.level) { 1 -> 22.sp; 2 -> 19.sp; else -> 17.sp }, lineHeight = 26.sp
                )
                is MdBlock.Bullet -> Row(Modifier.padding(start = (block.indent * 14).dp)) {
                    Text(block.marker, color = C.Accent, modifier = Modifier.width(24.dp), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        inlineMarkdown(block.text), color = color, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp
                    )
                }
                is MdBlock.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().background(C.BorderStrong))
                    Spacer(Modifier.width(10.dp))
                    Text(inlineMarkdown(block.text), color = C.Dim, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp)
                }
                is MdBlock.Code -> CodeBlock(block.lang, block.code)
                is MdBlock.Table -> Surface(
                    shape = RoundedCornerShape(10.dp), color = C.Code,
                    border = BorderStroke(1.dp, C.Border), modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        block.text, color = Color(0xFFD8DEF4), fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp, lineHeight = 18.sp,
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)
                    )
                }
                MdBlock.Rule -> HorizontalDivider(color = C.Border)
            }
        }
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        shape = RoundedCornerShape(12.dp), color = C.Code,
        border = BorderStroke(1.dp, C.Border), modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(C.CodeHeader).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lang.ifBlank { "código" }, color = C.Dim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.ContentCopy, "Copiar código", tint = C.Dim, modifier = Modifier.size(16.dp))
                }
            }
            Text(
                code, color = Color(0xFFD8DEF4), fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp, lineHeight = 18.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
            )
        }
    }
}
