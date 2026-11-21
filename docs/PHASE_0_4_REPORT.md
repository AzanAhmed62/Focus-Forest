# Focus Forest — Phase 0.4 Focus Workflows & Personalization Report

## 1. Executive Summary

Phase 0.4 makes starting and understanding a focus session faster without weakening the
local-first architecture or the Phase 0.3 timer/chain guarantees:

- **Reusable routines**: named templates reuse the existing `ChainConfig`,
  `FocusTimerEngine`, and `ChainStateMachine`. A routine can be started in one action from
  Today, Focus, or the Routines screen.
- **Session context**: optional categories, intentions, notes, and reflections. Completed
  sessions copy routine and category snapshots, so renaming, archiving, or deleting a
  template cannot rewrite history.
- **Independent goals**: the daily goal is preserved, and the weekly goal is now a separate
  DataStore value initialized once from the previous effective intention (`daily × 7`).
- **Useful history**: a dedicated bounded, filterable history screen with a detail view for
  stored facts and metadata-only note/reflection editing.
- **Honest insights**: category and routine breakdowns scoped to the selected 7D/30D/90D
  window, plus average completed-block duration and sample counts.
- **Integrity**: Room v3 adds two tables and seven session columns through genuine
  non-destructive migrations (`1 -> 2`, `2 -> 3`, and the chained `1 -> 3`). Notes and
  reflections can never change XP, seeds, trees, achievements, or challenges.
- **Privacy**: no new permission, network dependency, account, SDK, telemetry, ads,
  billing, or AI. A full wipe clears the new tables, metadata, weekly goal, caches, and
  scheduled work.

## 2. Branch, Base, and Scope

- **Base commit**: `d1b8a8f665ae90fa14870fd9ddc4a550724196e0` (Phase 0.3 final).
- **Working branch**: `phase-0.4/focus-workflows-and-personalization`.
- No commit or push to `main`; no force-push, reset, or history rewrite.
- No Supabase, Firebase, auth, cloud sync, social features, leaderboards, ads, remote
  analytics, billing, or AI.
- No new permissions and no `INTERNET` or `ACCESS_NETWORK_STATE`.
- No dependency upgrades. `applicationId`/namespace remain
  `com.azanahmed.focusforest`; `minSdk 26`, `compileSdk 36`, `targetSdk 36`.
- Existing Phase 0.3 timer, chain, recovery, reward, challenge, and usage behavior is
  preserved. Routines copy configuration into the existing engines rather than duplicating
  timer logic.

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
| Room | **2.7.0**, DB version **3**, `MIGRATION_1_2` unchanged, new `MIGRATION_2_3` |
| Robolectric | **4.16.1**, `@Config(sdk = [36])` |
| Build flags | `--no-daemon -Djava.awt.headless=true` |

## 4. Product Additions

### 4.1 Reusable Routines and One-Action Starts

- New `focus_routines` table and `FocusRoutine` model: name, description, focus/short/long
  minutes, rounds, long-break cadence, auto-transition, ambient preset, default category,
  pinned/archived state, sort order, and created/last-used timestamps.
- `FocusContextRepository` owns routine CRUD, validation, pinning, duplication, archive,
  delete, and starter seeding. Templates are never live timers.
- `FocusViewModel.startRoutine()` copies the routine configuration and name/category
  snapshots into the existing chain/timer start path. `FocusRoute(routineId, requestId)`
  plus `SavedStateHandle` one-shot keys make Today/Focus starts survive navigation without
  restarting on ViewModel recreation.
- Today shows pinned routines as one-tap starts. Focus shows up to four saved routines and
  a Manage entry. The Routines screen provides create/edit/delete/archive and category
  management.
- Editing a routine, renaming its category, or deleting it cannot mutate an active timer or
  a completed session. Both hold copies, not foreign keys.

### 4.2 Session Context and Categories

- New `focus_categories` table and default categories: Study, Coding, Reading, Work,
  Personal, Exercise.
- `focus_sessions` gains `routineId`, `routineNameSnapshot`, `routineSnapshot`,
  `categoryId`, `categoryNameSnapshot`, `note`, and `reflection`.
- The Focus screen exposes category chips and an optional intention. Active sessions show
  their routine/category context.
- Completed sessions store name/configuration snapshots. History and insights display
  snapshots, never a live lookup against the current template table.
- `FocusRepository.updateSessionMetadata` uses a narrow SQL update that only changes
  `note`/`reflection` on finished sessions. It cannot touch reward, duration, status, or
  ledger columns. Notes and reflections never affect XP, seeds, trees, achievements, or
  challenges.

### 4.3 Independent Daily and Weekly Goals

