# Focus Forest — Phase 0.3 Product Hardening & Feature Expansion Report

## 1. Executive Summary

Phase 0.3 hardened the existing local-first product instead of adding a backend or
speculative platform work:

- **Reliability**: cancellation is now propagated through usage refresh, completion
  workers, the Android usage gateway, and ambient audio; timer start has a synchronous
  double-tap guard; duplicate completion can no longer clear a newer chain step; chain
  recovery refuses unrelated timers and evaluates recovered steps against elapsed time.
- **Challenge behavior**: usage is refreshed before challenge evaluation, usage deletion
  immediately re-evaluates progress, and completed/expired goals remain visible in a local
  Challenge History.
- **Product/UX**: Today shows a Monday–Sunday focus total and weekly intention derived from
  the existing daily goal; the focus countdown exposes explicit spoken-duration semantics.
- **Integrity**: XP level math is overflow-safe; the Room schema remains version 2 with
  `MIGRATION_1_2` and no destructive migration.
- **Verification**: a clean build produced **43/43 passing tests**, **0 lint errors** (66
  informational warnings), and a debug APK with **no `INTERNET` or
  `ACCESS_NETWORK_STATE`** permission.

No physical API 36 or TalkBack verification is claimed. No dependency upgrades, cloud
services, accounts, analytics, ads, billing, or AI features were added.

## 2. Branch, Base, and Scope

- **Base commit**: `17377a9` (`docs: record Phase 0.2 build recovery and device QA results`).
- **Working branch**: `phase-0.3/product-hardening-and-feature-expansion`.
- No commit or push to `main`; no force-push or history rewrite.
- No Supabase, auth, cloud sync, social features, leaderboards, ads, remote analytics,
  billing, or AI.
- No Room schema change. The database remains version 2 with `MIGRATION_1_2`.
- `applicationId` and namespace remain `com.azanahmed.focusforest`; `minSdk 26`,
  `compileSdk 36`, `targetSdk 36`.
- No dependency versions changed in `gradle/libs.versions.toml` or `app/build.gradle.kts`.

## 3. Environment

| Item | Value |
| :--- | :--- |
| OS | Linux local workspace |
| JDK | Eclipse Temurin OpenJDK **21.0.8** |
| Gradle | **9.3.1** via committed wrapper |
| Android Gradle Plugin | **9.1.1** |
| Kotlin | **2.2.10** |
| compileSdk / targetSdk | **36** |
| minSdk | **26** |
| Java bytecode target | **17** |
| Room | **2.7.0**, DB version **2**, `MIGRATION_1_2` unchanged |
| Robolectric | **4.16.1**, `@Config(sdk = [36])` |
| Build flags | `--no-daemon -Djava.awt.headless=true` |

## 4. Product Additions

### 4.1 Local Challenge History

- `ChallengeRepository` now exposes `challengeHistory`, a flow of archived, expired, and
  past-period challenges derived from the existing `challenges` and `challenge_progress`
  tables.
- `ChallengesViewModel` combines active and history flows and keeps current-period
  challenges out of the history list to avoid duplicates.
- `ChallengesScreen` adds a **Challenge History** section, expired styling, an expired
  status label, and stable test tags. Current completed challenges remain in their active
  period list until the period rolls over.
- No new table, migration, network call, or upload path was added.

### 4.2 Weekly Focus Intention on Today

- `TodayViewModel` computes Monday–Sunday focus minutes from existing `daily_stats` and
  derives a weekly target from the user's daily goal (`dailyGoal × 7`).
- `TodayScreen` shows a local progress bar and a summary such as
  `120 / 350 min • 34% of your weekly intention`.
- The weekly value is derived in memory; no new profile or analytics record is persisted.

### 4.3 Spoken Timer Semantics

- The visual `mm:ss` countdown now carries an explicit screen-reader description such as
  “24 minutes 12 seconds remaining, paused”.
- The description is **not** a live region, so TalkBack is not interrupted every second.
- `FocusScreen` no longer performs a duplicate `ON_RESUME` sync; `ON_START` handles
  foreground reconciliation.

## 5. Reliability and Integrity Fixes

### 5.1 Cancellation Is No Longer Swallowed

- `UsageRepository.refreshUsageData()` rethrows `CancellationException`, cancels joined
  waiters, and clears `isRefreshing` instead of caching a phantom error dashboard.
- `SessionCompletionWorker.doWork()` rethrows cancellation rather than converting it into
  a WorkManager retry/failure.
- `AndroidUsageStatsGateway` rethrows cancellation from the platform query instead of
  wrapping it in `UsageQueryResult.Failure`.
