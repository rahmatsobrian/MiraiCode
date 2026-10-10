package com.rahmatsobrian.miraicode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ───────────── Util salin ─────────────
fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("MiraiCode", text))
    // Android 13+ sudah menampilkan konfirmasi sendiri
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Disalin", Toast.LENGTH_SHORT).show()
}

// ───────────── Model + parser ─────────────
sealed interface Md {
    data class Heading(val level: Int, val text: String) : Md
    data class Para(val text: String) : Md
    data class Code(val lang: String, val code: String) : Md
    data class Item(val indent: Int, val marker: String, val text: String) : Md
    data class Items(val items: List<Item>) : Md
    data class Quote(val text: String) : Md
    data class Table(val rows: List<List<String>>) : Md
    data object Rule : Md
}

private val reFence = Regex("^\\s*(`{3,}|~{3,})\\s*([\\w+#.-]*).*$")
private val reHead = Regex("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$")
private val reRule = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
private val reItem = Regex("^(\\s*)([-*+]|\\d{1,3}[.)])\\s+(.*)$")
private val reSep = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

private fun splitRow(l: String) = l.trim().trim('|').split('|').map { it.trim() }

fun parseMarkdown(src: String): List<Md> {
    val lines = src.replace("\r\n", "\n").replace("\t", "    ").split("\n")
    val out = mutableListOf<Md>()
    val para = StringBuilder()
    fun flush() { if (para.isNotBlank()) out += Md.Para(para.toString().trimEnd()); para.setLength(0) }
    var i = 0
    while (i < lines.size) {
        val l = lines[i]
        val f = reFence.matchEntire(l)
        if (f != null) {
            flush()
            val fence = f.groupValues[1]; val lang = f.groupValues[2]
            val code = mutableListOf<String>(); i++
            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.length >= fence.length && t.all { it == fence[0] }) break
                code += lines[i]; i++
            }
            i++ // lewati pagar penutup (kalau tidak ada = blok belum selesai, tetap dirender)
            out += Md.Code(lang, code.joinToString("\n").trimEnd('\n'))
            continue
        }
        if (l.isBlank()) { flush(); i++; continue }
        val h = reHead.matchEntire(l)
        if (h != null) { flush(); out += Md.Heading(h.groupValues[1].length, h.groupValues[2]); i++; continue }
        if (reRule.matches(l)) { flush(); out += Md.Rule; i++; continue }
        if (l.contains('|') && i + 1 < lines.size && lines[i + 1].contains('-') && reSep.matches(lines[i + 1])) {
            flush()
            val rows = mutableListOf(splitRow(l)); i += 2
            while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) { rows += splitRow(lines[i]); i++ }
            out += Md.Table(rows); continue
        }
        if (l.trimStart().startsWith(">")) {
            flush()
            val q = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) { q += lines[i].trimStart().removePrefix(">").removePrefix(" "); i++ }
            out += Md.Quote(q.joinToString("\n")); continue
        }
        val m = reItem.matchEntire(l)
        if (m != null) {
            flush()
            val marker = m.groupValues[2].let { if (it[0].isDigit()) it else "•" }
            out += Md.Item(m.groupValues[1].length / 2, marker, m.groupValues[3]); i++; continue
        }
        val last = out.lastOrNull()
        if (para.isEmpty() && last is Md.Item && l.startsWith("  ")) {   // lanjutan baris item daftar
            out[out.lastIndex] = last.copy(text = last.text + "\n" + l.trim()); i++; continue
        }
        para.append(l).append('\n'); i++
    }
    flush()
    // gabungkan Item berurutan jadi satu blok daftar
    val merged = mutableListOf<Md>()
    for (b in out) {
        val prev = merged.lastOrNull()
        if (b is Md.Item) {
            if (prev is Md.Items) merged[merged.lastIndex] = Md.Items(prev.items + b) else merged += Md.Items(listOf(b))
        } else merged += b
    }
    return merged
}

// ───────────── Inline (bold, italic, code, link, dst.) ─────────────
private class InlineStyle(val codeBg: Color, val link: Color)