- The existing daily goal remains in both the profile and DataStore.
- A new DataStore `weekly_goal_minutes` value is independent, supports `0` (disabled), and
  is capped at 3360 minutes.
- Onboarding calls `ensureWeeklyGoalInitialized()` once, persisting the previous effective
  intention (`daily × 7`). Later daily-goal edits do not rewrite an explicit weekly goal.
- Settings exposes a weekly slider and clear "No target" state. Today displays the weekly
  total against the independent target. `daily_stats.goalMinutes` remains the daily
  snapshot written at completion.

### 4.4 Filterable History

- New `HistoryViewModel`/`HistoryScreen` with date range, category (including
  uncategorized), routine, status, and text filters.
- Queries are bounded and filtered in SQL (`LIMIT`); the default view shows finished
  sessions (`COMPLETED`, `ABORTED`, `INTERRUPTED`).
- Session cards show type, status, duration, routine/category snapshots, intention, note
  preview, XP/seeds, and reflection.
- The detail dialog shows stored facts and snapshots and allows metadata-only note and
  reflection edits. It states plainly that these fields never change rewards.

### 4.5 Category and Routine Insights

- `InsightsAnalyticsEngine` adds category and routine breakdowns, average completed focus
  minutes, and honest sample counts, scoped to the selected 7D/30D/90D window.
- Breakdowns group by label id when present, display the most recent non-blank snapshot
  name, and exclude unlabelled sessions from the denominator.
- Aborted, interrupted, break, and out-of-window sessions are excluded from completed-focus
  averages and breakdowns.

### 4.6 CSV Export

- The existing header prefix is unchanged. Seven real fields are appended:
  `routine_id,routine_name,routine_snapshot,category_id,category_name,note,reflection`.
- Comma, quote, newline, Unicode, and locale-independent ISO-offset timestamp handling is
  preserved. Export remains explicit and local through the non-exported `FileProvider`.

### 4.7 Accessibility and Calm UX

- New controls have explicit labels/test tags; routine starts are single-action buttons.
- History filters and reflection chips use standard Compose semantics and touch targets.
- The UI adds no per-second live region and no new interrupting animation.
- Device TalkBack and large-font verification remains deferred; no claim is made here.

## 5. Data, Schema, and Migrations

### 5.1 Room v3

`MIGRATION_2_3`:

- creates `focus_routines` and `focus_categories` with indexes;
- adds the seven session-context columns with non-null defaults;
- adds indexes on `focus_sessions.routineId` and `focus_sessions.categoryId`;
- seeds the documented starter categories/routines once for existing installs.

`MIGRATION_1_2` is untouched. No destructive migration is registered. The exported schema
is `app/schemas/com.azanahmed.focusforest.data.local.db.FocusForestDatabase/3.json`.

New installs seed the same starter templates after onboarding. Ordinary app starts never
re-seed, so a user who deletes every routine does not get them silently recreated.

### 5.2 Snapshot and Deletion Semantics

- No foreign keys. Ids are used for filtering; display uses per-session snapshots.
- Deleting a routine or archiving/deleting a category cannot corrupt history.
- `clearAllUserData` clears the new tables and all session metadata.
- `deleteAllLocalData` also clears DataStore (including the weekly goal), cancels workers
  and notifications, deletes cached exports, and stops ambient audio. Only the documented
  starter templates may be re-seeded after onboarding.

## 6. Integrity and Reliability Decisions

- Routines reuse `FocusTimerEngine` and `ChainStateMachine`; no second timer implementation
  exists.
- Starting a routine respects the existing synchronous `timerStartPending` guard and
  persisted timer/chain recovery, so it cannot start a second timer.
- Chain steps copy routine/category snapshots when constructed; recovery never re-reads a
  template row.
- Metadata updates are narrow SQL and reject active/missing sessions and overlong notes.
- `FocusScreen` disables the start button until `isReady`, closing an asynchronous
  initialization race observed by `FocusViewModelCompletionTest`. The test now waits for
  readiness instead of weakening its assertion.
- Onboarding seeds starter routines/categories and initializes the weekly goal **before**
  writing the onboarding-complete flag. Writing the flag first disposed the onboarding
  ViewModel and cancelled its scope before seeding finished; the extended UI test covers
  the corrected ordering.
- History and insight queries stay bounded; insights remain on the existing windows.
- The Phase 0.4 workflow test runs its blocking event loop on `Dispatchers.IO` instead of
  the Robolectric main thread. A forced rerun was once observed hung with the main thread
  blocked inside `runBlocking`; moving the loop off the main thread removes that blockage
  without weakening any assertion. The genuine clean run completed 51/51 tests.

## 7. Tests and Verification

