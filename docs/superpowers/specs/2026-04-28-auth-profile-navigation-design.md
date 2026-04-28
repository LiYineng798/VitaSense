# Auth, Profile, And Navigation Redesign

## Summary

Redesign VitaSense around a new top-level information architecture:

- `Home`
- `Trends`
- `Mood`
- `Profile`

This change removes `Assessment` from the floating bottom tab and introduces a lightweight local authentication system with a future-ready remote authentication boundary.

The app must support:

- local registration
- local login
- local logout
- persistent signed-in state
- a configurable remote auth base URL stored locally for later server integration

The Home page gains a login-status entry in the top-left corner. The Profile page becomes the personal account hub and absorbs the current Settings functionality, including theme and appearance controls.

In parallel, the repo should include a standalone Python authentication API program that the user can deploy to a server later. The Android app will not call the remote server yet, but its repository boundaries and config storage must be designed so the future remote hookup does not require UI rewrites.

## Goals

- Replace the bottom-tab structure with `Home / Trends / Mood / Profile`.
- Remove the `Assessment` bottom-tab entry from the primary navigation surface.
- Add persistent local registration and login flows inside the Android app.
- Add a dedicated auth screen that supports both login and register modes.
- Show sign-in state in the Home header.
- Turn `Profile` into the destination for account info, appearance controls, existing settings, and logout.
- Keep a configurable remote auth base URL in local settings for future server hookup.
- Ship a standalone Python API implementation for future real registration and login.

## Non-Goals

- No Compose migration.
- No third-party auth provider integration.
- No cloud sync of health data in this iteration.
- No profile image upload in this iteration.
- No password reset or email verification in this iteration.
- No live Android-to-server auth integration until the user provides the deployed server address.

## User Experience

### Bottom Navigation

The floating bottom tab keeps the existing visual style and motion system, but changes the third and fourth destinations:

- `Home`
- `Trends`
- `Mood`
- `Profile`

`Mood` moves into the slot currently occupied by `Assessment`.

The old `Mood` slot becomes `Profile`.

The app should not expose `Assessment` as a bottom-tab destination anymore. If the assessment screen is still worth keeping in the codebase for future use, it can remain as a secondary destination and not a top-level tab.

### Home Header

The Home page header changes from a title-plus-settings action to a personal entry point:

- top-left avatar
- top-left adjacent status text
- tap action to auth flow when signed out
- tap action to profile when signed in

Signed-out state:

- avatar uses a question-mark or anonymous placeholder
- text reads `Tap to sign in`

Signed-in state:

- avatar uses a simple generated placeholder with the user's initial
- text reads `Welcome, {name}!`

The old top-right Settings action is removed from Home.

### Auth Screen

There is one dedicated auth destination with two modes:

- `Login`
- `Register`

Required register fields:

- full name
- email
- username
- password
- confirm password
- birth date via date picker

Required login fields:

- email or username
- password

Expected behaviors:

- register validates locally before submit
- register success creates the local account and signs the user in immediately
- login accepts either email or username
- auth errors are shown inline or as concise helper text, not hidden in toasts only
- successful auth returns the user to the previous logical context when possible

Entry points:

- Home header signed-out state
- Profile screen signed-out CTA

### Profile Screen

The Profile page becomes the merged account-and-settings hub.

When signed out:

- show avatar placeholder
- show a short signed-out explanation
- show a primary `Sign In / Register` CTA
- still allow Appearance access, because theming is app-level rather than account-only

When signed in:

- show avatar placeholder with initial
- show full name
- show email
- show grouped sections for:
  - account
  - appearance
  - data and import
  - privacy/info
- show logout action

The current Settings content should move here instead of existing as a separate bottom-tab destination.

## Architecture

### Auth Boundary

Introduce a dedicated auth data boundary instead of mixing authentication state directly into fragments or reusing the settings repository for all account data.

Recommended layers:

- `AuthRepository`
- local Room-backed implementation for now
- future remote implementation adapter or remote data source

The UI only depends on `AuthRepository`.

That allows the future server hookup to change repository internals without rewriting:

- `AuthFragment`
- `DashboardFragment`
- `ProfileFragment`
- navigation flow

### Local Data Split

Use two persistence styles:

- local users in a dedicated Room table
- lightweight session and config values in `app_settings`

