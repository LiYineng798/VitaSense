# AI Chat Design

## Goal

Add a prominent AI chat entry to the center of the floating bottom tab bar. Tapping it opens a dedicated chat page where the user can talk with an AI assistant about their current VitaSense state. The feature reuses the existing AI provider settings and API key, routes model calls through the VitaSense backend proxy, streams assistant responses, and stores chat history locally in Room.

## Scope

In scope:

- Add a center AI action button to the existing floating bottom tab shell.
- Add a new `AiChatFragment` destination.
- Hide the floating bottom tab shell while the chat page is open.
- Add local Room storage for chat sessions and messages.
- Reuse `SettingsRepository` AI provider config: provider, API key, base URL, and model.
- Send recent health context with each chat request.
- Add a Python backend streaming chat proxy endpoint.
- Support new conversation, delete conversation, message history, input, send, loading, streaming, and error states.

Out of scope for this first version:

- Syncing chat history to the backend account.
- Backend persistence of chat messages.
- Voice input, attachments, images, or tool calls.
- Medical diagnosis or emergency guidance.

## UX

The existing four primary tabs remain:

- Home
- Trends
- Mood
- Profile

A visually larger circular AI button is placed in the middle of the floating bottom tab bar, between Trends and Mood. This button is an independent action, not a fifth selected tab. It does not participate in `BottomTabDestination`, the liquid tab indicator, or top-level tab selection state.

When tapped, the app navigates to `AiChatFragment`. The chat page is treated as a secondary destination, so the floating bottom tab shell is hidden. This avoids conflict between the chat input bar and the app-level tab bar.

The chat page contains:

- A top bar with Back, New Chat, and Delete Chat actions.
- A scrollable message list.
- A fixed bottom composer with text input and Send button.
- A visible generating state while the backend is streaming.
- Inline error feedback when a response fails.
- A setup prompt when AI settings are incomplete.

## Android Architecture

### Navigation

Add `aiChatFragment` to `main_nav_graph.xml`.

`FloatingTabShellDestinationPolicy` keeps the chat page out of the top-level destination set, so the tab shell hides automatically when navigating to chat.

`MainActivity` binds the new center AI action in `view_floating_bottom_tabs.xml` and navigates to `R.id.aiChatFragment`. It leaves `BottomTabDestination` unchanged so the existing selected-tab behavior stays stable.

### UI Layer

Add package:

```text
org.wit.vitasense.ui.aichat
```

Primary classes:

- `AiChatFragment`
- `AiChatViewModel`
- `AiChatMessageAdapter`
- `AiChatUiModels`

`AiChatViewModel` exposes a `StateFlow<AiChatScreenState>` with:

- selected session id
- session title
- message items
- input enabled state
- streaming/generating state
- setup-required state
- error text

User actions:

- `sendMessage(text)`
- `startNewChat()`
- `deleteCurrentChat()`
- `retryLastFailedMessage()` if the final UI includes retry affordance

### Data Layer

Add repository interface:

```text
org.wit.vitasense.repository.AiChatRepository
```

Add implementation:

```text
org.wit.vitasense.data.repository.DefaultAiChatRepository
```

Responsibilities:

- Create and select local sessions.
- Persist user messages.
- Insert an assistant placeholder message before streaming begins.
- Append streamed chunks to the assistant message as they arrive.
- Mark assistant message as `complete` or `failed`.
- Delete local sessions and their messages.
- Build request payload from AI config, conversation history, and recent health context.

### Room

Add entities:

- `AiChatSessionEntity`
- `AiChatMessageEntity`

Suggested fields:

`AiChatSessionEntity`

- `id: Long`
- `title: String`
- `createdAt: Long`
- `updatedAt: Long`
- `isCurrent: Boolean`

`AiChatMessageEntity`

- `id: Long`
- `sessionId: Long`
- `role: String`
- `content: String`
- `createdAt: Long`
- `status: String`
- `errorMessage: String?`

Message roles are `system`, `user`, and `assistant`; only user and assistant messages are displayed. Message statuses include `sending`, `streaming`, `complete`, and `failed`.

Add DAOs:

- `AiChatSessionDao`
- `AiChatMessageDao`

The database version must be incremented. The current app already uses destructive migration fallback, but tests should still verify DAO behavior.

### Health Context

Each chat request includes a compact health context:

- latest risk assessment record
- last 7 daily physiology summaries
- latest mood record for the most recent available date or today when available

The context is sent as structured JSON. The backend converts it into a system message that tells the model:

- VitaSense supports health trend review and stress management.
- The assistant should help the user reflect on current state and practical next steps.
- The assistant must not provide medical diagnosis.
- Urgent or severe symptoms should direct the user to professional or emergency help.

## Backend Architecture

Add endpoint to `python_auth_api/main.py`:

```text
POST /api/v1/ai/chat/stream
```

The endpoint accepts:

- provider
- base URL
- model
- API key
- messages
- health context

It validates the payload using Pydantic, then calls an OpenAI-compatible chat completions endpoint with `stream=true`.

DeepSeek is handled as OpenAI-compatible because the existing config already defaults to DeepSeek's OpenAI-compatible API.

The backend streams text chunks back to Android. It does not store messages or sessions.

Error mapping follows the existing AI advice behavior:

- missing API key
- missing base URL
- missing model
- invalid API key
- model unavailable
- quota or rate limit
- provider network error
- unexpected provider response

## Streaming Protocol

Android should consume a simple line-based response from the VitaSense backend. The backend can emit Server-Sent-Event-style lines:

```text
data: {"delta":"..."}
data: {"done":true}
```

On error:

```text
data: {"error":{"code":"invalid_api_key","message":"The API key is invalid or expired."}}
```

Android parses each line, appends `delta` values to the in-progress assistant message, completes on `done`, and marks failed on `error` or transport failure.

## Error Handling

If AI settings are incomplete, the chat page shows a setup prompt and disables Send.

If a request fails before streaming begins, the user message remains and the assistant placeholder becomes failed with the mapped error.

If a request fails mid-stream, the partial assistant text remains visible and the message status becomes failed with an inline explanation.

While a response is streaming:

- Send is disabled for the current conversation.
- A generating indicator is shown.
- The active assistant message updates incrementally.

Deleting a conversation removes local Room rows only.

## Testing

Android unit tests:

- DAO insert/query/delete for sessions and messages.
- repository creates a session on first send.
- repository persists user and assistant messages.
- repository appends streamed chunks in order.
- repository marks assistant message failed on stream error.
- health context builder includes latest risk, last 7 summaries, and latest mood.
- ViewModel exposes setup-required state when AI config is incomplete.
- ViewModel handles new chat and delete chat.

Backend tests:

- payload validation rejects incomplete config.
- unsupported provider returns mapped error.
- provider HTTP errors map to existing AI error codes.
- simulated stream returns line-based deltas and done event.

Instrumentation smoke tests may cover:

- AI button opens chat page.
- incomplete AI settings show setup prompt.
- new chat and delete controls are visible.

## Open Decisions Resolved

- AI calls use the backend proxy, not direct Android-to-provider calls.
- Chat history is stored only in local Room for the first version.
- Recent health context is attached automatically to chat requests.
- Chat is a secondary page and hides the floating tab shell.