- `AmbientSoundEngine` rethrows cancellation after releasing the `AudioTrack`, without
  marking a bogus audio failure.

### 5.2 Timer Start and Recovery Guards

- `FocusViewModel` uses a synchronous `timerStartPending` flag around start/next-step
  launches, closing the double-tap window where two coroutines could both observe an empty
  timer.
- Initial recovery and `retryPendingCompletion()` respect the same guard; foreground sync
  skips while a start is pending.
- A recovered chain timer is evaluated against wall clock, elapsed realtime, and boot count
  instead of being granted a fresh full duration. The worker does the same and schedules
  the remaining time (minimum 1 second) with a bounded recovery re-entry.

### 5.3 Chain and Completion Integrity

- `UserPreferencesDataStore.clearActiveTimerIfSession(sessionId)` clears the timer only when
  it still matches the finished session.
- Completion and abort paths use that conditional clear, so a late duplicate worker cannot
  delete a newer chain-step timer.
- `markChainWaitingAfterInterruption(chainId, interruptedSessionId)` only parks the chain
  when the interrupted step is still the pending step.
- `recoverChainState()` refuses to attach a standalone or other-chain timer; it parks the
  chain for an explicit user decision and leaves the unrelated timer untouched.

### 5.4 Challenge and XP Integrity

- `ChallengesViewModel` refreshes usage before generation/evaluation, so usage-based
  challenges are not evaluated against a default empty dashboard.
- `InsightsViewModel.confirmDeleteUsageData()` re-evaluates challenge progress after the
  wipe.
- `XpLevelEngine` uses `Long` arithmetic and a `MAX_LEVEL` ceiling, preventing Int overflow
  on extreme or corrupted lifetime XP.

## 6. Tests and Verification

### 6.1 Commands

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --console=plain --no-daemon -Djava.awt.headless=true
```

Result: **BUILD SUCCESSFUL** in 2m 46s (configuration cache reused).

An earlier clean run failed because the new weekly-summary assertion left the Today list
scrolled, hiding the settings button when the test returned to Today. The test was fixed to
scroll back to `today_settings_button`; the assertion was not removed or weakened. The
final clean run above passed.

### 6.2 Test Suites

| Suite | Tests | Result |
| :--- | ---: | :--- |
| `DomainEnginesTest` | 11 | passed |
| `TimerWorkerAndChainTest` | 12 | passed |
| `PersistenceAndExportTest` | 4 | passed |
| `RoomMigrationAndLedgerTest` | 3 | passed |
| `FocusTimerEngineTest` | 3 | passed |
| `UsageAnalyticsEngineTest` | 3 | passed |
| `AndroidUsageStatsGatewayTest` | 2 | passed |
| `NotificationHelperTest` | 1 | passed |
| `FocusViewModelCompletionTest` (Robolectric SDK 36) | 1 | passed |
| `FocusForestUiTest` (Robolectric SDK 36) | 1 | passed |
| `ExampleUnitTest` + `ExampleRobolectricTest` | 2 | passed |
| **Total** | **43** | **43 passed, 0 failed, 0 skipped** |

New Phase 0.3 coverage includes:

- duplicate completion does not clear a newer chain timer;
- chain recovery refuses an unrelated standalone timer;
- a stale interruption cannot clear a newer chain step;
- expired daily challenges appear in history without duplicating today's list;
- usage refresh propagates cancellation and recovers on the next call;
- extreme lifetime XP cannot overflow level calculation;
- the UI countdown exposes “remaining” and “paused” descriptions;
- the Today screen renders the weekly goal summary.

### 6.3 Lint

`lintDebug` completed with **0 errors, 66 warnings**. Warning categories are informational:
`GradleDependency` (25), `UnusedResources` (17), `NewerVersionAvailable` (11),
`IconLauncherShape` (6), `IconLocation` (3), `AndroidGradlePluginVersion` (2),
`RedundantLabel` (1), and `ObsoleteSdkInt` (1). No correctness, security, or manifest
errors.

### 6.4 Debug APK

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Size: **21,050,697 bytes**
- SHA-256: `1cd46b1a0a7715ec1e32768ffb0be8a0ed101dc877662ac11371c1f7f8a3ab39`
- Package: `com.azanahmed.focusforest`, versionCode 1, versionName 1.0.0
- minSdk 26, targetSdk 36, compileSdk 36, debuggable
- Permissions in the merged manifest:
  - `PACKAGE_USAGE_STATS`
  - `POST_NOTIFICATIONS`
  - `WAKE_LOCK`
  - `RECEIVE_BOOT_COMPLETED`
  - `FOREGROUND_SERVICE`
  - `com.azanahmed.focusforest.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
