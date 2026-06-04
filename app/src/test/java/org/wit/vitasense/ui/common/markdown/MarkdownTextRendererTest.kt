package org.wit.vitasense.ui.common.markdown

import android.graphics.Typeface
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTextRendererTest {
    @Test
    fun heading_markers_are_removed_and_heading_style_is_applied() {
        val rendered = MarkdownTextRenderer.render("## Recovery plan")

        assertEquals("Recovery plan", rendered.toString())
        val boldSpans = rendered.getSpans(0, rendered.length, StyleSpan::class.java)
        val sizeSpans = rendered.getSpans(0, rendered.length, RelativeSizeSpan::class.java)
        assertTrue(boldSpans.any { it.style == Typeface.BOLD })
        assertTrue(sizeSpans.any { it.sizeChange > 1.0f })
    }

    @Test
    fun bold_markers_are_removed_and_inner_text_is_bold() {
        val rendered = MarkdownTextRenderer.render("Keep **hydration** steady")

        assertEquals("Keep hydration steady", rendered.toString())
        val start = rendered.toString().indexOf("hydration")
        val end = start + "hydration".length
        val boldSpans = rendered.getSpans(start, end, StyleSpan::class.java)
        assertTrue(boldSpans.any { it.style == Typeface.BOLD })
    }

    @Test
    fun bullet_markers_are_normalized() {
        val rendered = MarkdownTextRenderer.render("- Drink water\n* Take a walk")

        assertEquals("• Drink water\n• Take a walk", rendered.toString())
    }

    @Test
    fun numbered_list_markers_are_preserved() {
        val rendered = MarkdownTextRenderer.render("1. Breathe\n2. Sleep early")

        assertEquals("1. Breathe\n2. Sleep early", rendered.toString())
    }

    @Test
    fun unmatched_bold_marker_remains_readable() {
        val rendered = MarkdownTextRenderer.render("Rest **earlier tonight")

        assertEquals("Rest **earlier tonight", rendered.toString())
    }
}