### 7.1 Commands

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew --version
JAVA_HOME=/opt/jdk-21 ./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --console=plain --no-daemon -Djava.awt.headless=true
```

### 7.2 Test Suites

| Suite | Tests | Result |
| :--- | ---: | :--- |
| `DomainEnginesTest` | 12 | passed |
| `FocusTimerEngineTest` | 3 | passed |
| `UsageAnalyticsEngineTest` | 3 | passed |
| `AndroidUsageStatsGatewayTest` | 2 | passed |
| `PersistenceAndExportTest` | 4 | passed |
| `RoomMigrationAndLedgerTest` | 5 | passed |
| `FocusContextWorkflowTest` | 5 | passed |
| `NotificationHelperTest` | 1 | passed |
| `TimerWorkerAndChainTest` | 12 | passed |
| `FocusViewModelCompletionTest` (Robolectric SDK 36) | 1 | passed |
| `FocusForestUiTest` (Robolectric SDK 36) | 1 | passed |
| `ExampleUnitTest` + `ExampleRobolectricTest` | 2 | passed |
| **Total** | **51** | **51 passed, 0 failed, 0 skipped** |

New Phase 0.4 coverage includes:

- routine start copies snapshots; template edit/rename/delete cannot rewrite an active
  timer or completed history;
- metadata edits leave XP, seeds, durations, status, daily stats, forest totals, and the
  reward ledger unchanged; active sessions reject notes; overlong/missing updates fail;
- weekly goal stays independent from the daily goal, supports `0`, and initializes exactly
  once without overwriting explicit values;
- full wipe clears routines, categories, notes, reflections, weekly goal, theme, and usage
  consent;
- CSV keeps the stable header prefix and round-trips Unicode, commas, quotes, multiline
  fields, and locale-independent timestamps;
- insight breakdowns stay inside the supplied window and display the latest snapshot name
  without resolving deleted ids against current tables;
- the end-to-end UI journey covers starter-routine navigation, an aborted session in
  History, and the metadata dialog.

### 7.3 Lint

`lintDebug` completed with **0 errors, 59 warnings** on the final clean run. Every warning
is informational: `GradleDependency` (23), `UnusedResources` (17),
`NewerVersionAvailable` (6), `IconLauncherShape` (6), `IconLocation` (3),
`AndroidGradlePluginVersion` (2), `RedundantLabel` (1), and `ObsoleteSdkInt` (1). No
correctness, security, or manifest errors.

The Phase 0.4 baseline recorded 66 warnings; the difference is entirely in lint's
dependency-version notices (`GradleDependency`/`NewerVersionAvailable`), which vary with
the local repository metadata cache. The source/resource warning set is unchanged.

### 7.4 Debug APK

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Size: **21,296,457 bytes**
- SHA-256: `a6ea0770e22567b32965fad68ab02af6c87f3b00eac164ed82713094e8e0fcb5`
- Package: `com.azanahmed.focusforest`, versionCode 1, versionName 1.0.0
- minSdk 26, targetSdk 36, compileSdk 36, debuggable
- Merged permissions: `PACKAGE_USAGE_STATS`, `POST_NOTIFICATIONS`, `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, and
  `com.azanahmed.focusforest.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (the AndroidX
  dynamic receiver permission).
- **No `INTERNET` and no `ACCESS_NETWORK_STATE`.**

### 7.5 Final Clean Verification

Verification was run on the committed branch tip with JDK 21 and the committed Gradle
wrapper. A previous clean run restored tests `FROM-CACHE`, so the final run used
`--no-build-cache`; `clean` plus that flag forces every task to execute.

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew --version
JAVA_HOME=/opt/jdk-21 ./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --no-build-cache --console=plain --no-daemon -Djava.awt.headless=true
```

- `--version`: Gradle **9.3.1**, Launcher JVM **21.0.8** (Eclipse Adoptium 21.0.8+9-LTS),
  Gradle-embedded Kotlin 2.2.21, Linux. The project uses the Kotlin 2.2.10 plugin.
- Build result: **BUILD SUCCESSFUL in 10m 36s**; 59 actionable tasks:
  **58 executed, 1 up-to-date**.
- `:app:testDebugUnitTest`: executed (not `FROM-CACHE`) — **51 tests, 51 passed,
  0 failures, 0 errors, 0 skipped**.
- `:app:lintDebug`: **0 errors, 59 warnings** (informational categories in section 7.3).
- `:app:assembleDebug`: successful; APK size, SHA-256, and merged permissions are in
  section 7.4.
- `git status --short`: clean after the final documentation commit. The branch was not
  pushed or merged.

## 8. Privacy Review