This split keeps account records queryable and structured, while avoiding a full session table that would be unnecessary for a single-user local sign-in model.

Store in Room user table:

- user id
- full name
- email
- username
- password hash
- birth date
- created at

Store in `app_settings`:

- current signed-in user id
- auth base URL

### Why Not Store Everything In `app_settings`

The current settings table is key-value storage. It is appropriate for theme mode, theme family, and a future base URL. It is not appropriate for user lookup, uniqueness checks, or email/username login queries.

User records therefore need a dedicated entity and DAO.

### Navigation Structure

The nav graph should introduce:

- `profileFragment`
- `authFragment`

It should remove `settingsFragment` from primary navigation usage.

If `settingsFragment` is fully replaced by `profileFragment`, the old destination can be removed from the graph once no callers remain.

If `assessmentFragment` remains in the graph, it should no longer be referenced by the bottom-tab enum or floating tab layout.

## Android Components

### Data Layer

Add:

- `LocalUserEntity`
- `LocalUserDao`
- auth domain models for session and form results
- `AuthRepository` interface
- `DefaultAuthRepository` implementation

Responsibilities:

- register user with uniqueness validation
- hash password before persistence
- login by email or username
- resolve current signed-in user
- logout
- read/write future auth base URL

### ViewModels

Add:

- `AuthViewModel`
- `ProfileViewModel`

Extend:

- `DashboardViewModel` or its UI mapping source to observe auth state for the Home header

Responsibilities:

`AuthViewModel`

- manage login/register mode
- validate form input
- expose loading, error, and success state
- call repository login/register

`ProfileViewModel`

- expose current signed-in user
- expose theme mode and theme family
- expose demo import options
- expose logout and settings actions

This likely absorbs most of the current `SettingsViewModel` behavior.

### Fragments

Add:

- `AuthFragment`
- `ProfileFragment`

Update:

- `DashboardFragment`
- `MainActivity`

Responsibilities:

`AuthFragment`

- mode switch between login and register
- bind forms
- launch date picker
- show validation and submit results

`ProfileFragment`

- render signed-in vs signed-out state
- host appearance controls
- host import/data sections migrated from Settings
- host logout button
- provide sign-in CTA when signed out

`DashboardFragment`

- render Home auth status entry
- remove toolbar settings menu

`MainActivity`

- wire bottom tabs to `Home / Trends / Mood / Profile`
- keep floating indicator behavior correct after destination replacement

## Data Flow

### Register Flow

1. User opens auth screen in register mode.
2. User enters name, email, username, password, confirm password, and birth date.
3. `AuthViewModel` validates:
   - non-empty fields
   - email format
   - username format
   - password length/strength threshold
   - password confirmation match
   - birth date chosen
4. `AuthRepository.register(...)` checks uniqueness for email and username.
5. Repository hashes password and saves the user.
6. Repository writes `current_user_id` into settings.
7. UI receives signed-in state and navigates back or into Profile/Home context.

### Login Flow

1. User opens auth screen in login mode.
2. User enters `email or username` and password.
3. Repository detects whether the identifier matches an email or username candidate.
4. Repository fetches matching local user.
5. Repository verifies hashed password.
6. On success, repository writes `current_user_id`.
7. Observers update Home and Profile automatically.

### Logout Flow

1. User taps logout in Profile.
2. Repository clears `current_user_id`.
3. Home header switches to signed-out state.
4. Profile switches to signed-out presentation.

## Validation And Security Rules

### Local Password Handling

Passwords must not be stored in plain text.

This app can use a straightforward one-way hash for local-only storage in this iteration. The implementation does not need enterprise auth complexity, but it must avoid obvious plain-text persistence.

Recommended pragmatic approach:

- SHA-256 with deterministic hashing helper for now

This is acceptable for the current course-project scope and avoids pretending to have production-grade security while still improving over raw-text storage.

The Python API can use the same pragmatic level or a slightly stronger password library if dependency footprint stays low.

### Validation Rules

Register:

- email must be syntactically valid
- email must be unique
- username must be unique
- password must meet minimum length
- confirm password must match

Login:

- identifier required
- password required
- invalid credential error should not reveal whether the username/email exists

### Error Messaging

Prefer concise, deterministic messages such as:

