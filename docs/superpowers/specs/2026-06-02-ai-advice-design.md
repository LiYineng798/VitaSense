# AI Advice Design

## Goal

Add a real network-backed AI advice feature that generates daily wellness suggestions from the user's latest VitaSense health data. The feature must call an AI service only when the user explicitly taps a button, because generated advice can consume user API quota.

Users provide their own API key. The Android app stores the user's AI configuration locally and sends it to the existing Python backend only for the current request. The backend acts as a request proxy and must not persist API keys.

## Scope

In scope:

- Add an AI Advice card to the Home screen.
- Add AI provider configuration to Settings.
- Add a Python backend endpoint for AI advice generation.
- Support DeepSeek and a custom OpenAI-compatible provider in the first version.
- Save the latest successful advice locally so Home can show it without another AI call.
- Show loading and specific error feedback for common failures.

Out of scope for the first version:

- Automatic advice generation on app launch, navigation, or data change.
- Hard daily quota enforcement.
- Long-term server-side storage of advice or user API keys.
- MiniMax-specific adapter support. MiniMax can be added later once the exact current endpoint and model contract are selected.

## Entry Points

### Home

Add an `AI Advice` card below the health score and trend cards, before or near `Quick Mood Log`.

Card states:

- API key missing: show setup guidance and a button that navigates to Settings.
- Configured but no advice yet: show `Generate advice`.
- Generating: disable the action button and show progress with `Generating...`.
- Success: show the latest generated advice summary and a `Refresh advice` button.
- Error: show the specific error message and keep the action available for retry when appropriate.

The button is the only trigger for AI generation. The app must not call AI automatically when Home opens or when health data changes.

### Settings

Add an AI Provider section to Settings:

- Provider selector: `DeepSeek` or `Custom OpenAI-compatible`.
- API key field.
- Base URL field.
- Model field.

Default values:

- DeepSeek base URL: `https://api.deepseek.com`
- DeepSeek model: `deepseek-chat`
- Custom provider base URL: empty until user enters it.
- Custom provider model: empty until user enters it.

Settings text should make clear that API usage may consume the user's quota and that API keys are sent to the VitaSense backend only to perform the current request.

## Android Architecture

Add AI configuration and advice state to the local settings layer.

Suggested local setting keys:

- `ai_provider`
- `ai_api_key`
- `ai_base_url`
- `ai_model`
- `ai_latest_advice_json`
- `ai_latest_advice_generated_at`

Add an `AiAdviceRepository` interface and a default implementation that calls the Python backend.

The request uses the latest available data:

- latest summary date
- sleep duration
- HRV RMSSD
- resting heart rate
- average heart rate
- anomaly flags
- latest risk total score
- latest risk level
- existing rule-based suggestion text

`DashboardViewModel` should expose:

- current card state
- `generateAiAdvice()` action
- loading state
- error state

The ViewModel must ignore duplicate generation clicks while a request is already running.

## Python Backend Architecture

Add:

`POST /api/v1/ai/advice`

The endpoint receives:

- provider
- base URL
- model
- API key
- health summary payload

It validates required fields, builds a concise prompt, calls the selected AI service, and returns normalized advice JSON.

The Python service must not write the API key to disk, logs, or SQLite.

First-version provider behavior:

- `deepseek`: call `{base_url}/chat/completions` with Bearer auth.
- `openai_compatible`: call `{base_url}/chat/completions` with Bearer auth.

The response should be normalized to:

```json
{
  "summary": "Short overall guidance.",
  "recommendations": [
    "Concrete recommendation 1.",
    "Concrete recommendation 2.",
    "Concrete recommendation 3."
  ],
  "risk_note": "Brief explanation of what drove the advice.",
  "disclaimer": "This is wellness support, not medical diagnosis."
}
```

## Prompt Behavior

The system prompt should frame the assistant as a wellness support coach, not a medical doctor. It should:

- Use the provided metrics only.
- Avoid diagnosis.
- Recommend rest, load reduction, hydration, sleep hygiene, stress management, and monitoring when appropriate.
- Tell the user to seek professional medical help for severe or persistent symptoms.
- Return concise JSON only.

The user prompt should include the latest VitaSense data in a structured format and ask for 2 to 4 practical suggestions.

## Error Handling

The backend should map common failures to clear error codes. Android should convert those to user-facing messages.

Recommended errors:

- `missing_api_key`: `Add an API key in Settings first.`
- `missing_model`: `Add a model name in Settings first.`
- `missing_base_url`: `Add an AI base URL in Settings first.`
- `proxy_unreachable`: `Unable to reach the VitaSense AI proxy.`
- `ai_network_error`: `Unable to reach the AI service. Check your network or base URL.`
- `invalid_api_key`: `The API key is invalid or expired.`
- `model_unavailable`: `The selected model is not available. Check the model name.`
- `quota_or_rate_limit`: `The AI service quota or rate limit was reached.`
- `unexpected_ai_response`: `The AI service returned an unexpected response.`

HTTP mapping guidance:

- 400 from backend validation: missing configuration or invalid payload.
- 401 or 403 from AI provider: invalid API key.
- 404 from AI provider: model or endpoint unavailable.
- 402 or 429 from AI provider: quota or rate limit.
- 5xx or connection failure: network or provider service failure.

## Call Count Controls

The app must minimize AI calls:

- No automatic generation.
- Button-only generation.
- Disable the button while generation is in progress.
- Store and show the latest successful advice locally.
- If today's advice already exists, label the button `Refresh advice` so the user understands that clicking again makes another API call.

No hard daily limit is required in the first version, but the design should allow adding one later by checking `ai_latest_advice_generated_at`.

## Testing

Android unit tests:

- AI card state when API key is missing.
- AI card state with saved advice.
- duplicate generation click is ignored while loading.
- backend error codes map to correct messages.

Python tests:

- validation rejects missing API key, base URL, and model.
- provider response is normalized.
- provider 401 maps to `invalid_api_key`.
- provider 429 maps to `quota_or_rate_limit`.
- unexpected provider JSON maps to `unexpected_ai_response`.

Manual verification:

- Configure DeepSeek with a valid key and generate advice.
- Try an invalid key and confirm the user sees a key-specific error.
- Try an invalid model and confirm the user sees a model-specific error.
- Confirm returning to Home displays saved advice without making a new AI call.
