# Testing Strategy & Suites

Focus Forest uses local JVM unit tests and Robolectric tests (targeting SDK 36) without relying on emulators or ADB.

## Test Suites (`app/src/test/java/com/example/`)

1. **`domain/DomainEnginesTest.kt`**
   - Verifies `XpLevelEngine` rewards (focus blocks grant XP & seeds; `SHORT_BREAK` / `LONG_BREAK` strictly return `0 XP` and `0 seeds`).
   - Verifies monotonic XP levels, `StreakCalculator` rest-day bridging & grace rules, deterministic daily/weekly challenges, `ChallengeEngine` deferred reward crediting, custom validation & expiry rules, `InsightsAnalyticsEngine` time-of-day buckets / 30D trend / calendar cell kinds, `AchievementEngine`, `ForestGrowthEngine`, and `ChainConfig.validated()`.
2. **`domain/FocusTimerEngineTest.kt`**
   - Verifies start, pause, resume, elapsed monotonic countdown, duplicate session reward suppression, reboot recovery (`Active` vs `InterruptedAfterReboot`), and aborted session zero rewards.
3. **`domain/UsageAnalyticsEngineTest.kt`**
   - Verifies self/system package exclusions, sub-minute `<1m` aggregation (`displayDuration` & exact millisecond sums), `SuccessZeroUsage` vs `Unavailable` distinction, and honest completed-day / 7-vs-7-day baseline calculations without partial-today inflation.
4. **`platform/usage/AndroidUsageStatsGatewayTest.kt`**
   - Exercises `AppOpsManager` permission checks (`DENIED` vs `GRANTED`), `UsageQueryResult.PermissionDenied`, `SuccessZeroUsage`, future-date rejection, and icon safety filtering.
5. **`data/PersistenceAndExportTest.kt`**
   - Exercises transactional `FocusRepository` + `ShopAndForestRepository` flows, `UsageRepository` 30s cache TTL & concurrent `Mutex` / `CompletableDeferred` coalescing, CSV exports, privacy-safe share formatter, DataStore preferences, and full data wipe.
6. **`data/RoomMigrationAndLedgerTest.kt`**
   - Tests Room `1 -> 2` SQLite migration (`FocusForestDatabase.MIGRATION_1_2`) preserving pre-existing v1 rows and validating Room v2 schema & `reward_ledger`, verifies zero break rewards in repository persistence, tests `ChallengeRepository` atomic reward crediting, custom challenge edit/delete/archive, and `MAX_REWARDED_CUSTOM_CHALLENGES_PER_DAY = 3` cap.
7. **`notifications/NotificationHelperTest.kt` & `TimerWorkerAndChainTest.kt`**
   - Tests quiet-hours overnight logic, `SessionCompletionCoordinator` (stale, active reschedule, reboot interrupted, idempotent completion), Pomodoro Chain lifecycle (`POMODORO -> SHORT_BREAK -> POMODORO -> complete`), `WeeklySummaryCoordinator` quiet-hours gating, and `AmbientAudioController` state transitions.
8. **`ui/FocusForestUiTest.kt`**
   - End-to-end Compose UI journey in Robolectric covering onboarding, single session start/pause/abort, Pomodoro Chain setup, ambient soundscape toggle, custom challenge validation & creation, 30D insights filter & calendar day selection, usage permission unavailable state, Botanical Shop `Owned` filter, dark theme switch, and strict local-first Privacy screen.

## Running Tests

```bash
gradle :app:testDebugUnitTest
```
