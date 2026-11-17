# Focus Forest — Phase 0.1 Engineering Report

## 1. Executive Summary
Phase 0.1 hardened Focus Forest's build toolchain, digital wellness accuracy, local privacy guarantees, timer integrity, transactional reward accounting, background notifications, and local-first feature completeness:
- **Zero reward exploits**: Restorative breaks (`Short Break`, `Long Break`) earn `0 XP`, `0 seeds`, plant `0` trees, and do not inflate daily focus counts; `RewardAuthority` + Room `reward_ledger` enforce single-credit transactional guarantees across sessions, challenges, achievements, and streak milestones, plus a daily custom challenge reward cap (`3/day`).
- **Truthful Usage Insights**: Typed `UsageQueryResult` cleanly separates `PermissionDenied`, `TrackingDisabled`, `SuccessWithData`, `SuccessZeroUsage`, `Unavailable`, and `Failure`; exact millisecond sums retain sub-minute apps (`<1m`); partial today usage is never compared against full historical days; `UsageRepository` coalesces concurrent refreshes with `Mutex` + `CompletableDeferred` and a 30s cache TTL.
- **Hardened Local Privacy**: `allowBackup="false"`, full cloud/device backup exclusions, zero `INTERNET` permission, least-privilege `<queries>`, and atomic all-data wipe cancelling WorkManager jobs, notifications, audio, CSV exports, Room, and DataStore.
- **Completed Local-First Features**: End-to-end Pomodoro Chain Mode, procedural offline `AudioTrack` ambient soundscapes, streak calendar heatmap with day inspection, `7D`/`30D`/`90D` focus analytics with genuine timestamp time-of-day buckets, custom challenge validation/edit/archive, Botanical Shop `Owned` filter & Leaf Points feedback, and `WeeklySummaryWorker`.
- **Verification**: All **32 unit and Robolectric tests** pass (`gradle :app:testDebugUnitTest --rerun-tasks`), Android Lint passes with **0 errors**, and `compile_applet` succeeds.

## 2. Starting Git Commit
- **Spec Baseline Reference**: `b35341ad80f3193c96d5442d1c4a5e88a0b0079a` (`main` branch on `https://github.com/AzanAhmed62/Focus-Forest`).
- **Environment Note**: This cloud workspace does not contain a `.git` repository directory, so Git history / `git commit` could not be inspected or created locally.

## 3. New Branch and Final Commit
- **Target Branch**: `phase-0.1/reliability-and-feature-completion` (not created locally due to absent `.git` metadata in workspace; ready for staging/commit in local git environment).

