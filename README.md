# VitaSense

VitaSense is an Android mental-health-support and recovery-tracking app built with XML layouts, Fragments, Room, and a lightweight Python backend service.

The project combines:

- a native Android client for daily health review, trend exploration, mood logging, AI-supported reflection, family check-ins, and profile personalization
- a standalone Python API for authentication, family support, cloud sync, AI advice, and streaming AI chat proxying

## Core Experience

### Home

- shows the Health Assessment total score at the top
- includes a swipeable 7-day mini-trend area for Sleep, HRV, and Heart Rate
- provides a direct `Quick Mood Log` entry into the Mood page
- exposes account entry through the avatar area

### Trends

- supports `7 Days` and `30 Days` views
- uses custom line-chart-based visualizations instead of stock chart widgets
- includes recent micro-trends, daily status cards, a 30-day main trend, weekly snapshot comparison, recovery heatmap, and insight cards
- keeps floating tabs hidden on secondary pages so the chart views stay focused

### Assessment

- presents the total score, breakdown, explanation, and suggested action

### Mood

- supports creating, filtering, and deleting mood records
- supports date-based filtering and mood-category filtering

### AI Chat

- exposes a prominent center `AI` action in the floating bottom tab shell
- opens a dedicated secondary chat page with history, input, send, new chat, and delete chat controls
- streams assistant replies through the VitaSense backend AI proxy
- renders assistant Markdown responses in-app
- reuses the API key, base URL, model, and provider configured in Settings
- attaches compact recent health context to each request:
  - latest health assessment
  - recent daily physiology summaries
  - latest mood check-in
- stores chat sessions and messages locally in Room; chat history is not uploaded in this version

### Family

- supports creating or joining a family through invite codes
- supports owner actions such as renaming the family, regenerating invite codes, and removing members
- supports daily family status sharing from mood and optional health score data
- supports lightweight support actions between family members

### Profile, Appearance, and Settings

- `Profile` is the landing page for account state and secondary navigation
- `Appearance` is a dedicated secondary page for theme selection
- `Settings & Import` is a dedicated secondary page for demo import, cloud sync, AI provider settings, data reset, privacy notes, and disclaimers
- supports light/dark mode plus multiple theme families:
  - `Default`
  - `Olive Ember`
  - `Sunlit Meadow`
  - `Rose Indigo`

### Authentication

- Android client uses remote authentication first
- current default server base URL is `https://server.np5.top`
- successful sign in restores the session on next launch

### Cloud Sync and AI

- cloud sync can bootstrap and push theme settings, mood records, heart-rate samples, and sleep records after sign in
- imported health source data still recomputes derived local summaries and risk scores on-device
- AI Advice and AI Chat both route through the VitaSense backend proxy
- the configured provider API key is sent to the backend only for the current AI request
- AI chat history remains local in Room in this version

## Tech Stack

### Android

- Kotlin
- Android Views with XML + Fragments
- Navigation Component
- ViewBinding
- Material 3 components
- Room
- Coroutines + Flow
- custom chart views for trend rendering
- custom Markdown rendering for AI text
- custom floating bottom tab shell with an independent center AI action

### Python API

- FastAPI
- SQLite
- Pydantic
- OpenAI-compatible AI proxy calls
- streaming response support for AI chat

## Repository Layout

```text
project/
├─ app/                         Android application
│  ├─ src/main/java/org/wit/vitasense/
│  │  ├─ data/                  repository and import implementations
│  │  ├─ db/                    Room database, DAOs, entities
│  │  ├─ domain/                scoring, anomaly detection, recompute logic
│  │  ├─ model/                 shared domain and UI models
│  │  ├─ repository/            repository interfaces
│  │  ├─ ui/
│  │  │  ├─ auth/               login and registration
│  │  │  ├─ aichat/             AI chat page and message list
│  │  │  ├─ dashboard/          Home page
│  │  │  ├─ trends/             trend review UI and custom charts
│  │  │  ├─ assessment/         score explanation
│  │  │  ├─ mood/               mood logging
│  │  │  ├─ family/             family status and support sharing
│  │  │  ├─ profile/            Profile + Appearance
│  │  │  ├─ settings/           Settings & Import
│  │  │  ├─ common/             shared charts, Markdown, and factories
│  │  │  └─ navigation/         floating bottom tab shell
│  │  ├─ util/                  utilities such as password hashing
│  │  ├─ AppContainer.kt        dependency assembly
│  │  └─ MainActivity.kt        app shell and floating-tab host
│  └─ src/test + src/androidTest
├─ python_auth_api/             standalone backend API service
├─ docs/superpowers/            design and implementation notes produced during development
└─ README.md
```

