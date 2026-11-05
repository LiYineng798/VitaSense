# VitaSense 2.0 implementation

Implemented and verified locally on 2026-11-05 (Asia/Shanghai).

## Architecture and navigation

The app remains a single Activity with Kotlin, XML, Fragments, ViewBinding, Material components and Room. AppContainer assembles repositories; VitaSenseViewModelFactory supplies the four new ViewModels. Existing authentication, AI settings/proxy, source-data synchronization, recomputation and theme families are reused. No Compose or alternate backend/database is introduced.

Entry paths:

| Existing page | New destination |
| --- | --- |
| Trends | AI Health Insights |
| Profile → Family | Family Support & Trusted Contacts |
| Profile | Weekly Reports |
| Profile | Reminders & Widget |

The Home / Trends / Mood / Profile tabs and independent AI Chat action remain. Secondary destinations hide the floating tabs under the existing destination policy. Notification intents route through MainActivity into the appropriate Fragment; the family “I'm OK” action opens a confirmation before any family request.

`fragment_health_feature.xml` is a shared scrollable ViewBinding shell for new secondary pages, with Back, title, progress, live status, actions and themed cards. Cards and controls use theme attributes and Material widgets. Trend charts show gaps for absent measurements and include text descriptions for screen readers. All new screens are exercised under Default, Olive Ember, Sunlit Meadow and Rose Indigo in light and dark modes.

The existing floating-tab overlap bug found by the Mood regression test is fixed by OverlayAwareScrollView: requested/focused controls scroll above the shell's bottom inset while retaining the transparent overlay presentation.

## Phase 1: correlations and optional AI

HealthInsightsRepository takes a transactional Room snapshot of daily summaries, active sleep records, assessments and active mood records. Sleep uses the stored daily summary when present, otherwise the actual sleep record. HRV is the stored RMSSD derived by the existing recomputation engine from heart-rate data. Average heart rate and baselines come from stored daily summaries. No extra sample data or missing-value defaults are created.

HealthCorrelationAnalyzer computes Pearson correlation of average ranks (Spearman). It removes only incomplete/non-finite pairs, averages duplicate ranks, rejects constant series and requires ten pairs. It reports no p-values or significance. Descriptive strength thresholds use absolute rho: below .2 very weak, below .4 weak, below .7 moderate, otherwise strong.

The 90-calendar-day analysis includes sleep vs next-calendar-day mood, HRV vs same-day mood, sleep vs HRV, heart rate vs recovery and sleep vs recovery. MoodType currently has only positive and negative groups: +1 and -1 encode category valence, averaged across valid records on each date. Unknown categories remain absent; no neutral class or mood intensity is fabricated. The stored sleep date is not shifted to guess wake date; next-day mood explicitly uses stored date + one calendar day.

Recovery is explicitly labelled as a proxy using the existing stored assessment score. That score already includes sleep and physiology, so relationships with its inputs are partly formula-derived and cannot establish an independent effect. Existing scoring may use default component contributions when source data is missing; the UI and report disclose this.

The screen shows all five relationship cards with coefficient availability, paired-day count, direction/strength, methodology, data coverage, heart-rate period comparison, stored baseline comparison and recommendations. Heart-rate comparison needs three measured days in each adjacent seven-day window.

HealthInsightsRepository reuses DefaultAiChatRemoteDataSource and the configured provider. Per-request consent is required in the UI. Only generated summary strings enter the request; no note fields, contact details or raw samples are serialized. Provider misconfiguration, transport failure, incomplete streams and AI errors leave the local results available. The same consented aggregate explanation path serves reports. The existing general AI Chat behavior is separate and unchanged.

## Phase 2: contacts, check-ins and privacy

TrustedContactEntity stores ID, name, relationship, phone, optional email, primary status, consent confirmation and timestamps locally. FamilySafetyRepository validates manual entry and changes primary selection in a Room transaction. Empty, edited, deleted and primary-contact states are supported. No READ_CONTACTS access is needed. Contacts are installation-local; they are not sent to family APIs or cloud sync. Room databases are excluded from OS cloud backup/device transfer.

Emergency actions use ACTION_DIAL with a tel URI and ACTION_SENDTO with a smsto URI. Android's system app performs final review and execution. There is no direct call/SMS permission, automatic notification, location collection or score-triggered emergency action. Missing dialer/SMS applications produce an explanatory message.

FamilySafetyRepository reuses the existing FamilyRepository status endpoint. Privacy is stored in AppSettingEntity under the signed-in user ID. Mood, score and check-in sharing default off. Only today's assessment score may be shared. Every Android family payload excludes journal notes; categories disabled by consent are omitted at the network boundary. The original family creation/join/member/support interfaces and support actions remain.