private fun AnnotatedString.Builder.inline(s: String, st: InlineStyle) {
    val plain = StringBuilder()
    fun flush() { if (plain.isNotEmpty()) { append(plain.toString()); plain.setLength(0) } }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '\\' && i + 1 < s.length && s[i + 1] in "\\`*_{}[]()#+-.!|~>") { plain.append(s[i + 1]); i += 2; continue }
        if (c == '`') {
            var n = 0; while (i + n < s.length && s[i + n] == '`') n++
            val end = s.indexOf("`".repeat(n), i + n)
            if (end >= 0) {
                flush()
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = st.codeBg, fontSize = 0.9.em)) { append(s.substring(i + n, end).trim(' ')) }
                i = end + n; continue
            }
            plain.append(s, i, i + n); i += n; continue
        }
        if (s.startsWith("**", i) || s.startsWith("__", i)) {
            val d = s.substring(i, i + 2); val end = s.indexOf(d, i + 2)
            if (end > i + 2) {
                flush(); withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { inline(s.substring(i + 2, end), st) }
                i = end + 2; continue
            }
        }
        if (s.startsWith("~~", i)) {
            val end = s.indexOf("~~", i + 2)
            if (end > i + 2) {
                flush(); withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { inline(s.substring(i + 2, end), st) }
                i = end + 2; continue
            }
        }
        if (c == '*' || c == '_') {
            val prevOk = c == '*' || i == 0 || !s[i - 1].isLetterOrDigit()
            var end = i + 1
            while (true) {
                end = s.indexOf(c, end)
                if (end >= 0 && end + 1 < s.length && s[end + 1] == c) { end += 2; continue }
                break
            }
            if (prevOk && end > i + 1 && !s[i + 1].isWhitespace() && !s[end - 1].isWhitespace() &&
                (c == '*' || end + 1 >= s.length || !s[end + 1].isLetterOrDigit())) {
                flush(); withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { inline(s.substring(i + 1, end), st) }
                i = end + 1; continue
            }
        }
        if (c == '[') {
            val close = s.indexOf("](", i)
            val endp = if (close > i) s.indexOf(')', close + 2) else -1
            if (close > i && endp > 0) {
                val url = s.substring(close + 2, endp).trim()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    flush()
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = st.link, textDecoration = TextDecoration.Underline)))) {
                        append(s.substring(i + 1, close))
                    }
                    i = endp + 1; continue
                }
            }
        }
        if (c == 'h' && (s.startsWith("http://", i) || s.startsWith("https://", i)) && (i == 0 || !s[i - 1].isLetterOrDigit())) {
            var e = i
            while (e < s.length && !s[e].isWhitespace() && s[e] !in "<>\"") e++
            while (e > i && s[e - 1] in ".,;:!?)*_") e--
            if (e > i + 8) {
                flush()
                val url = s.substring(i, e)
                withLink(LinkAnnotation.Url(url, TextLinkStyles(SpanStyle(color = st.link, textDecoration = TextDecoration.Underline)))) { append(url) }
                i = e; continue
            }
        }
        plain.append(c); i++
    }
    flush()
}

@Composable
private fun rememberInline(t: String, st: InlineStyle): AnnotatedString =
    remember(t, st.codeBg, st.link) { buildAnnotatedString { inline(t, st) } }

// ───────────── Composable ─────────────
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseMarkdown(text) }
    val fg = LocalContentColor.current
    val st = remember(fg) { InlineStyle(codeBg = fg.copy(alpha = 0.14f), link = fg) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { MdBlock(it, st) }
    }
}

@Composable
private fun MdBlock(b: Md, st: InlineStyle) {
    val line = LocalContentColor.current.copy(alpha = 0.25f)
    when (b) {
        is Md.Heading -> Text(
            rememberInline(b.text, st), Modifier.padding(top = 4.dp), fontWeight = FontWeight.Bold,
            style = when (b.level) { 1 -> MaterialTheme.typography.titleLarge; 2 -> MaterialTheme.typography.titleMedium; else -> MaterialTheme.typography.titleSmall }
        )
        is Md.Para -> Text(rememberInline(b.text, st))
        is Md.Code -> CodeBlock(b.lang, b.code)
        is Md.Items -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            b.items.forEach { it ->
                Row(Modifier.padding(start = (it.indent * 16).dp)) {
                    Text(if (it.marker == "•" && it.indent > 0) "◦" else it.marker, Modifier.widthIn(min = 22.dp))
                    Text(rememberInline(it.text, st), Modifier.weight(1f))
                }
            }
        }
        is Md.Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(line, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Text(rememberInline(b.text, st), fontStyle = FontStyle.Italic)
        }
        Md.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        is Md.Table -> {
            val cols = b.rows.maxOf { it.size }.coerceAtLeast(1)
            val w = 130.dp
            Column(Modifier.horizontalScroll(rememberScrollState()).border(1.dp, line, RoundedCornerShape(8.dp))) {
                b.rows.forEachIndexed { r, row ->
                    Row(if (r == 0) Modifier.background(line.copy(alpha = 0.12f)) else Modifier) {
                        for (c in 0 until cols) Text(
                            rememberInline(row.getOrElse(c) { "" }, st), Modifier.width(w).padding(8.dp),
                            fontSize = 13.sp, fontWeight = if (r == 0) FontWeight.Bold else null
                        )
                    }
                    if (r < b.rows.lastIndex) Box(Modifier.width(w * cols).height(1.dp).background(line))
                }
            }
        }
        is Md.Item -> Unit
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1500); copied = false } }
    Surface(color = cs.surfaceContainerLow, contentColor = cs.onSurface, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, cs.outlineVariant.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(lang.ifBlank { "kode" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                TextButton(
                    onClick = { copyToClipboard(ctx, code); copied = true },
                    modifier = Modifier.height(36.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (copied) "Disalin" else "Salin", style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
                Text(code, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, softWrap = false)
            }
        }
    }
}