- No new permission, network dependency, account, SDK, telemetry, ads, or billing.
- Routine/category names, session notes, and reflections are user-owned local data. They
  are never uploaded or included in share text; CSV export happens only on explicit user
  action.
- The new tables and columns are cleared by the full-wipe path. The independent weekly goal
  is cleared with DataStore preferences.
- Snapshots protect historical records from template edits and deletion.
- See [PRIVACY_AND_DATA_FLOW.md](PRIVACY_AND_DATA_FLOW.md) for the detailed data-flow
  review.

## 9. Verification Limits

- No API 36 physical device or emulator was available. Robolectric SDK 36 exercises logic
  and Compose semantics, not real hardware behavior.
- No physical TalkBack or large-font pass was performed. Accessibility labels are covered
  by code and UI tests only.
- Audio audibility, Doze/overnight completion, OEM usage-stat behavior, reboot
  rescheduling, and CSV share-sheet behavior were not re-verified in this phase.
- Migration from a real Phase 0.3 installation on-device was not performed; migrations are
  covered by Robolectric SQLite migration tests.
- Remote CI was not run and nothing was pushed.

## 10. Files Changed (High Level)

New:

- `app/src/main/java/com/azanahmed/focusforest/features/routines/`
- `app/src/main/java/com/azanahmed/focusforest/features/history/`
- `app/src/test/java/com/azanahmed/focusforest/data/FocusContextWorkflowTest.kt`
- `app/schemas/.../3.json`
- `docs/PHASE_0_4_BASELINE.md`
- `docs/PHASE_0_4_REPORT.md`

Data and domain:

- `data/local/db/` (v3 entities, DAOs, migration, clear-all)
- `data/local/datastore/UserPreferencesDataStore.kt`
- `data/repository/FocusContextRepository.kt`
- `data/repository/FocusRepository.kt`
- `data/repository/DataManagementRepository.kt`
- `domain/engine/FocusTimerEngine.kt`
- `domain/engine/ChainStateMachine.kt`
- `domain/model/FocusModels.kt`
- `domain/engine/InsightsAnalyticsEngine.kt`

UI and integration:

- `features/focus/FocusViewModel.kt`, `FocusScreen.kt`
- `features/home/TodayViewModel.kt`, `TodayScreen.kt`
- `features/settings/SettingsViewModel.kt`, `SettingsScreen.kt`
- `features/onboarding/OnboardingViewModel.kt`
- `features/insights/InsightsViewModel.kt`, `InsightsScreen.kt`
- `navigation/FocusForestNavHost.kt`, `FocusForestRoutes.kt`
- `core/util/CsvExporter.kt`

Tests and docs:

- `data/RoomMigrationAndLedgerTest.kt`
- `domain/DomainEnginesTest.kt`
- `features/focus/FocusViewModelCompletionTest.kt`
- `ui/FocusForestUiTest.kt`
- `README.md`, `docs/ARCHITECTURE.md`, `docs/FEATURE_STATUS.md`, `docs/TESTING.md`,
  `docs/PRIVACY_AND_DATA_FLOW.md`, `docs/FUTURE_BACKEND_BOUNDARIES.md`

## 11. Commits

On `phase-0.4/focus-workflows-and-personalization`:

- `a591366` — chore: establish phase 0.4 baseline
- `d8dcc88` — feat(data): add room v3 routines, categories, and session context
- `0caef06` — feat(focus): add one-action routine starts and session context
- `56bbe2d` — feat(insights): add category and routine breakdowns
- `7a7a569` — feat(data): export session context fields in CSV
- `87d8801` — feat(today): integrate routines, history, and weekly goals
- `6a44634` — ci: run Android checks on phase 0.4 branches
- `7c3a939` — test: keep workflow scenario off the Robolectric main thread
- final docs commit — README and Phase 0.4 documentation

No push, force-push, or commit to `main` was performed.

## 12. Deferred Work and Phase 0.5 Recommendation

Deferred intentionally:

- API 36 physical/emulator QA, TalkBack/font-scale verification, audio audibility, Doze,
  reboot rescheduling, OEM usage-stat behavior, CSV share-sheet testing, and on-device
  migration from a real Phase 0.3 install.
- Cloud sync, accounts, social features, leaderboards, ads, analytics, billing, and AI.
- Dependency or Compose/Material upgrades that would force incompatible toolchain changes.

Recommended Phase 0.5 focus:

1. Device/emulator verification on API 36 with the existing matrix, including migration
   from a Phase 0.3 database and a full-wipe/onboarding cycle.
2. TalkBack and large-font pass across onboarding, focus, routines, history, insights,
   challenges, and privacy.
3. Share-sheet/export verification with real Unicode/multiline notes.
4. Continue local-only depth only where it can be verified without a server.
