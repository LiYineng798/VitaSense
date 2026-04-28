# VitaSense

VitaSense is an Android mental-health-support and recovery-tracking app built with XML layouts, Fragments, Room, and a lightweight Python authentication service.

The project combines:

- a native Android client for daily health review, trend exploration, mood logging, and profile personalization
- a standalone Python authentication API for sign in, registration, and session restoration

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

### Profile, Appearance, and Settings

- `Profile` is the landing page for account state and secondary navigation
- `Appearance` is a dedicated secondary page for theme selection
- `Settings & Import` is a dedicated secondary page for demo import, data reset, privacy notes, and disclaimers
- supports light/dark mode plus multiple theme families:
  - `Default`
  - `Olive Ember`
  - `Sunlit Meadow`
  - `Rose Indigo`

### Authentication

- Android client uses remote authentication first
- current default server base URL is `https://server.np5.top`
- successful sign in restores the session on next launch
- health, mood, and trend data remain local in Room in this version

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

### Python API

- FastAPI
- SQLite
- Pydantic

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
│  │  │  ├─ dashboard/          Home page
│  │  │  ├─ trends/             trend review UI and custom charts
│  │  │  ├─ assessment/         score explanation
│  │  │  ├─ mood/               mood logging
│  │  │  ├─ profile/            Profile + Appearance
│  │  │  ├─ settings/           Settings & Import
│  │  │  └─ navigation/         floating bottom tab shell
│  │  ├─ util/                  utilities such as password hashing
│  │  ├─ AppContainer.kt        dependency assembly
│  │  └─ MainActivity.kt        app shell and floating-tab host
│  └─ src/test + src/androidTest
├─ python_auth_api/             standalone authentication service
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
- Secondary pages such as `Auth`, `Appearance`, and `Settings & Import` are still Fragment destinations, but the floating tab shell is hidden there.
- Local health data is stored in `vitasense.db` through Room.
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

It stores users and sessions in a local SQLite database file:

- `python_auth_api/auth.db`

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

The Android app currently seeds the auth base URL with:

```text
https://server.np5.top
```

The remote-auth flow is used for both registration and login. The app does not fall back to local account storage when the remote server fails.

## Design Conventions Implemented

- all in-app user-facing copy is English
- bottom navigation is a custom floating tab shell rather than the legacy bottom navigation view
- secondary pages use dedicated back navigation and hide the floating tabs
- Profile uses concise themed secondary-entry buttons
- trend pages use custom chart views and interactive visual summaries

## Verification Commands Used During Development

These are the main verification commands used for the Android project:

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:assembleDebugAndroidTest
```

Targeted regression checks were also added for profile secondary-entry layout behavior.

## Notes

- `docs/superpowers/` contains development specs and implementation plans generated during the build process.
- `python_auth_api/README.md` contains service-specific quickstart notes.
- `.gitignore` excludes local Gradle homes, Android temp homes, Python bytecode, and the local auth database so developer-machine artifacts do not get committed.