## Android Architecture Notes

- The app uses a single-activity architecture with Fragment destinations declared in `app/src/main/res/navigation/main_nav_graph.xml`.
- Top-level tabs are:
  - `Home`
  - `Trends`
  - `Mood`
  - `Profile`
- The center `AI` bottom action is independent from top-level tab selection and opens `AI Chat`.
- Secondary pages such as `Auth`, `AI Chat`, `Appearance`, `Settings & Import`, and `Family` are still Fragment destinations, but the floating tab shell is hidden there.
- Local health data, mood records, settings, import logs, and AI chat history are stored in `vitasense.db` through Room.
- Importing demo data triggers derived-content recomputation so Home, Trends, Assessment, and related summaries stay in sync.

## Authentication Architecture

### Android Side

- `DefaultAuthRepository` sends HTTP requests directly with `HttpURLConnection`
- auth state is persisted through `SettingsRepository`
- stored values include:
  - auth base URL
  - auth token
  - current user JSON
  - current user ID

The default base URL is initialized in:

- `app/src/main/java/org/wit/vitasense/AppContainer.kt`

If you deploy a different auth service, update the default base URL there or write a new value into the stored `auth_base_url` setting.

### Python API Side

The service in `python_auth_api/` provides:

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `POST /api/v1/families`
- `GET /api/v1/families/me`
- `POST /api/v1/families/join`
- `PATCH /api/v1/families/{family_id}`
- `POST /api/v1/families/{family_id}/invite-code/regenerate`
- `DELETE /api/v1/families/{family_id}/members/{member_user_id}`
- `DELETE /api/v1/families/{family_id}/members/me`
- `POST /api/v1/families/{family_id}/status`
- `POST /api/v1/families/{family_id}/supports`
- `GET /api/v1/sync/bootstrap`
- `POST /api/v1/sync/push`
- `POST /api/v1/ai/advice`
- `POST /api/v1/ai/chat/stream`

It stores users, sessions, family state, support actions, and cloud-sync source data in a local SQLite database file:

- `python_auth_api/auth.db`

The AI advice and AI chat endpoints do not persist AI prompts or chat history. They validate the request and forward it to the configured OpenAI-compatible provider.

## Requirements

### Android

- Android Studio with a JDK 21 runtime available
- Android SDK installed locally
- Gradle wrapper
- device or emulator running Android 9.0+ (`minSdk 28`)

### Python API

- Python 3.10+ recommended
- `pip`

## Build and Run

### Android App

From the project root:

```powershell
./gradlew.bat :app:assembleDebug
```

Run unit tests:

```powershell
./gradlew.bat :app:testDebugUnitTest
```

Build instrumentation APK:

```powershell
./gradlew.bat :app:assembleDebugAndroidTest
```

Open the project in Android Studio and run the `app` configuration on a device or emulator.

### Python Auth API

From `python_auth_api/`:

```bash
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000
```

Run the local smoke test after the server starts:

```bash
python smoke_test.py
```

## Current Server Configuration

The Android app currently seeds both the auth base URL and AI proxy base URL with:

```text
https://server.np5.top
```

The remote-auth flow is used for both registration and login. The app does not fall back to local account storage when the remote server fails.

If the backend is self-hosted, the deployed `python_auth_api/main.py` must include the AI chat streaming endpoint used by Android:

```text
POST /api/v1/ai/chat/stream
```

## Design Conventions Implemented

- all in-app user-facing copy is English
- bottom navigation is a custom floating tab shell rather than the legacy bottom navigation view
- AI Chat uses an independent center action instead of becoming a fifth selected tab
- secondary pages use dedicated back navigation and hide the floating tabs
- Profile uses concise themed secondary-entry buttons
- trend pages use custom chart views and interactive visual summaries
- AI text uses in-app Markdown rendering for headings, bold text, and lists

## Verification Commands Used During Development

These are the main verification commands used for the Android project:

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleDebugAndroidTest
```

Backend checks include:

```bash
python smoke_test.py
python sync_endpoints_test.py
python family_endpoints_test.py
python ai_chat_stream_test.py
```

Targeted regression checks were also added for profile secondary-entry layout behavior, cloud sync contracts, family support behavior, AI advice parsing, AI chat stream parsing, and Markdown rendering.

## Notes

- `docs/superpowers/` contains development specs and implementation plans generated during the build process.
- `python_auth_api/README.md` contains service-specific quickstart notes.
- `.gitignore` excludes local Gradle homes, Android temp homes, Python bytecode, and the local auth database so developer-machine artifacts do not get committed.
