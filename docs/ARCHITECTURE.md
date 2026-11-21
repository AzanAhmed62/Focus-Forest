# Architecture

Focus Forest follows single-Activity MVVM with manual constructor dependency injection (`AppContainer`), clean separation between pure Kotlin domain engines, Room + DataStore persistence, Android platform gateways, background workers, and Jetpack Compose UI.

## Package Layout (`app/src/main/java/com/azanahmed/focusforest/`)

- **`core/`**
  - `di/AppContainer.kt`: Application-scoped manual dependency graph.
  - `time/ClockProvider.kt`: Wall-clock, monotonic elapsed realtime, boot count, and timezone abstraction (`SystemClockProvider`) for deterministic timing and tests.
  - `monetization/EntitlementGateway.kt`: Local honest entitlement abstraction (`LocalFreeEntitlementGateway`) keeping all local features open with zero simulated IAP/pro unlocks.
  - `util/CsvExporter.kt`, `util/ShareFormatter.kt`: `FileProvider` CSV exports and usage-free share summaries.
- **`domain/`**
  - `model/`: Pure models (`FocusModels.kt`, `UsageModels.kt`, `ChallengeModels.kt`, `GamificationModels.kt`).
  - `engine/`: Pure, testable business logic:
    - `FocusTimerEngine.kt`: Monotonic countdowns, pause/resume, reboot recovery (`ReadyToComplete`, `Active`, `InterruptedAfterReboot`), abort states.
    - `ChainStateMachine.kt`: Pure, side-effect-free Pomodoro Chain transitions shared by the worker, ViewModel, and cold-start recovery (single progress rule, completion, interruption pause).
    - `XpLevelEngine.kt`: XP & Seed reward rules (strictly `0 XP` and `0 seeds` for restorative breaks), level thresholds, streak milestones.
    - `UsageAnalyticsEngine.kt`: Millisecond-first app aggregation, `SnapshotCompleteness` classification, completed-day and 7v7 baseline comparison.
    - `ChallengeEngine.kt`: Deterministic daily/weekly generation, custom bounds validation, period expiration, side-effect-free evaluation (`ChallengeEvaluationOutput`).
    - `InsightsAnalyticsEngine.kt`: 7/30/90-day windows, genuine `startedAtWallMs` time-of-day buckets, session-type breakdown, category/routine breakdowns from completed sessions in the selected window, average completed-block duration, monthly streak calendar grids.
    - `AchievementEngine.kt`, `StreakCalculator.kt`, `ForestGrowthEngine.kt`.
- **`data/`**
  - `local/db/`: `FocusForestDatabase` (Room v3 + `MIGRATION_1_2` + `MIGRATION_2_3` + schema export), DAOs, entities including `FocusRoutineEntity`, `FocusCategoryEntity`, `RewardLedgerEntity`, mappers.
  - `local/datastore/UserPreferencesDataStore.kt`: Onboarding state, theme, notification preferences, active timer & chain snapshots (including routine/category snapshots), ambient audio settings, monitored packages, daily usage budget, and the independent weekly goal (initialized once from `daily × 7`).
  - `repository/`:
    - `RewardAuthority.kt`: Single transactional ledger authority (`reward_ledger`) preventing duplicate reward credits across ViewModels, workers, challenges, achievements, and milestones, plus anti-farm custom challenge caps (`3/day`).
    - `FocusRepository.kt`: Focus session & Pomodoro chain persistence + completion transactions. Chain continuation is serialized with a `Mutex`, guarded by `lastAdvancedSessionId` (exactly-once progression) and persisted atomically with the next timer (`saveChainAndTimer`); `recoverChainState()` reconciles chain + timer + session after process death or WorkManager-only completion, refuses to attach a timer from another chain, and completion/abort clear the active timer only when it still matches the finished session (`clearActiveTimerIfSession`). Completed sessions copy routine/category snapshots; `updateSessionMetadata` uses a narrow SQL update that can only change `note`/`reflection` on finished sessions.
    - `FocusContextRepository.kt`: Local-only routine and category CRUD, validation, pinning, archive/delete, starter-template seeding, and snapshot propagation into the existing timer/chain models.
    - `ChallengeRepository.kt`: Challenge generation, custom challenge CRUD/validation/archiving, atomic reward evaluation, and a `challengeHistory` flow for archived, expired, and past-period challenges.
    - `UsageRepository.kt`: Coalesced (`Mutex` + `CompletableDeferred`) 30s-cached Android usage polling, 14-day history backfill, budget updates. Coroutine cancellation is propagated instead of being converted into a cached usage error.
    - `ShopAndForestRepository.kt`: Catalog seeding, Leaf Points spending, equipping themes/environments/decorations.
    - `DataManagementRepository.kt`: CSV export intents and full atomic wipe (`deleteAllLocalData`).