A switch updates local privacy and immediately attempts publishing. If offline, the screen states the server update is unconfirmed and provides a retry action. Revocation is not claimed until the server responds. Disabling check-in sharing removes that user's shared history for the family; disabling score/mood sharing clears the corresponding status fields. Contact access itself does not require sign-in.

Backend additions:

- Pydantic extends FamilyStatusRequest with booleans and an optional `Literal["ok", "needs_support"]`.
- The existing POST status route records explicit check-ins with the authenticated user and server clock.
- Identical actions within sixty seconds are deduplicated; support-needed transitions are retained.
- Additive SQLite table `family_check_ins` references the composite family membership key with cascading deletion, plus an index on family and timestamp.
- Authenticated GET `/api/v1/families/{family_id}/check-ins` requires current membership and returns the most recent 100 entries. Outsiders receive the existing membership error convention.
- Existing per-member support actions provide acknowledgement; no separate messaging or push service is added.

History is explicitly refreshed from the UI. Sharing preferences are local per user; deploy the updated backend before using history on a remote server.

## Phase 3: weekly reports and PDF

WeeklyReportsRepository and WeeklyReportGenerator cover today minus six days through today, including a possibly incomplete current day. The comparison interval is today minus thirteen through today minus seven. Each metric uses available observations only, with an independent count and missing-day count. A period comparison needs at least three measured days per interval; this is a coverage rule, not a significance test.

Reports include metric averages, assessment/recovery trend, mood category counts and logged days, stored anomaly flags, reporting period, generation timestamp, local recommendations, methodology and missing measurements. Four charts preserve absent days as gaps. Mood notes never enter the aggregate sent to optional AI. No generated report entity is necessary: the report is derived on request from Room and retained by its ViewModel across rotation.

WeeklyReportPdfExporter uses Android PdfDocument with A4 pages, 40-point side margins, 11-point body text, 16-point headings, wrapped lines, page numbers and repeated branding/footer. Daily values are clearly labelled tabular summaries. It handles long words and multi-page text without exceeding the content region. A 28-page stress report was rendered and inspected at the start, section transitions and final page.

Save uses ActivityResultContracts.CreateDocument (`application/pdf`) and the returned content resolver stream. Sharing writes to `cacheDir/weekly-reports` and grants temporary read access to a FileProvider content URI. The provider exports only that directory, is not externally exported, and supports URI grants. Old cached exports are removed after one day when another export is created. There is no broad storage permission or file URI.

## Phase 4: reminders and widget

ReminderScheduleEntity stores four kinds (`mood`, `sleep`, `weekly`, `family`) with enabled state, local minute of day, weekday bitmask (Monday bit 0), description, optional quiet start/end and revision timestamp. AppSettingEntity stores widget privacy and notification occurrence claims. Settings are local and excluded from cloud sync.

ReminderTimeCalculator uses java.time calendar dates in the current zone. DST gaps move to the next valid local time; overlaps choose the first occurrence. Quiet hours defer to their end, possibly the next day. Repeat days select the original preferred date, so a Sunday evening reminder may deliver Monday morning. The calculator also checks yesterday to recover such a deferred occurrence after reboot. Equal quiet start/end is rejected instead of silently disabling all delivery.

ReminderScheduler uses one unique work name per kind. Edits replace pending work; disabling cancels it. Worker input contains the schedule revision and planned occurrence. Stale revisions cannot notify. A completed reminder appends its successor rather than using fixed 24-hour recurrence; local time is recomputed on every cycle. A Room transaction claims each occurrence once, and stable notification IDs avoid duplicate entries. Doze-delayed work skips a stale calendar day and checks quiet hours again before delivery.

WorkManager persists work across reboot. A receiver additionally reschedules on boot, app replacement, wall-clock changes and time-zone changes. Application startup repairs schedules. No exact alarm is requested. All scheduling is inexact, subject to Android battery policy and force-stop restrictions.

The app creates the VitaSense Reminders notification channel. Android 13+ permission is requested only from the Reminders screen. Both runtime permission and channel/application settings are checked. When blocked, the screen clearly labels schedules as configured with delivery blocked, provides guidance and opens Android settings. Notifications have private lock-screen visibility and actions to the relevant screens; no family data is sent by a background worker.

