package org.wit.vitasense.ui.common.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

object MarkdownTextRenderer {
    private val headingPattern = Regex("^(#{1,3})\\s+(.+)$")
    private val bulletPattern = Regex("^[-*]\\s+(.+)$")
    private val numberedPattern = Regex("^(\\d+\\.\\s+)(.+)$")

    fun render(markdown: String): Spannable {
        val builder = SpannableStringBuilder()
        markdown
            .lineSequence()
            .forEachIndexed { index, rawLine ->
                if (index > 0) {
                    builder.append('\n')
                }
                appendLine(builder, rawLine)
            }
        return builder
    }

    private fun appendLine(
        builder: SpannableStringBuilder,
        rawLine: String,
    ) {
        val line = rawLine.trimEnd()
        val headingMatch = headingPattern.matchEntire(line)
        if (headingMatch != null) {
            appendHeading(builder, headingMatch.groupValues[1].length, headingMatch.groupValues[2])
            return
        }

        val bulletMatch = bulletPattern.matchEntire(line)
        if (bulletMatch != null) {
            builder.append("• ")
            appendBoldText(builder, bulletMatch.groupValues[1])
            return
        }

        val numberedMatch = numberedPattern.matchEntire(line)
        if (numberedMatch != null) {
            builder.append(numberedMatch.groupValues[1])
            appendBoldText(builder, numberedMatch.groupValues[2])
            return
        }

        appendBoldText(builder, line)
    }

    private fun appendHeading(
        builder: SpannableStringBuilder,
        level: Int,
        text: String,
    ) {
        val start = builder.length
        appendBoldText(builder, text.trim())
        val end = builder.length
        builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(RelativeSizeSpan(headingSize(level)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun headingSize(level: Int): Float =
        when (level) {
            1 -> 1.24f
            2 -> 1.16f
            else -> 1.08f
        }

    private fun appendBoldText(
        builder: SpannableStringBuilder,
        text: String,
    ) {
        var cursor = 0
        while (cursor < text.length) {
            val open = text.indexOf("**", cursor)
            if (open == -1) {
                builder.append(text.substring(cursor))
                return
            }
            val close = text.indexOf("**", open + 2)
            if (close == -1) {
                builder.append(text.substring(cursor))
                return
            }

            builder.append(text.substring(cursor, open))
            val boldStart = builder.length
            builder.append(text.substring(open + 2, close))
            val boldEnd = builder.length
            builder.setSpan(StyleSpan(Typeface.BOLD), boldStart, boldEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            cursor = close + 2
        }
    }
}