- **No `INTERNET` and no `ACCESS_NETWORK_STATE`.**
- `FileProvider` remains `android:exported="false"` with URI grants enabled;
  `allowBackup="false"`, backup rules, and data-extraction rules remain in place.

## 7. Privacy Review

- No new permission, network dependency, account, SDK, or database table.
- Challenge history and weekly summaries use existing local tables/in-memory derivation.
- Cancellation/recovery fixes change control flow only; no logging or telemetry was added.
- The Room schema is unchanged; `MIGRATION_1_2` remains the only migration.
- The full-wipe path still clears user-owned Room tables, DataStore, WorkManager jobs,
  notifications, cached exports, ambient audio, and the usage cache.
- Future server-backed work must satisfy [FUTURE_BACKEND_BOUNDARIES.md](FUTURE_BACKEND_BOUNDARIES.md).

## 8. Verification Limits

- No API 36 device or emulator was available. Robolectric SDK 36 tests exercise logic and
  Compose semantics, not real hardware behavior.
- No new physical-device run was performed in Phase 0.3. Phase 0.2 device QA remains the
  latest hardware evidence (OnePlus 6T, Android 11 / API 30).
- TalkBack was not run on a device. The spoken countdown semantics are covered by the UI
  test, but a full TalkBack/font-scale pass is still pending.
- Audio audibility, Doze/overnight completion, OEM usage-stat quirks, reboot rescheduling,
  and CSV share-sheet behavior were not re-verified in this phase.
- CI was updated for `phase-0.3/**` but was not pushed or run remotely.

## 9. Files Changed (High Level)

New:

- `docs/PHASE_0_3_BASELINE.md`
- `docs/PHASE_0_3_REPORT.md`
- `docs/FUTURE_BACKEND_BOUNDARIES.md`

Core reliability:

- `data/local/datastore/UserPreferencesDataStore.kt`
- `data/repository/FocusRepository.kt`
- `data/repository/UsageRepository.kt`
- `notifications/FocusWorkers.kt`
- `features/focus/FocusViewModel.kt`
- `platform/audio/AmbientSoundEngine.kt`
- `platform/usage/AndroidUsageStatsGateway.kt`
- `domain/engine/XpLevelEngine.kt`

Product/UX:

- `data/repository/ChallengeRepository.kt`
- `features/challenges/ChallengesViewModel.kt`
- `features/challenges/ChallengesScreen.kt`
- `features/home/TodayViewModel.kt`
- `features/home/TodayScreen.kt`
- `features/focus/FocusScreen.kt`
- `features/insights/InsightsViewModel.kt`

Tests:

- `data/PersistenceAndExportTest.kt`
- `domain/DomainEnginesTest.kt`
- `notifications/TimerWorkerAndChainTest.kt`
- `ui/FocusForestUiTest.kt`

Process/docs:

- `.github/workflows/android-ci.yml`
- `.gitignore`
- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/FEATURE_STATUS.md`
- `docs/PRIVACY_AND_DATA_FLOW.md`
- `docs/TESTING.md`

## 10. Commits

On `phase-0.3/product-hardening-and-feature-expansion`:

- `d1bd298` — fix: harden timer, chain, usage, and audio reliability
- `82f0f3e` — feat: add challenge history, weekly focus goal, and spoken timer semantics
- `b344411` — test: cover timer races, cancellation, history, and accessibility
- `aa9296e` — ci: run Android checks on phase 0.3 branches
- `43d9dfb` — chore: ignore local IDE metadata

This report and the documentation updates are committed as the final docs commit on the
branch. No push, force-push, or commit to `main` was performed.

## 11. Deferred Work and Phase 0.4 Recommendation

Deferred intentionally:

- API 36 physical/emulator QA, TalkBack/font-scale verification, audio audibility, Doze,
  reboot rescheduling, OEM usage-stat behavior, and CSV share-sheet testing.
- Cloud sync, accounts, social features, leaderboards, ads, analytics, billing, and AI.
- Compose/Material/Kotlin upgrades that would force incompatible toolchain changes.
- Any Room schema change beyond a tested, additive migration.

Recommended Phase 0.4 focus:

1. Device/emulator verification on API 36 with the existing test matrix.
2. Accessibility pass with TalkBack and large fonts across onboarding, focus, challenges,
   insights, and privacy.
3. Share-sheet/export and full-wipe verification on-device.
4. Only then consider additional local-only depth; do not add a backend in Phase 0.4.