- `Email is already registered.`
- `Username is already taken.`
- `Passwords do not match.`
- `Invalid credentials.`

## Remote Readiness

### Configurable Base URL

The Android app should expose a stored auth base URL setting, but not actively depend on it yet.

The value should be persisted now but does not need a visible Profile control in this iteration. The repository layer and data model should support it immediately.

This lets the later server hookup be implemented as:

- add remote auth data source
- inject base URL from settings
- switch repository strategy

without redesigning the screens.

### Future Integration Direction

When the user later provides the deployed address, the app should:

- point `AuthRepository` remote calls at `auth_base_url`
- map the same request/response models already used by the Python API
- keep the UI unchanged

## Python API Program

### Purpose

Ship a standalone Python auth service that the user can deploy independently from the Android app.

### Recommended Stack

- `FastAPI`
- built-in `sqlite3` or SQLAlchemy if the implementation stays small
- Pydantic request/response models

Pragmatic recommendation:

- `FastAPI + sqlite3`

This keeps the program small, easy to run, and easy to deploy on a basic server.

### Endpoints

Required endpoints:

- `GET /api/v1/health`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`

Suggested response model:

- success flag
- message
- user object when relevant
- session token on successful login/register

For this iteration, a simple opaque bearer token store is sufficient. The Android app will not consume it yet, but the service should already represent a realistic auth contract and support `/me`.

### Python Service Data Model

User record fields:

- id
- full_name
- email
- username
- password_hash
- birth_date
- created_at

### Contract Alignment

The request and response field names should be chosen so Android can reuse the same conceptual models later:

Register request:

- `full_name`
- `email`
- `username`
- `password`
- `birth_date`

Login request:

- `identifier`
- `password`

User response:

- `id`
- `full_name`
- `email`
- `username`
- `birth_date`

## Testing Strategy

### Android Unit Tests

Add tests for:

- auth repository registration success
- auth repository duplicate email rejection
- auth repository duplicate username rejection
- auth repository login by email
- auth repository login by username
- auth repository invalid password rejection
- auth repository logout clears session
- dashboard/profile auth-state mapping
- profile view model theme and auth state composition

Update tests for:

- bottom-tab destination mapping
- any Home screen state model affected by the new header

### Android Build Verification

Required verification after implementation:

- targeted auth repository tests
- targeted profile/settings view model tests
- targeted bottom-tab/navigation tests
- `:app:assembleDebug`
- `:app:assembleDebugAndroidTest`

### Python API Verification

Add a minimal runnable smoke test path:

- register a user
- reject duplicate registration
- login with valid credentials
- reject invalid credentials
- read `me` with token

This can be done with lightweight Python tests or a documented curl-based smoke sequence.

## Migration Strategy

1. Add new auth persistence and repository code.
2. Add auth and profile destinations.
3. Replace bottom-tab destination wiring.
4. Move settings content into profile.
5. Remove Home toolbar settings entry.
6. Add Home auth header state.
7. Add Python API program.
8. Run targeted tests and builds.

This order minimizes broken navigation states and keeps theme/settings logic reusable during the migration.

## Risks And Controls

### Risk: Fragment Scope Creep

If Profile directly duplicates Settings logic without extracting reusable pieces, the screen can become bloated quickly.

Control:

- move shared appearance/data logic through `ProfileViewModel`
- keep fragment responsibilities thin

### Risk: Room Schema Expansion Regressions

Adding a new entity changes the database schema.

Control:

- increment Room database version
- use destructive migration for this iteration because the app is still in local development and existing data is demo/local state

### Risk: Navigation Regression

Changing the tab structure can break indicator behavior or stale destination selection.

Control:

- update the enum, XML, and `MainActivity` together
- keep bottom-tab tests in sync

### Risk: Future Remote Hookup Drift

If local auth models and Python API contracts diverge, future integration will create avoidable rework.

Control:

- define shared field names now
- keep login/register semantics aligned between Android and Python

## Implementation Recommendation

Proceed with:

- dedicated local user table
- `AuthRepository` abstraction
- `ProfileFragment` replacing Settings as the user-facing configuration hub
- `AuthFragment` for login/register
- standalone FastAPI service under a separate project directory inside the repo

This is the smallest design that satisfies the requested experience while preserving a clean upgrade path to real server-backed authentication later.
