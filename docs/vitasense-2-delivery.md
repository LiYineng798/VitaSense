# VitaSense 2.0 delivery

Verified on 2026-11-05 (Asia/Shanghai) in `VitaSense-main`.

## Implemented features

1. AI Health Insights: actual Room data, robust Spearman analysis, ten-pair minimum, category-based mood valence, coverage/baselines, local recommendations and explicit opt-in AI explanations.
2. Family Support: local trusted contacts with validation/consent/atomic primary selection, reviewed dial/SMS intents, explicit check-ins, authenticated history and independent sharing/revocation controls.
3. Weekly Reports: current/previous seven-day aggregation, missing-data coverage, charts, local/opt-in AI insights and multi-page PdfDocument export via SAF and secure FileProvider sharing.
4. Reminders & Widget: configurable unique WorkManager jobs, permission/channel guidance, quiet hours/DST/time-zone handling, destination actions and a private-by-default native Room-backed widget.

## Database, API and permission changes

- Room **4 → 5 → 6**, additive `trusted_contacts` and `reminder_schedules` tables. Both explicit migrations are registered. Destructive migration fallback removed. Emulator test verifies retained settings and a private mood record through the full upgrade.
- Existing backend SQLite: additive `family_check_ins` table and family/time index, linked to current membership with cascading deletion.
- New protected endpoint: `GET /api/v1/families/{family_id}/check-ins` (latest 100). Existing POST status extended with validated `share_mood`, `share_check_in`, `check_in`.
- Explicit Android permissions added: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`.
- New standard WorkManager merged declarations: `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, and the app's signature-only `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. No contacts, direct SMS/call, location, broad-storage or exact-alarm permission.
- Database OS cloud backup/device-transfer excluded for local contacts/credentials. Existing explicit source-data synchronization is retained and does not include new contact/reminder/widget records.

## Verification results

| Check | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest` | PASS: **164 tests, 0 failures** |
| `./gradlew :app:assembleDebug` | PASS |
| `./gradlew :app:assembleDebugAndroidTest` | PASS |
| `./gradlew :app:connectedDebugAndroidTest` | PASS: **17 tests, 0 failures**, API-36 Pixel 8 emulator |
| Backend `python -m pytest -q` | PASS: **5 tests**, one upstream Starlette/httpx deprecation warning |
| `python smoke_test.py` | PASS against an isolated local test server/database |
| `python family_endpoints_test.py` | PASS |
| `python sync_endpoints_test.py` | PASS |
| `python sync_schema_test.py` | PASS |
| `python ai_chat_stream_test.py` | PASS with mock provider |
| `python family_check_ins_test.py` | PASS |
| Python syntax compile | PASS |
| PDF stress export | PASS: 28 A4 pages; page count/content URI tested; first pages, section transitions and final page rendered and visually inspected |
| Theme matrix | PASS: all four new screens in all four families × light/dark; assertions wait for theme recreation and destination animation |

Unit coverage includes correlations (positive, negative, zero, missing, constant, ties, insufficient pairs and mood mapping), contact persistence/validation, weekly aggregation/ranges/coverage/comparisons, consented aggregate privacy/fallback, reminder replacement/cancellation/claims, quiet hours/DST/reboot rollover, permission denial and widget privacy/mapping. Device coverage includes migration preservation, database-triggered widget refresh, native PDF generation/sharing and existing/new navigation and mood/settings regression paths.

The first full runs exposed existing JUnit setup defects, stale translated UI test strings, density rounding and layout assumptions. Tests were repaired to exercise the current English UI. A real Mood delete-button hit-area problem under the floating shell was fixed with inset-aware scrolling, preserving the visual shell.

Build outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`
- Unit report: `app/build/reports/tests/testDebugUnitTest/index.html`
- Device report: `app/build/reports/androidTests/connected/debug/index.html`

## External dependencies and remaining limits

- Deploy the updated existing FastAPI backend to enable remote check-in history; no production server was deployed or altered.
- Live external AI needs user-supplied provider credentials and per-request consent. Paid/live-provider calls were not made. Complete local analysis/report functionality and mocked proxy streaming were tested.
- Physical-device launcher behavior, actual system dial/SMS completion, manufacturer notification timing and cross-app SAF/share UX still need device acceptance testing. No real calls or messages were sent.
- Recovery is explicitly the stored assessment proxy; no independently measured recovery field is available. No statistical significance, causation or medical diagnosis is claimed.
- Explicit upgrades cover the provided version-4 schema. Historic versions 1–3 require their verified original migration schemas; unsupported upgrades fail safely rather than delete data.
- Reports are generated from Room on demand and retained by the screen ViewModel; they are not a separate historical report archive.

See [architecture and manual acceptance instructions](vitasense-2-implementation.md) for the complete test procedure and platform behavior.

## File manifest

The supplied project was untracked inside its enclosing Git repository before work began. This manifest compares source-file hashes with the initial inventory; no source files were removed. `gradlew` also received executable permission for the requested Unix build commands. Generated builds, caches, emulator captures and test databases are excluded.

### Newly created files (52)

