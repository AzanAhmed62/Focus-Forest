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
    - `InsightsAnalyticsEngine.kt`: 7/30/90-day windows, genuine `startedAtWallMs` time-of-day buckets, session-type breakdown, monthly streak calendar grids.
    - `AchievementEngine.kt`, `StreakCalculator.kt`, `ForestGrowthEngine.kt`.
- **`data/`**
  - `local/db/`: `FocusForestDatabase` (Room v2 + `MIGRATION_1_2` + schema export), DAOs, entities including `RewardLedgerEntity`, mappers.
  - `local/datastore/UserPreferencesDataStore.kt`: Onboarding state, theme, notification preferences, active timer & chain snapshots, ambient audio settings, monitored packages, daily usage budget.
  - `repository/`:
    - `RewardAuthority.kt`: Single transactional ledger authority (`reward_ledger`) preventing duplicate reward credits across ViewModels, workers, challenges, achievements, and milestones, plus anti-farm custom challenge caps (`3/day`).
    - `FocusRepository.kt`: Focus session & Pomodoro chain persistence + completion transactions. Chain continuation is serialized with a `Mutex`, guarded by `lastAdvancedSessionId` (exactly-once progression) and persisted atomically with the next timer (`saveChainAndTimer`); `recoverChainState()` reconciles chain + timer + session after process death or WorkManager-only completion, refuses to attach a timer from another chain, and completion/abort clear the active timer only when it still matches the finished session (`clearActiveTimerIfSession`).
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
  - StateFlow ViewModels + Compose screens for `onboarding`, `home` (Today), `focus`, `challenges`, `insights`, `forest`, `shop`, `settings`, and `privacy`.
  - Today derives a Monday–Sunday focus total and weekly target from existing daily stats. Challenges exposes local history for completed/expired goals. The focus countdown exposes explicit spoken-duration semantics for screen readers.

## Reliability Invariants

- A session reward is credited at most once through `reward_ledger`, even when foreground completion, a worker, and cold-start recovery race.
- A late duplicate completion cannot clear a newer chain-step timer.
- An interrupted chain step is parked for explicit user action and cannot clear a newer pending step.
- Cancellation is never treated as a storage failure, usage failure, or audio failure.
- No Room schema change was made in Phase 0.3; the database remains version 2 with `MIGRATION_1_2`.

## Future Backend Boundary

Focus Forest currently has no backend, accounts, or network permission. Any future server-backed feature must preserve the local-first guarantees documented in [FUTURE_BACKEND_BOUNDARIES.md](FUTURE_BACKEND_BOUNDARIES.md).
