# Remote Auth Server Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Switch Android authentication to the deployed remote auth API at `https://server.np5.top`, persist remote session state locally, and enforce the updated register/login validation behavior.

**Architecture:** Keep `AuthRepository` as the stable UI boundary, but rework `DefaultAuthRepository` into a remote HTTP implementation backed by `SettingsRepository` session storage. `AuthViewModel`, `ProfileViewModel`, and `DashboardViewModel` continue to consume the same repository contract.

**Tech Stack:** Android XML + Fragments, Kotlin Flow, ViewModel, Room-backed settings storage, `HttpURLConnection`, `org.json`, JUnit.

---

### Task 1: Extend settings storage for remote session data

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultSettingsRepository.kt`
- Modify: `app/src/test/java/org/wit/vitasense/data/repository/DefaultSettingsRepositoryTest.kt`

- [ ] Add `observeAuthToken()`, `getAuthToken()`, `setAuthToken(...)`, `observeCurrentUserJson()`, `getCurrentUserJson()`, `setCurrentUserJson(...)` to `SettingsRepository`.
- [ ] Add corresponding key constants and persistence logic to `DefaultSettingsRepository`.
- [ ] Add/adjust tests proving token and user JSON are stored and read back.

### Task 2: Replace local auth repository behavior with remote HTTP auth

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/data/repository/DefaultAuthRepository.kt`
- Modify: `app/src/main/java/org/wit/vitasense/model/AuthModels.kt`
- Modify: `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- Modify: `app/src/test/java/org/wit/vitasense/data/repository/DefaultAuthRepositoryTest.kt`

- [ ] Write repository tests for:
  - login success stores token and current user
  - login `401` maps to `Invalid credentials.`
  - register server conflict message is surfaced
  - `observeCurrentUser()` restores state from stored token via `/me`
  - `/me` auth failure clears session
- [ ] Introduce minimal remote response/request parsing helpers in `AuthModels.kt` or a close-by helper.
- [ ] Replace Room user lookup logic with `HttpURLConnection` requests to `/api/v1/auth/register`, `/api/v1/auth/login`, and `/api/v1/auth/me`.
- [ ] Seed `auth_base_url` with `https://server.np5.top` from `AppContainer` when no base URL is already stored.

### Task 3: Update AuthViewModel validation and messaging

**Files:**
- Modify: `app/src/main/java/org/wit/vitasense/ui/auth/AuthViewModel.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/auth/AuthViewModelTest.kt`

- [ ] Add failing tests for:
  - password shorter than or equal to 6 is rejected
  - password without digits is rejected
  - password without letters is rejected
  - login repository error `Invalid credentials.` is preserved
- [ ] Implement the new password policy and keep login/register messaging aligned with the remote contract.

### Task 4: Keep Profile and Home state aligned with remote session behavior

**Files:**
- Modify: `app/src/test/java/org/wit/vitasense/ui/profile/ProfileViewModelTest.kt`
- Modify: `app/src/test/java/org/wit/vitasense/ui/dashboard/DashboardViewModelTest.kt`
- Modify: any production files only if the repository contract change requires it

- [ ] Verify existing tests still describe the intended behavior with a remote-backed auth repository.
- [ ] Update fakes to implement any new settings/auth methods without changing UI behavior expectations.

### Task 5: Final verification

**Files:**
- Modify: any touched files from Tasks 1-4

- [ ] Run focused auth/settings/view-model tests.
- [ ] Run `:app:testDebugUnitTest`.
- [ ] Run `:app:assembleDebug`.
- [ ] Do not commit unless the user explicitly asks.