VitaSenseWidgetProvider is a native AppWidgetProvider with RemoteViews, metadata XML, resizing, generic loading state, empty-data mapping, open-app and manual refresh actions. The provider schedules a CoroutineWorker for safe asynchronous Room reads and does not use the network. Show Health Details on Home Widget defaults off; in privacy mode no health snapshot is read or displayed. Enabled mode shows today's assessment, latest available sleep/HRV/mood, source dates and refresh time. Unavailable metrics remain placeholders.

Room invalidation observation covers daily summaries, assessments, mood, sleep and settings. Imports, recomputation, assessments and mood changes therefore request refresh without modifying or duplicating existing data pipelines. Startup, widget broadcasts, manual refresh, resize and time changes also request updates. Widget colors are resolved from the selected existing theme family and mode.

## Database migration and permissions

| Migration | Additive changes |
| --- | --- |
| 4 → 5 | trusted_contacts |
| 5 → 6 | reminder_schedules |

Both migrations are registered in AppContainer. The destructive fallback is removed. The emulator migration test simulates the exact existing version-4 table set, retains a setting and private mood record, upgrades and queries both old/new tables. Unknown historical schemas (versions 1–3) fail safely; their original schema migrations must be supplied before supporting those installations.

Explicit permissions: POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED. WorkManager also merges its normal ACCESS_NETWORK_STATE, WAKE_LOCK, FOREGROUND_SERVICE and its signature-only receiver declaration. No contacts, direct calling/SMS, location, broad external storage or exact alarm permission is added.

## Manual acceptance test guide

1. Install the debug APK on Android 9+; use Android 13+ for notification permission checks. Confirm Home, Trends, Mood and Profile still work and the independent AI action opens chat. Check sign-in/registration and existing cloud sync against your deployed test backend.
2. Open Trends → AI Health Insights with no data. Verify the empty state and absence of coefficients. Import actual health records and log mood across ten or more paired dates. Refresh and inspect coefficients, coverage, constants and next-day pairing. Seven-day demo bundles are intentionally insufficient for ten-day correlations. Cancel the AI consent dialog and confirm local analysis remains. With an independently configured provider, consent and inspect the explanation; disconnect the network and verify fallback.
3. From Profile → Family, confirm signed-out access to trusted contacts. Add a manual contact with missing consent/invalid phone, then correct it. Edit, set primary, add a second contact and change primary. Open call/message review and cancel in the system app. Delete a contact and confirm the empty state when all are removed.
4. Use two test accounts on the updated backend. Create/join a family with existing invite codes. Enable check-in sharing, share I'm OK and I Need Support, refresh history, and acknowledge through existing member support actions. Toggle mood and score independently; verify the other account sees only opted-in fields. Revoke each setting and refresh the other account. Repeat offline and confirm unconfirmed revocation guidance/retry. Journal notes must never appear.
5. Open Weekly Reports and generate with no data, partial data and complete data. Verify the exact dates/counts, current-day caveat, gaps and comparison coverage rule. Request AI only after consent. Save a PDF to the document picker and open it in a PDF viewer. Share to a second app, verify readable content access, multiple pages and disclaimers, and verify private app files are not exposed.
6. On Reminders & Widget deny POST_NOTIFICATIONS and verify blocked guidance. Grant it, enable one reminder near the current time, set days/time/description/quiet hours, then change its time and ensure one active schedule remains. Disable it and verify cancellation. Test notification actions. Reboot and change zone/time; verify local-time recalculation. Android can delay inexact work, so do not expect exact-minute execution.
7. Add VitaSense through the launcher widget picker. Verify generic branding by default and no score/mood on the home screen. Enable details and confirm actual stored values/source dates. Log/delete a mood, import/recompute health data and manually refresh; verify updates and missing-value placeholders. Disable details and check metrics disappear. Resize the widget.
8. Repeat each new screen in all four theme families and both light/dark modes, on a small phone and larger screen. Use TalkBack to check action labels, headings, live statuses and chart descriptions. Confirm long contact names/report text remain scrollable and actionable controls scroll above the floating tabs.

## Practical limits and external dependencies

- Real external AI calls require the user's configured provider credentials and per-request consent. They were not exercised against a paid/live provider; deterministic local functionality and the existing mock-provider stream tests are verified.
- Remote family history requires deployment of the updated existing backend. No production server was modified or messaged.
- Direct dial/SMS execution, notification timing under manufacturer battery policies, launcher-specific widget behavior and cross-app SAF/share flows should also be checked on target physical devices. No real calls or messages were placed.
- Recovery is the disclosed assessment proxy; statistical significance and medical diagnosis are deliberately absent.
- Coverage and UI tests run on an API-36 emulator; the minSdk remains 28. Versions 1–3 require verified historic migrations; no data is deleted to bypass that requirement.