- `app/src/androidTest/java/org/wit/vitasense/db/FeatureMigrationTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/db/WidgetInvalidationTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/reports/WeeklyPdfTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/FeatureThemesTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/NewFeaturesNavigationTest.kt`
- `app/src/main/java/org/wit/vitasense/background/ReminderScheduler.kt`
- `app/src/main/java/org/wit/vitasense/background/ReminderWorker.kt`
- `app/src/main/java/org/wit/vitasense/data/repository/FamilySafetyRepository.kt`
- `app/src/main/java/org/wit/vitasense/data/repository/HealthInsightsRepository.kt`
- `app/src/main/java/org/wit/vitasense/data/repository/RemindersRepository.kt`
- `app/src/main/java/org/wit/vitasense/data/repository/WeeklyReportsRepository.kt`
- `app/src/main/java/org/wit/vitasense/db/FeatureMigrations.kt`
- `app/src/main/java/org/wit/vitasense/db/dao/ReminderScheduleDao.kt`
- `app/src/main/java/org/wit/vitasense/db/dao/TrustedContactDao.kt`
- `app/src/main/java/org/wit/vitasense/db/entity/ReminderScheduleEntity.kt`
- `app/src/main/java/org/wit/vitasense/db/entity/TrustedContactEntity.kt`
- `app/src/main/java/org/wit/vitasense/domain/HealthCorrelationAnalyzer.kt`
- `app/src/main/java/org/wit/vitasense/domain/ReminderTimeCalculator.kt`
- `app/src/main/java/org/wit/vitasense/domain/TrustedContactValidator.kt`
- `app/src/main/java/org/wit/vitasense/domain/WeeklyReportGenerator.kt`
- `app/src/main/java/org/wit/vitasense/ui/family/FamilySafetyFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/family/FamilySafetyViewModel.kt`
- `app/src/main/java/org/wit/vitasense/ui/insights/FeatureViews.kt`
- `app/src/main/java/org/wit/vitasense/ui/insights/HealthInsightsFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/insights/HealthInsightsViewModel.kt`
- `app/src/main/java/org/wit/vitasense/ui/navigation/OverlayAwareScrollView.kt`
- `app/src/main/java/org/wit/vitasense/ui/reminders/RemindersFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/reminders/RemindersViewModel.kt`
- `app/src/main/java/org/wit/vitasense/ui/reports/ReportTrendView.kt`
- `app/src/main/java/org/wit/vitasense/ui/reports/WeeklyReportPdfExporter.kt`
- `app/src/main/java/org/wit/vitasense/ui/reports/WeeklyReportsFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/reports/WeeklyReportsViewModel.kt`
- `app/src/main/java/org/wit/vitasense/widget/VitaSenseWidgetProvider.kt`
- `app/src/main/java/org/wit/vitasense/widget/WidgetDataMapper.kt`
- `app/src/main/res/layout/fragment_health_feature.xml`
- `app/src/main/res/layout/widget_vitasense.xml`
- `app/src/main/res/xml/report_paths.xml`
- `app/src/main/res/xml/vitasense_widget_info.xml`
- `app/src/test/java/org/wit/vitasense/domain/AggregatedAiPrivacyTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/ContactPersistenceTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/HealthCorrelationAnalyzerTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/NotificationPermissionTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/ReminderPersistenceTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/ReminderSchedulingTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/ReminderTimeCalculatorTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/TrustedContactValidatorTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/WeeklyReportGeneratorTest.kt`
- `app/src/test/java/org/wit/vitasense/domain/WidgetDataMapperTest.kt`
- `docs/vitasense-2-delivery.md`
- `docs/vitasense-2-implementation.md`
- `python_auth_api/family_check_ins_test.py`
- `python_auth_api/requirements-dev.txt`

### Modified files (34)

- `.gitignore`
- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTest/java/org/wit/vitasense/ui/FloatingBottomTabNavigationTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/MainActivitySmokeTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/MoodScreenTest.kt`
- `app/src/androidTest/java/org/wit/vitasense/ui/SettingsImportTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/org/wit/vitasense/AppContainer.kt`
- `app/src/main/java/org/wit/vitasense/MainActivity.kt`
- `app/src/main/java/org/wit/vitasense/VitaSenseApplication.kt`
- `app/src/main/java/org/wit/vitasense/data/repository/DefaultFamilyRepository.kt`
- `app/src/main/java/org/wit/vitasense/db/AppDatabase.kt`
- `app/src/main/java/org/wit/vitasense/model/FamilyModels.kt`
- `app/src/main/java/org/wit/vitasense/repository/FamilyRepository.kt`
- `app/src/main/java/org/wit/vitasense/ui/common/VitaSenseViewModelFactory.kt`
- `app/src/main/java/org/wit/vitasense/ui/family/FamilyFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/family/FamilyViewModel.kt`
- `app/src/main/java/org/wit/vitasense/ui/profile/ProfileFragment.kt`
- `app/src/main/java/org/wit/vitasense/ui/trends/TrendsFragment.kt`
- `app/src/main/res/layout/fragment_family.xml`
- `app/src/main/res/layout/fragment_mood.xml`
- `app/src/main/res/layout/fragment_profile.xml`
- `app/src/main/res/layout/fragment_trends.xml`
- `app/src/main/res/navigation/main_nav_graph.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/test/java/org/wit/vitasense/data/repository/DefaultFamilyRepositoryTest.kt`
- `app/src/test/java/org/wit/vitasense/ui/common/markdown/MarkdownTextRendererTest.kt`
- `app/src/test/java/org/wit/vitasense/ui/family/FamilyViewModelTest.kt`
- `gradlew`
- `python_auth_api/README.md`
- `python_auth_api/main.py`
