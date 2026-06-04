# AI Advice Markdown Rendering Design

## Goal

Render AI advice content as readable rich text on the Dashboard instead of showing raw Markdown-like plain text.

The user should be able to read AI responses with clear headings, bold emphasis, bullet lists, and paragraph spacing.

## Current Behavior

`DashboardFragment.renderAiAdvice()` builds one plain string from:

- `DashboardAiAdviceState.summary`
- `DashboardAiAdviceState.recommendations`
- `DashboardAiAdviceState.disclaimer`

Then it assigns that plain string to `aiAdviceSummaryText`.

If the AI returns Markdown syntax such as `## Recovery`, `**Important**`, or `- Drink water`, the Dashboard currently shows those symbols literally.

## Chosen Approach

Add a small Android-local Markdown renderer that converts the subset of Markdown commonly returned by the AI into a `Spannable`.

This avoids backend changes and avoids adding a third-party Markdown dependency for a narrow display need.

## Supported Markdown Subset

The renderer supports:

- Headings:
  - `# Title`
  - `## Title`
  - `### Title`
- Bold spans:
  - `**text**`
- Bullet lists:
  - `- item`
  - `* item`
- Numbered lists:
  - `1. item`
  - `2. item`
- Paragraph spacing through blank lines.

The renderer does not support:

- Images.
- Tables.
- Inline HTML.
- Clickable links.
- Code blocks.
- Nested lists.

Unsupported syntax remains readable as plain text.

## Rendering Rules

- Headings render without the leading `#` markers.
- Headings are bold and slightly larger than body text.
- Bullet list markers are normalized to `•`.
- Numbered list markers are preserved.
- Bold markers are removed and the inner text is bold.
- Blank lines create paragraph separation.
- The renderer must not throw on malformed Markdown such as an unmatched `**`.

## Dashboard Integration

`DashboardFragment.renderAiAdvice()` will continue to own display binding.

It will build a Markdown source string from the existing AI state:

- summary first.
- recommendations as bullet lines.
- risk note if present.
- disclaimer last.

Then it will call the renderer and assign the result to `aiAdviceSummaryText`.

The data model remains unchanged.

## Architecture

Create a small focused utility:

```text
app/src/main/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRenderer.kt
```

Responsibility:

- Convert a Markdown string into a `Spannable`.
- Own Markdown parsing and Android span application.
- Avoid dependencies on Dashboard-specific state.

Dashboard responsibility:

- Build the Markdown source from `DashboardAiAdviceState`.
- Bind rendered text to the existing TextView.

## Testing

Add JVM unit tests for the renderer:

- headings remove markers and apply style spans.
- bold removes markers and applies `StyleSpan(Typeface.BOLD)`.
- bullet list normalizes markers to `•`.
- malformed bold text remains readable.

Run:

- `.\gradlew.bat --no-daemon :app:compileDebugUnitTestKotlin`
- `.\gradlew.bat --no-daemon :app:assembleDebug`

Attempt targeted tests:

- `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "org.wit.vitasense.ui.common.markdown.MarkdownTextRendererTest"`

If targeted tests hit the known local Gradle Test Executor `GradleWorkerMain` issue, record that as an environment failure and rely on compile/build gates.

## Out Of Scope

- Backend response changes.
- AI prompt changes.
- Storing rendered HTML.
- WebView rendering.
- Full CommonMark compatibility.
- Clickable links.
