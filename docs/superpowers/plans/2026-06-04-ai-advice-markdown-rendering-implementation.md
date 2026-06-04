# AI Advice Markdown Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render AI advice Markdown as readable rich text in the Dashboard AI Advice card.

**Architecture:** Add a focused `MarkdownTextRenderer` utility that converts a limited Markdown subset into Android `Spannable` text. Keep AI models, repositories, backend APIs, and persistence unchanged; `DashboardFragment` only builds the Markdown source and binds the rendered result to the existing `TextView`.

**Tech Stack:** Kotlin, Android `SpannableStringBuilder`, `StyleSpan`, `RelativeSizeSpan`, XML Views, ViewBinding, JUnit, Gradle Android build gates.

---

## Scope Check

This plan is a presentation-only change. It does not add a third-party Markdown dependency, WebView, backend changes, AI prompt changes, clickable links, images, tables, code blocks, or full CommonMark parsing.

---

## File Map

- Create `app/src/main/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRenderer.kt`
  - Converts supported Markdown text into a `Spannable`.
- Create `app/src/test/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRendererTest.kt`
  - Covers headings, bold spans, bullet normalization, numbered lists, and malformed input.
- Modify `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`
  - Builds Markdown source from `DashboardAiAdviceState`.
  - Uses `MarkdownTextRenderer.render(...)` for `aiAdviceSummaryText`.

---

### Task 1: Markdown Renderer

**Files:**
- Create: `app/src/main/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRenderer.kt`
- Create: `app/src/test/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRendererTest.kt`

- [ ] **Step 1: Add failing renderer tests**

Create `app/src/test/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRendererTest.kt`:

```kotlin
package org.wit.vitasense.ui.common.markdown

import android.graphics.Typeface
import android.text.Spanned
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
```

- [ ] **Step 2: Run compile and confirm failure**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: fails because `MarkdownTextRenderer` does not exist.

- [ ] **Step 3: Implement renderer**

Create `app/src/main/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRenderer.kt`:

```kotlin
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
```

- [ ] **Step 4: Run compile and verify pass**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```powershell
git add app/src/main/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRenderer.kt app/src/test/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRendererTest.kt
git commit -m "feat: add markdown text renderer"
```

---

### Task 2: Dashboard AI Advice Integration

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`

- [ ] **Step 1: Import renderer**

In `app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt`, add:

```kotlin
import org.wit.vitasense.ui.common.markdown.MarkdownTextRenderer
```

- [ ] **Step 2: Replace plain text binding with rendered Markdown**

In `DashboardFragment.renderAiAdvice(ai: DashboardAiAdviceState)`, replace the existing `summaryText` builder and text assignment with:

```kotlin
val markdownText =
    buildString {
        if (ai.summary.isNotBlank()) {
            append(ai.summary.trim())
        }
        if (ai.recommendations.isNotEmpty()) {
            if (isNotBlank()) append("\n\n")
            ai.recommendations.forEach { recommendation ->
                append("- ").append(recommendation.trim()).append("\n")
            }
        }
        if (ai.riskNote.isNotBlank()) {
            if (isNotBlank()) append("\n\n")
            append(ai.riskNote.trim())
        }
        if (ai.disclaimer.isNotBlank()) {
            if (isNotBlank()) append("\n\n")
            append(ai.disclaimer.trim())
        }
    }.trim()
binding.aiAdviceSummaryText.text =
    if (markdownText.isBlank()) {
        ""
    } else {
        MarkdownTextRenderer.render(markdownText)
    }
binding.aiAdviceSummaryText.visibility = if (markdownText.isBlank()) View.GONE else View.VISIBLE
```

This intentionally includes `riskNote`, which is already present in `DashboardAiAdviceState` but was not displayed by the old plain-text builder.

- [ ] **Step 3: Run Android compile**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Run Android assemble**

Run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```powershell
git add app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt
git commit -m "feat: render ai advice markdown"
```

---

### Task 3: Final Verification

**Files:**
- Modify only files already touched if verification exposes issues.

- [ ] **Step 1: Run Android compile gate**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run Android build gate**

Run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Attempt targeted renderer tests**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "org.wit.vitasense.ui.common.markdown.MarkdownTextRendererTest"
```

Expected if the local Gradle worker issue is fixed: tests pass.

If it fails with:

```text
ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain
```

Record it as the known local Gradle Test Executor issue. Do not change application code for that environment failure.

- [ ] **Step 4: Manual UI check**

Run the debug build and generate AI advice containing this Markdown:

```text
## Recovery Focus

Your current pattern is **mostly stable**.

- Keep hydration steady
- Take a short walk
- Sleep earlier tonight

Not a diagnosis.
```

Expected:

- `Recovery Focus` appears without `##` and is visually stronger.
- `mostly stable` appears bold without `**`.
- list items show clear bullet markers.
- paragraph spacing remains readable.
- raw Markdown markers are not visible for supported syntax.

- [ ] **Step 5: Commit verification fixes if needed**

If fixes were required:

```powershell
git add app/src/main/java/org/wit/vitasense/ui/common/markdown app/src/main/java/org/wit/vitasense/ui/dashboard/DashboardFragment.kt app/src/test/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRendererTest.kt
git commit -m "fix: polish ai advice markdown rendering"
```

If no fixes were required, skip this commit.

---

## Self-Review

Spec coverage:

- Headings: Task 1 renderer tests and implementation cover `#`, `##`, and `###`.
- Bold spans: Task 1 tests and implementation cover `**text**`.
- Bullet lists: Task 1 tests and implementation cover `-` and `*`.
- Numbered lists: Task 1 tests and implementation preserve numbered markers.
- Malformed input: Task 1 unmatched bold test covers readable fallback.
- Dashboard integration: Task 2 uses the renderer in `DashboardFragment`.
- No backend or dependency changes: no backend files or Gradle dependencies are in the file map.

Completeness scan:

- No incomplete deferred steps remain.
- Every code-changing step includes concrete snippets.
- All commands include expected outcomes.

Type consistency:

- Renderer API is `MarkdownTextRenderer.render(markdown: String): Spannable`.
- Test and Dashboard integration use the same API.