- **`platform/`**
  - `usage/UsageStatsGateway.kt`, `AndroidUsageStatsGateway.kt`: `PACKAGE_USAGE_STATS` check, least-privilege launcher queries, typed `UsageQueryResult`, safe system/shared-lib icon exclusion. Platform-query cancellation is rethrown rather than wrapped as a usage failure.
  - `audio/AmbientSoundEngine.kt`: Procedural PCM `AudioTrack` synthesizer (`SOFT_RAIN`, `FOREST_WIND`, `OCEAN_WAVES`, `HEARTH_FIRE`, `BROWN_NOISE`) with audio-focus handling, becoming-noisy pause, lifecycle-aware start/pause, guarded `AudioTrack` state/write checks, single-loop mutex, cancellation propagation, and release in `finally`.
- **`notifications/`**
  - `NotificationHelper.kt`: Notification channels (`focus_session_complete`, `focus_gentle_reminders`), API 33+ permission check, quiet-hours enforcement.
  - `FocusWorkers.kt`: `SessionCompletionWorker` (`SessionCompletionCoordinator`), `FocusReminderWorker`, `WeeklySummaryWorker` (`WeeklySummaryCoordinator`), and `FocusWorkers` central scheduler/canceller. Recovered chain timers are evaluated against the monotonic clock and rescheduled for their remaining duration rather than a fresh full block; worker cancellation is rethrown.
- **`features/` & `navigation/` & `ui/`**
  - StateFlow ViewModels + Compose screens for `onboarding`, `home` (Today), `focus`, `routines`, `history`, `challenges`, `insights`, `forest`, `shop`, `settings`, and `privacy`.
  - Today shows pinned routines, the active/paused session, recent activity, and an independently persisted weekly goal. Focus exposes one-action routine starts, category chips, and session context. Routines provides CRUD for reusable templates and categories. History provides bounded filters and a metadata-only reflection editor. Insights adds category/routine breakdowns with honest sample counts. The focus countdown exposes explicit spoken-duration semantics for screen readers.

## Routines, Context, and Historical Integrity

- A routine is a template, never a live timer. Starting one copies its configuration and name/category snapshots into `ChainConfig` and `ActiveTimerState`; the existing `FocusTimerEngine` and `ChainStateMachine` remain the only timer/chain authorities.
- Sessions store `routineId`/`categoryId` for filtering plus `routineNameSnapshot`/`routineSnapshot`/`categoryNameSnapshot` for display. There are no foreign keys, so renaming, archiving, or deleting a template cannot rewrite an active session or completed history.
- Notes and reflections are written only through `updateSessionMetadata`, which cannot touch reward, duration, status, or ledger columns. They never feed XP, seeds, trees, achievements, or challenges.
- History queries are bounded and filtered in SQL; default views include finished sessions (`COMPLETED`, `ABORTED`, `INTERRUPTED`).

## Reliability Invariants

- A session reward is credited at most once through `reward_ledger`, even when foreground completion, a worker, and cold-start recovery race.
- A late duplicate completion cannot clear a newer chain-step timer.
- An interrupted chain step is parked for explicit user action and cannot clear a newer pending step.
- Cancellation is never treated as a storage failure, usage failure, or audio failure.
- Routine/category edits and deletion cannot mutate an active timer or completed history; display always uses session snapshots.
- Metadata edits cannot change rewards, durations, status, or the reward ledger.
- Room is version 3 with genuine, non-destructive `MIGRATION_1_2` and `MIGRATION_2_3` migrations; no destructive fallback is registered.

## Future Backend Boundary

Focus Forest currently has no backend, accounts, or network permission. Any future server-backed feature must preserve the local-first guarantees documented in [FUTURE_BACKEND_BOUNDARIES.md](FUTURE_BACKEND_BOUNDARIES.md).