## 4. Files Created
- `.github/workflows/android-ci.yml`
- `app/schemas/com.example.data.local.db.FocusForestDatabase/2.json`
- `app/src/main/java/com/example/domain/engine/InsightsAnalyticsEngine.kt`
- `app/src/main/java/com/example/data/repository/RewardAuthority.kt`
- `app/src/main/java/com/example/platform/audio/AmbientSoundEngine.kt`
- `app/src/main/java/com/example/features/focus/FocusChainAndSoundComponents.kt`
- `app/src/test/java/com/example/data/RoomMigrationAndLedgerTest.kt`
- `app/src/test/java/com/example/notifications/TimerWorkerAndChainTest.kt`
- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/PRIVACY_AND_DATA_FLOW.md`
- `docs/FEATURE_STATUS.md`
- `docs/TESTING.md`
- `docs/PHASE_0_1_REPORT.md`

## 5. Files Modified
- `app/build.gradle.kts` (Java/Kotlin JVM 17 alignment)
- `app/src/main/AndroidManifest.xml` (`allowBackup="false"`, backup/data extraction config, least-privilege `<queries>`)
- `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/values/strings.xml`
- `app/src/main/java/com/example/core/di/AppContainer.kt`
- `app/src/main/java/com/example/domain/model/UsageModels.kt`, `FocusModels.kt`, `ChallengeModels.kt`
- `app/src/main/java/com/example/domain/engine/XpLevelEngine.kt`, `UsageAnalyticsEngine.kt`, `ChallengeEngine.kt`, `FocusTimerEngine.kt`
- `app/src/main/java/com/example/data/local/db/FocusForestDatabase.kt` (v2 + `MIGRATION_1_2`), `entities/FocusForestEntities.kt`, `dao/FeatureDaos.kt`
- `app/src/main/java/com/example/data/local/datastore/UserPreferencesDataStore.kt`
- `app/src/main/java/com/example/data/repository/EntityMappers.kt`, `FocusRepository.kt`, `ChallengeRepository.kt`, `UsageRepository.kt`, `DataManagementRepository.kt`
- `app/src/main/java/com/example/platform/usage/UsageStatsGateway.kt`, `AndroidUsageStatsGateway.kt`
- `app/src/main/java/com/example/notifications/NotificationHelper.kt`, `FocusWorkers.kt`
- `app/src/main/java/com/example/FocusForestApplication.kt`
- `app/src/main/java/com/example/ui/components/BotanicalCharts.kt`
- `app/src/main/java/com/example/features/home/TodayViewModel.kt`, `TodayScreen.kt`
- `app/src/main/java/com/example/features/focus/FocusViewModel.kt`, `FocusScreen.kt`
- `app/src/main/java/com/example/features/challenges/ChallengesViewModel.kt`, `ChallengesScreen.kt`
- `app/src/main/java/com/example/features/insights/InsightsViewModel.kt`, `InsightsScreen.kt`
- `app/src/main/java/com/example/features/forest/ForestScreen.kt`
- `app/src/main/java/com/example/features/shop/ShopViewModel.kt`, `ShopScreen.kt`
- `app/src/main/java/com/example/features/settings/SettingsViewModel.kt`, `SettingsScreen.kt`, `PrivacyScreen.kt`
- `app/src/test/java/com/example/domain/DomainEnginesTest.kt`, `UsageAnalyticsEngineTest.kt`
- `app/src/test/java/com/example/platform/usage/AndroidUsageStatsGatewayTest.kt`
- `app/src/test/java/com/example/data/PersistenceAndExportTest.kt`
- `app/src/test/java/com/example/ui/FocusForestUiTest.kt`

## 6. Architecture Changes
- Centralized reward granting into `RewardAuthority` backed by `RewardLedgerEntity` (`reward_ledger`) inside Room transactions (`MIGRATION_1_2` upgrading DB v1 → v2).
- Separated pure challenge evaluation (`ChallengeEngine.evaluate`) from ledger crediting so `rewardClaimed` never flips to `true` without persisted XP/seed updates.
- Extracted `SessionCompletionCoordinator` and `WeeklySummaryCoordinator` out of `CoroutineWorker` classes for direct deterministic testing.
- Preserved `LocalFreeEntitlementGateway` behind `EntitlementGateway` with zero fake purchase states.

## 7. Usage Tracking Corrections
- Introduced `UsageQueryResult` (`PermissionDenied`, `TrackingDisabled`, `SuccessWithData`, `SuccessZeroUsage`, `Unavailable`, `Failure`) and `SnapshotCompleteness` (`COMPLETE`, `PARTIAL_TODAY`, `ZERO_USAGE`, `UNAVAILABLE`).
- Eliminated the bug dropping `<1m` apps: `foregroundMillis` values are aggregated first across all apps, and sub-minute apps display as `<1m` via `AppUsageSummary.displayDuration`.
- Eliminated partial-today comparisons: `improvementPercent` strictly compares the latest completed day (`comparableCompletedDayDate`) against the 7 completed days prior to it; weekly comparisons strictly require two full 7-day completed windows. Today's partial usage tracks against an editable `dailyUsageBudgetMinutes`.
- Added `Mutex` + `CompletableDeferred` coalescing and 30s TTL caching in `UsageRepository`, querying a 14-day window + Room backfill.

## 8. Timer Corrections
- Cooldown/UI ticker runs at a 1s interval while monotonic `elapsedRealtime()` and `bootCount()` remain the source of truth in `FocusTimerEngine`.
- Added `try/finally` completion guard and error/retry UI recovery in `FocusViewModel`.
- `SessionCompletionCoordinator` validates session ID and `RUNNING` state before completing, reschedules if remaining time exists, marks reboot-expired sessions `INTERRUPTED` without rewards, and notifies only after non-duplicate completion.

## 9. Reward-Integrity Corrections
- `XpLevelEngine.calculateSessionReward` returns `(0, 0)` for `SHORT_BREAK` and `LONG_BREAK`.
- `FocusTimerEngine.buildCompletedSession` sets `rewardGranted = false` for breaks; `FocusRepository.recordCompletedSession` skips tree planting, skips focus-minute/session increments, and avoids creating spurious empty `DailyStatsEntity` rows on plain breaks while still recording the completed break once.
- `ChallengeRepository.refreshProgress` atomically commits challenge XP, seeds, Leaf Points, and unlocked achievements via `RewardAuthority.applyChallengeEvaluationAndSyncTotalsInTransaction`.
- Added custom challenge bounds validation, edit guards (disallowed once rewarded), archive-instead-of-hard-delete for rewarded custom challenges, and a daily limit of `3` rewarded custom challenges (`RewardLedgerDao.countBySourceSince` filtering `xpDelta > 0 OR seedsDelta > 0`).

## 10. New Features
- **Pomodoro Chain Mode**: Configurable rounds (`1–8`), focus/short/long break lengths, long-break frequency, manual vs automatic transitions, banner progress, DataStore persistence, and full lifecycle recovery.
- **Procedural Ambient Soundscapes**: Pure offline DSP `AudioTrack` synthesis (`Soft Rain`, `Forest Wind`, `Ocean Waves`, `Hearth Ember`, `Deep Focus Hum`), volume control, and optional timer sync.
- **Streak Calendar & Advanced Insights**: Interactive monthly streak calendar (`GOAL_MET`, `FOCUS_ACTIVE`, `REST_DAY`, `MISSED`, `FUTURE`) with day tap inspection; `7D`/`30D`/`90D` trend windows; genuine `startedAtWallMs` time-of-day buckets; monthly consistency %; session-type breakdown.
- **Forest Timeline & Botanical Shop Filters**: `All` / `Owned` filters, clear Leaf Point balances, and insufficient-points snackbar guidance.
- **Weekly Reflection Worker**: `WeeklySummaryWorker` synced via `FocusWorkers.syncAllSchedules` and respecting global toggle, weekly toggle, API 33+ permission, and quiet hours.

## 11. Privacy and Backup Changes
- Disabled backup in `AndroidManifest.xml` (`android:allowBackup="false"`) and excluded database, DataStore, shared prefs, files, and external domains in `backup_rules.xml` and `data_extraction_rules.xml`.
- Updated `PrivacyScreen` and `strings.xml` usage disclosure to reflect local-only rules, backup exclusion, and usage scope accurately.
- Hardened `DataManagementRepository.deleteAllLocalData` to stop ambient audio, cancel all WorkManager work, clear notifications, delete cached CSVs, clear all Room tables (`reward_ledger` included), clear DataStore, and reset `UsageRepository` memory cache.

## 12. Dependency Changes
- Updated `app/build.gradle.kts` compile/JVM target to Java 17 (`JavaVersion.VERSION_17` / `JvmTarget.JVM_17`).
- Kept AGP `9.1.1`, Kotlin `2.2.10`, Compose BOM `2024.09.00`, Room `2.7.0`, WorkManager `2.10.0`, and DataStore `1.1.7` unchanged to preserve tested environment compatibility; unused Firebase/network/camera dependencies remain commented out.

## 13. Unit Tests Executed & Actual Results
Command: `gradle :app:testDebugUnitTest --rerun-tasks` → **BUILD SUCCESSFUL (32 executed, 32 passed, 0 failed, 0 skipped)**:
- `DomainEnginesTest`: 10/10 passed
- `FocusTimerEngineTest`: 3/3 passed
- `UsageAnalyticsEngineTest`: 3/3 passed
- `AndroidUsageStatsGatewayTest`: 2/2 passed
- `PersistenceAndExportTest`: 3/3 passed
- `RoomMigrationAndLedgerTest`: 3/3 passed
- `NotificationHelperTest`: 1/1 passed
- `TimerWorkerAndChainTest`: 4/4 passed
- `ExampleUnitTest`: 1/1 passed
- `ExampleRobolectricTest`: 1/1 passed

## 14. UI Tests Executed & Actual Results
Included in `gradle :app:testDebugUnitTest --rerun-tasks` via Robolectric (`@Config(sdk = [36])`):
- `FocusForestUiTest`: 1/1 passed (covers onboarding skip-usage flow, Today intention card, single timer start/pause/abort, Pomodoro Chain Mode configuration, Ambient Soundscape chip switching, Custom Challenge validation error + creation, Insights `30 Days` filter + Streak Calendar tap + Usage unavailable state, Forest & Botanical Shop `Owned` filter, Dark Theme switch, and Privacy verification).

## 15. Gradle Build Results
- `compile_applet` (after final source edits): **Build succeeded - the applet is compiled**.

## 16. Lint Results
- `lint_applet` (`:app:lintDebug` / `:app:lint`): **BUILD SUCCESSFUL — 0 errors** (65 pre-existing informational/template warnings such as unused template colors/nav strings and newer version checks in `libs.versions.toml`).
- Resolved `MissingPermission` lint errors in `NotificationHelper.kt` by adding inline `ContextCompat.checkSelfPermission` and `SecurityException` guards before `NotificationManagerCompat.notify`.

## 17. Known Defects & Environment Limitations
- **Kotlin Namespace (`com.example` vs `com.azanahmed.focusforest`)**: `applicationId` is `"com.azanahmed.focusforest"`, while `namespace = "com.example"` and `com/example/` directories were intentionally kept unchanged per AI Studio workspace environment constraints (Section 2.2 refactor deferred to external OpenCode/git branch if full R-class package rename is desired).
- **Gradle Wrapper & Git Repository**: Workspace omits `./gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, and `.git` metadata; `.github/workflows/android-ci.yml` expects `./gradlew` once generated locally (`gradle wrapper --gradle-version 9.3.1`).
- **Compose BOM**: Kept on `2024.09.00` for stability with existing libraries rather than upgrading to `2026.09.00`.

