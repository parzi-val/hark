package com.hark.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hark.ui.theme.Hark
import com.hark.ui.theme.HarkMono
import com.hark.ui.theme.HarkSerif
import com.hark.ui.theme.HarkType

/** Flattens markdown to plain text for truncated previews (stream / grid / shelf snippets). */
fun stripMarkdown(s: String): String = (s)
    .replace(Regex("""```[\s\S]*?```"""), " ")
    .replace(Regex("""`([^`]+)`"""), "$1")
    .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
    .replace(Regex("""(?m)^#{1,6}\s+"""), "")
    .replace(Regex("""(?m)^\s*[-*+]\s+"""), "")
    .replace(Regex("""(?m)^\s*\d+\.\s+"""), "")
    .replace(Regex("""(?m)^\s*>\s?"""), "")
    .replace(Regex("""(\*\*|__)(.*?)\1"""), "$2")
    .replace(Regex("""(\*|_)(.*?)\1"""), "$2")
    .replace(Regex("""~~(.*?)~~"""), "$2")
    .replace(Regex("""\s+"""), " ")
    .trim()

/**
 * Clean, lightweight Hark Markdown renderer.
 * Formats paragraphs, ## headings, bullet lists, quotes, bold, italic, and inline code.
 */
@Composable
fun HarkMarkdown(
    text: String,
    modifier: Modifier = Modifier,
) {
    val c = Hark.colors
    val lines = text.lines()

    Column(modifier = modifier.fillMaxWidth()) {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> {
                    Spacer(Modifier.height(10.dp))
                    i++
                }
                trimmed.startsWith("## ") || trimmed.startsWith("### ") || trimmed.startsWith("# ") -> {
                    val headingText = trimmed.replace(Regex("""^#{1,3}\s+"""), "")
                    Text(
                        text = headingText,
                        style = TextStyle(
                            fontFamily = HarkSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp,
                            lineHeight = 26.sp,
                        ),
                        color = c.ink,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    i++
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val bulletContent = trimmed.substring(2)
                    Row(modifier = Modifier.padding(vertical = 2.dp, horizontal = 4.dp)) {
                        Text(
                            text = "•",
                            style = HarkType.body,
                            color = c.rust,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = parseInlineMarkdown(bulletContent),
                            style = HarkType.body,
                            color = c.ink,
                        )
                    }
                    i++
                }
                trimmed.startsWith("> ") -> {
                    val quoteContent = trimmed.substring(2)
                    Text(
                        text = parseInlineMarkdown(quoteContent),
                        style = HarkType.body.copy(fontStyle = FontStyle.Italic),
                        color = c.inkMuted,
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                    )
                    i++
                }
                else -> {
                    Text(
                        text = parseInlineMarkdown(line),
                        style = HarkType.body,
                        color = c.ink,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                    i++
                }
            }
        }
    }
}

/** Parses bold (`**`), italic (`*`), inline code (`` ` ``), and links (`[text](url)`). */
@Composable
private fun parseInlineMarkdown(text: String): AnnotatedString {
    val c = Hark.colors
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("""(\*\*(.*?)\*\*|\*(.*?)\*|`([^`]+)`|\[([^\]]+)]\(([^)]*)\))""")
        val matches = regex.findAll(text)

        for (match in matches) {
            val range = match.range
            if (range.first > cursor) {
                append(text.substring(cursor, range.first))
            }

            val value = match.value
            when {
                value.startsWith("**") && value.endsWith("**") -> {
                    val inner = value.removeSurrounding("**")
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                }
                value.startsWith("*") && value.endsWith("*") -> {
                    val inner = value.removeSurrounding("*")
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                }
                value.startsWith("`") && value.endsWith("`") -> {
                    val inner = value.removeSurrounding("`")
                    val start = length
                    append(inner)
                    addStyle(
                        SpanStyle(
                            fontFamily = HarkMono,
                            fontSize = 13.sp,
                            color = c.rust,
                        ),
                        start,
                        length,
                    )
                }
                value.startsWith("[") && value.contains("](") -> {
                    val label = match.groupValues[5]
                    val start = length
                    append(label)
                    addStyle(
                        SpanStyle(
                            color = c.rust,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        start,
                        length,
                    )
                }
                else -> append(value)
            }
            cursor = range.last + 1
        }

        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}
