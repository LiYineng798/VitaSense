# Remote Auth Server Integration Design

**Date:** 2026-04-28

**Goal**

Switch VitaSense Android authentication from local-only Room-backed auth to remote-first authentication using the deployed auth API at `https://server.np5.top`, while keeping the existing UI flow and local health data architecture intact.

**Scope**

- Replace local login/register behavior with remote HTTP calls.
- Persist the remote auth base URL and bearer token locally.
- Restore signed-in state from the remote `/me` endpoint.
- Keep Home and Profile login state driven by the remote session.
- Enforce register password validation on Android before sending the request.

**Out of Scope**

- Syncing health, trend, or mood data to the server.
- Changing the deployed API contract beyond what already exists.
- Offline fallback to the local Room auth table.
- Visual redesign of auth-related screens.

## Current State

- `AuthRepository` is implemented by `DefaultAuthRepository`.
- `DefaultAuthRepository` currently reads and writes a local `local_users` Room table.
- `SettingsRepository` already stores `auth_base_url`, but the value is not used at runtime.
- `AuthViewModel`, `ProfileViewModel`, and `DashboardViewModel` consume `AuthRepository` and already react to signed-in state.

## Requirements

### Authentication Source

- Login and register must call the remote server only.
- If the server returns an error, the app must display that failure instead of falling back to local auth.

### Login Errors

- `/login` failures use one unified message: `Invalid credentials.`
- Network, timeout, or malformed-response failures use a generic reachability message.

### Register Validation

- Password must be longer than 6 characters.
- Password must contain at least one letter and at least one digit.
- These checks happen in Android before the request is sent.

### Register Errors

- When the server returns a specific business error such as `Username is already taken.` or `Email is already registered.`, the app should show that message directly.

### Session Persistence

- The app must save the configured auth base URL.
- The app must save the bearer token returned by successful login/register.
- The app must restore the current user by calling `/api/v1/auth/me` with the saved token.
- If `/me` fails with an auth error, the app clears the saved session and becomes signed out.

## Architecture

### Repository Strategy

Keep `AuthRepository` as the UI-facing contract, but change `DefaultAuthRepository` from a Room-backed implementation to a remote-session implementation:

- `register(...)` -> `POST /api/v1/auth/register`
- `login(...)` -> `POST /api/v1/auth/login`
- `getCurrentUser()` / `observeCurrentUser()` -> local session cache hydrated from `/api/v1/auth/me`
- `logout()` -> clear stored token and current user cache

This avoids broad UI churn and keeps Home/Profile/Auth view models unchanged at the interface boundary.

### Local Session Storage

Extend `SettingsRepository` with values for:

- `auth_token`
- `current_user_json`

`current_user_id` remains in the interface only if it is still required by existing tests or code, but it is no longer the source of truth for authentication.

### Networking

Use `HttpURLConnection` plus `org.json` rather than introducing Retrofit/OkHttp. The project already ships `org.json`, and the API surface is small enough that a lightweight client is lower risk.

### Base URL

`AppContainer` must ensure the default remote auth base URL is `https://server.np5.top` unless the user has already stored another value locally.

## Data Flow

### Login

1. `AuthViewModel.submitLogin(...)` validates presence of identifier/password.
2. `DefaultAuthRepository.login(...)` posts to `/api/v1/auth/login`.
3. On success, parse `{ token, user }`, persist both locally, update in-memory auth state, and return `AuthResult.Success`.
4. On `401`, return `AuthResult.Error("Invalid credentials.")`.
5. On network or unexpected failures, return a generic error.

### Register

1. `AuthViewModel.submitRegister(...)` validates password length and composition before network I/O.
2. Repository posts to `/api/v1/auth/register`.
3. On success, persist token and user exactly as in login.
4. On `409` or `400` with known server messages, surface the server message directly.

### App Restore

1. Repository startup path reads `auth_token`.
2. If empty, emit `null`.
3. If present, call `/api/v1/auth/me`.
4. On success, persist normalized user JSON and emit the user.
5. On auth failure, clear token + user JSON and emit `null`.

## Error Handling

- `401` on login -> `Invalid credentials.`
- Missing/blank base URL -> `Authentication server is not configured.`
- Connection failure, TLS failure, timeout, DNS failure -> `Unable to reach the server.`
- Unexpected JSON/body -> `Unexpected server response.`
- Register conflict/business errors -> use server `message` verbatim when present

## Testing Strategy

- Replace local-auth repository tests with remote-auth repository tests using a fake HTTP connection factory.
- Add settings repository tests for token and current-user JSON persistence.
- Update `AuthViewModelTest` for the password policy and error messaging.
- Keep `ProfileViewModelTest` and `DashboardViewModelTest` focused on repository contract behavior, not transport details.

## Risks

- `HttpURLConnection` tests are more manual than Retrofit tests; isolate transport creation behind a small factory to keep tests controlled.
- The deployed server must present a valid TLS certificate for `https://server.np5.top`.
- Existing local-user Room tables will become effectively unused for auth, but leaving them in place is acceptable for this phase.