## 18. Physical-Device Verification Still Needed
All tests ran on JVM / Robolectric (`sdk = [36]`). No physical device or emulator was attached in this execution environment. Verify on physical hardware (ideally API 26, API 30, API 33, API 36):
- Real `UsageStatsManager` buckets across midnight, timezone switches, and OEM power managers.
- `AndroidAmbientAudioController` `AudioTrack` speaker/headphone playback and audio focus interruptions during active timers.
- Real WorkManager Doze-mode wakeup behavior for `SessionCompletionWorker`, `FocusReminderWorker`, and `WeeklySummaryWorker`.
- `FileProvider` CSV share sheet & TalkBack screen reader traversal + font-scaling layout inspection.

## 19. Features Deferred to Next Backend / Billing Phase
Per Phase 0.1 policy (no mock billing, no fake users/social rooms, no local Pro unlocks):
- User accounts & Google Sign-In
- Cloud sync & backup/restore backend
- Friends, leaderboards, and real-time social challenge rooms
- Google Play Billing subscriptions (`Focus Forest Plus`), purchase token verification, and server-verified entitlements

## 20. Recommended Next Engineering Phase
- **Phase 0.2 (Pre-Release Physical QA & Hygiene)**: In a local git workspace, run `gradle wrapper --gradle-version 9.3.1`, optionally refactor `namespace` + directories from `com.example` to `com.azanahmed.focusforest`, complete physical-device QA matrix, move static `.jpg` drawables to `drawable-nodpi` / trim unused template `colors.xml` entries, and verify `.github/workflows/android-ci.yml` on GitHub Actions.
- **Phase 1.0 (Authenticated Backend & Play Billing)**: Introduce server-verified auth/sync and real Google Play Billing integrated cleanly behind `EntitlementGateway`.
