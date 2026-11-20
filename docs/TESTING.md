# Testing Strategy & Suites

Focus Forest uses local JVM unit tests and Robolectric tests (targeting SDK 36) without relying on emulators or ADB. Gradle, tests, and CI run on **JDK 21** while app bytecode targets Java 17.

## Test Suites (`app/src/test/java/com/azanahmed/focusforest/`)

1. **`domain/DomainEnginesTest.kt`**
   - Verifies `XpLevelEngine` rewards (focus blocks grant XP & seeds; `SHORT_BREAK` / `LONG_BREAK` strictly return `0 XP` and `0 seeds`).
   - Verifies monotonic XP levels, overflow-safe level calculation for extreme lifetime XP, `StreakCalculator` rest-day bridging & grace rules, deterministic daily/weekly challenges, `ChallengeEngine` deferred reward crediting, custom validation & expiry rules, `InsightsAnalyticsEngine` time-of-day buckets / 30D trend / calendar cell kinds, `AchievementEngine`, `ForestGrowthEngine`, and `ChainConfig.validated()`.
2. **`domain/FocusTimerEngineTest.kt`**
   - Verifies start, pause, resume, elapsed monotonic countdown, duplicate session reward suppression, reboot recovery (`Active` vs `InterruptedAfterReboot`), and aborted session zero rewards.
3. **`domain/UsageAnalyticsEngineTest.kt`**
   - Verifies self/system package exclusions, sub-minute `<1m` aggregation (`displayDuration` & exact millisecond sums), `SuccessZeroUsage` vs `Unavailable` distinction, and honest completed-day / 7-vs-7-day baseline calculations without partial-today inflation.
4. **`platform/usage/AndroidUsageStatsGatewayTest.kt`**
   - Exercises `AppOpsManager` permission checks (`DENIED` vs `GRANTED`), `UsageQueryResult.PermissionDenied`, `SuccessZeroUsage`, future-date rejection, and icon safety filtering.
5. **`data/PersistenceAndExportTest.kt`**
   - Exercises transactional `FocusRepository` + `ShopAndForestRepository` flows, `UsageRepository` 30s cache TTL & concurrent `Mutex` / `CompletableDeferred` coalescing, cancellation propagation without caching a phantom usage error, CSV exports, privacy-safe share formatter, DataStore preferences, and full data wipe.
6. **`data/RoomMigrationAndLedgerTest.kt`**
   - Tests Room `1 -> 2` SQLite migration (`FocusForestDatabase.MIGRATION_1_2`) preserving pre-existing v1 rows and validating Room v2 schema & `reward_ledger`, verifies zero break rewards in repository persistence, tests `ChallengeRepository` atomic reward crediting, custom challenge edit/delete/archive, and `MAX_REWARDED_CUSTOM_CHALLENGES_PER_DAY = 3` cap. Phase 0.3 does not change the schema, so the same migration test remains authoritative.
7. **`notifications/NotificationHelperTest.kt` & `TimerWorkerAndChainTest.kt`**
   - Tests quiet-hours overnight logic, `SessionCompletionCoordinator` (stale, active reschedule, reboot interrupted, idempotent completion), Pomodoro Chain lifecycle (`POMODORO -> SHORT_BREAK -> POMODORO -> complete`), auto-chain recovery after process death (`pendingStepSessionId` + `lastAdvancedSessionId`, exactly-once continuation), worker-only chain completion while no Activity exists, manual chains waiting for the user, reboot pausing an auto chain, final-focus chain completion, `WeeklySummaryCoordinator` quiet-hours gating, and `AmbientAudioController` state transitions.
   - Phase 0.3 adds adversarial cases: duplicate completion never clears a newer chain timer; chain recovery refuses an unrelated standalone timer; a stale interruption cannot clear a newer chain step; and challenge history exposes expired daily challenges without duplicating today's list.
8. **`ui/FocusForestUiTest.kt`**
   - End-to-end Compose UI journey in Robolectric covering onboarding, single session start/pause/abort, spoken countdown semantics while running and paused, Pomodoro Chain setup, ambient soundscape toggle, custom challenge validation & creation, 30D insights filter & calendar day selection, usage permission unavailable state, Botanical Shop `Owned` filter, dark theme switch, and strict local-first Privacy screen.

## Running Tests

Use JDK 21 and the committed Gradle 9.3.1 wrapper:

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew :app:testDebugUnitTest --no-daemon -Djava.awt.headless=true
JAVA_HOME=/opt/jdk-21 ./gradlew :app:lintDebug --no-daemon -Djava.awt.headless=true
JAVA_HOME=/opt/jdk-21 ./gradlew :app:assembleDebug --no-daemon -Djava.awt.headless=true
```

The first Robolectric run downloads the 213 MB `android-all-instrumented` API 36 jar into the local Maven cache (`~/.m2/repository`); it is cached for later runs.

Phase 0.3 local result: **43 tests executed, 43 passed, 0 failed, 0 skipped** on JDK 21.0.8, Gradle 9.3.1, Robolectric 4.16.1 (`@Config(sdk = [36])`). This includes `FocusViewModelCompletionTest`, which advances a fake clock and verifies the foreground ticker completes a session without cancelling its own save, and the new adversarial timer/chain/cancellation/challenge-history cases. See [PHASE_0_3_REPORT.md](PHASE_0_3_REPORT.md) for the exact suite table and verification limits.
