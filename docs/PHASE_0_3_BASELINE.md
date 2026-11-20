# Phase 0.3 Baseline — Product Hardening and Feature Expansion

Status: baseline audit complete; hardening and product work implemented (see `PHASE_0_3_REPORT.md` for results).
Branch: `phase-0.3/product-hardening-and-feature-expansion`
Base commit: `17377a9` (`docs: record Phase 0.2 build recovery and device QA results` on `phase-0.2/build-recovery-and-device-qa`)

This document records the state of Focus Forest at the start of Phase 0.3. It is the
reference point for every change in this phase. It contains only findings that were
verified by inspecting the repository, running the test suite, or building the app.

## 1. Product and platform constraints

Focus Forest is a strictly offline, local-first Android digital wellness app.

- Application ID and namespace: `com.azanahmed.focusforest` (unchanged).
- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36 (minor API level 1)`.
- Kotlin `2.2.10`, AGP `9.1.1`, Gradle wrapper `9.3.1`, Java 17 bytecode, JDK 21 toolchain.
- No `INTERNET` permission. `ACCESS_NETWORK_STATE` is explicitly removed from the merged
  manifest because the app schedules no network-constrained work.
- `PACKAGE_USAGE_STATS` is the only special access permission; it is opt-in and revocable.
- `POST_NOTIFICATIONS` is requested at runtime on API 33+.
- `android:allowBackup="false"`; the FileProvider used for CSV sharing is not exported.
- No accounts, auth, sync, telemetry, ads, billing, leaderboards, social, AI, or remote backend.
- Room database is the durable source of truth; DataStore holds preferences and active
  timer/chain checkpoints.

## 2. Repository and build baseline

- Working tree at audit start: clean.
- Test suite: 37 JVM/Robolectric tests, all passing in Phase 0.2.
- Debug APK from Phase 0.2:
  `8de349762584c05f730dfe8ae69acd8cb920d59293d2af0f444334413564aecf`.
- CI workflow triggers on `main`, `phase-0.2/**`, and PRs to `main` only. It does not yet
  trigger on `phase-0.3/**`.
- Release signing is configured to read `KEYSTORE_PATH`, `STORE_PASSWORD`, and
  `KEY_PASSWORD`; no production signing material is present in the repository.
- Dependencies are intentionally conservative and have not been upgraded blind. The
  Compose BOM (`2024.09.00`), lifecycle (`2.8.7`), navigation (`2.8.9`), Room (`2.7.0`),
  and WorkManager (`2.10.0`) remain on versions compatible with the current toolchain.
  Upgrading Compose to the 1.13 line / Material 3 1.5 line would require Kotlin 2.2+ and
  compileSdk 37.1-era tooling and is out of scope for this phase.

## 3. Architecture map

```
app/src/main/java/com/azanahmed/focusforest/
  core/
    di/AppContainer.kt              manual dependency container
    time/ClockProvider.kt           wall clock + elapsedRealtime + boot count
    monetization/                   local free entitlement abstraction
    util/CsvExporter.kt             CSV building and FileProvider sharing
    util/ShareFormatter.kt          text share summaries
  data/
    local/datastore/                preferences, timer/chain checkpoints
    local/db/                       Room v2, entities, DAOs, migration
    repository/                     Focus, Challenge, Usage, Shop/Forest, DataManagement
  domain/
    engine/                         pure timer, chain, challenge, streak, XP, analytics
    model/                          immutable domain models
  features/
    onboarding, home, focus, challenges, insights, forest, settings, shop, privacy
  notifications/                    WorkManager workers and notification channels
  platform/
    audio/                          procedural PCM ambient sound engine
    usage/                          Android UsageStatsManager gateway
  navigation/                       type-safe Compose navigation
  ui/                               theme and reusable components
```

Key invariants:

- `ClockProvider` is injected so timer behavior is deterministic in tests.
- Timer state stores wall clock, elapsed-realtime, and boot count. Recovery distinguishes
  an actively running timer, a timer ready to complete, and a timer interrupted by reboot.
- Timer + chain checkpoints are written through DataStore, not Room, because they change
  frequently and must be recoverable before the database opens.
- Rewards are protected by a unique `reward_ledger.rewardKey` (`session:<id>`,
  `challenge:<id>`, `achievement:<name>`, `streak_milestone:<days>`) and awarded inside
  Room transactions.
- Session completion is idempotent: a foreground completion, a WorkManager completion, and
  a cold-start recovery may all evaluate the same session, but only one credit is written.
- `ChainStateMachine` is side-effect free so UI, worker, and recovery paths share one
  transition rule set.

## 4. Persistence baseline

### Room database (version 2)

Tables: `user_profile`, `focus_sessions`, `daily_stats`, `challenges`,
`challenge_progress`, `achievements`, `forest_progress`, `forest_trees`, `shop_items`,
`owned_items`, `app_usage_snapshots`, `app_usage_items`, `reward_ledger`.

- `MIGRATION_1_2` adds `focus_sessions.rewardGranted` and `challenges.isArchived` and
  creates `reward_ledger`. Migration is covered by `RoomMigrationAndLedgerTest`.
- Exported schema: `app/schemas/com.azanahmed.focusforest.data.local.db.FocusForestDatabase/2.json`.
- `clearAllUserData()` clears user-owned tables. `shop_items` is a static catalog and is
  deliberately retained because it contains no user data; owned items, rewards, sessions,
  progress, usage snapshots, and preferences are all removed.

### DataStore

`UserPreferencesDataStore` stores onboarding/theme/goal, usage consent and monitored apps,
notification preferences, ambient sound preferences, the active timer checkpoint, and the
active chain checkpoint. `saveChainAndTimer` writes both in one transaction.

## 5. Systems verified in the audit

### Timer and completion

- `FocusTimerEngine` validates planned minutes, pause/resume, elapsed-time progress, reboot
  detection, and session construction.
- `FocusViewModel` ticks once per second, persists state on pause/resume, and schedules a
  unique `SessionCompletionWorker` with `REPLACE`.
- `SessionCompletionCoordinator` handles active timers, reboot interruption, ready-to-complete
  timers, duplicate completions, and worker-only chain continuation.
- Tests cover timer boundaries, paused timers, reboot interruption, worker completion,
  duplicate worker completion, chain continuation, and manual chain waiting.

### Chains

- Chain transitions are serialized with a mutex in `FocusRepository`.
- Auto-transition persists the next timer atomically with the advanced chain.
- Manual transition persists the advanced chain with a waiting flag and no timer.
- `recoverChainState()` reconciles a persisted chain with a persisted timer, a completed
  pending session, or a missing timer after process death.

### Rewards, challenges, achievements, streaks

- Session XP/seeds, streak milestones, challenge rewards, and achievement bonuses all pass
  through `RewardAuthority` and the reward ledger.
- Break sessions grant no direct XP/seeds by design, but can satisfy planned-break challenges.
- Challenge evaluation persists progress for active and expired challenges and credits
  rewards only for newly completed ones.
- Daily and weekly challenge generation is deterministic and includes usage-based
  challenges only when a valid usage baseline exists.

### Usage insights

- Consent is explicit; disabling usage tracking deletes stored snapshots and items.
- Queries are limited to relevant user apps, exclude system/launcher/self packages, and
  store at most 15 days of snapshots plus top-app detail.
- Refresh coalesces concurrent callers via `CompletableDeferred` and a 30-second cache.

### Audio

- Ambient sound is synthesized locally as PCM through `AudioTrack`; no assets or network.
- Audio focus loss pauses playback; backgrounding pauses playback.
- Synthesis jobs are generation-guarded so stale loops cannot resurrect playback.

### Navigation, export, deletion, privacy

- Type-safe serializable routes with a four-tab bottom bar plus stack destinations.
- CSV export writes to the app cache and shares through a non-exported FileProvider.
- Settings offers full local-data deletion and returns the app to onboarding.
- Privacy screen describes local-only behavior and Android usage access.

## 6. Defects and hardening targets identified

These are the concrete issues Phase 0.3 must address. They were confirmed by reading the
relevant code paths, not inferred from documentation.

### Cancellation and reliability

1. `UsageRepository.refreshUsageData()` catches `Throwable`, which converts coroutine
   cancellation into a completed fallback state and can deliver a bogus result to joiners.
2. `SessionCompletionWorker.doWork()` catches `Throwable`, converting cancellation into a
   WorkManager retry/failure instead of propagating cancellation.
3. `AndroidUsageStatsGateway.queryDailyForegroundUsageResult()` catches `Throwable` around
   the platform query; cancellation should never be wrapped as a usage failure.
4. `AmbientSoundEngine.runSynthesisLoop()` catches `Throwable` and checks `isActive`; the
   release path is correct, but cancellation should be rethrown explicitly after cleanup.

### Timer and chain integrity

5. `FocusViewModel.startSession()` and `startNextChainStep()` guard only on the UI
   `activeTimer`, which is set inside a launched coroutine. Two rapid taps can both pass the
   guard and create two sessions.
6. `retryPendingCompletion()` can complete a stale failed session while another timer is
   active if state is updated concurrently.
7. `FocusRepository.recordCompletedSession()` and `recordAbortedSession()` clear the active
   timer unconditionally. A duplicate worker completion that runs after the UI already
   advanced a chain can therefore delete the next chain timer.
8. `SessionCompletionCoordinator` schedules a recovered chain timer for its full planned
   duration instead of evaluating how much time has already elapsed.
9. `FocusRepository.recoverChainState()` can return a timer whose `chainId` does not match
   the active chain, exposing an unrelated running timer to chain recovery.

### State refresh and challenge behavior

10. `ChallengesViewModel.init` reads `usageRepository.dashboardState.value` before any usage
    refresh, so usage-based challenge progress can be evaluated against an empty baseline.
11. Deleting usage data does not immediately re-evaluate challenge progress, leaving
    usage-derived progress stale until another refresh happens.
12. Completed and expired challenges disappear from the challenge screen once their period
    ends; there is no local history of past challenge outcomes.
13. `FocusScreen` syncs once on `ON_START` and again on `ON_RESUME`, duplicating recovery
    work on every foreground transition.

### Documentation and process

14. CI does not run on `phase-0.3/**`.
15. The Phase 0.3 baseline, report, and future-backend-boundary documents do not exist yet.
16. README and existing docs need to reflect the new hardening guarantees and test counts.

## 7. Deliberately deferred items

- No cloud sync, auth, backend, social, leaderboards, ads, telemetry, billing, or AI.
- No Compose/Material major upgrade that would force Kotlin 2.2+ and compileSdk 37.1-era
  tooling. Dependency upgrades must remain controlled and verified.
- No destructive Room migration. Any schema change requires a version bump, a real
  migration, exported schema, and a migration test.
- No claim of physical-device or TalkBack verification unless it is actually performed.
  API 36 emulator images are not available in this environment; physical QA is on
  Android 11 (API 30) hardware only. JVM/Robolectric tests cover logic, not hardware
  behavior.

## 8. Phase 0.3 work plan

1. Fix cancellation swallowing in usage refresh, workers, the usage gateway, and audio.
2. Harden timer start/retry/abort/completion paths with synchronous guards and conditional
   timer clearing.
3. Make chain recovery evaluate persisted timers rather than restarting them blindly, and
   refuse mismatched chain/timer pairs.
4. Refresh usage data before challenge evaluation and re-evaluate after usage deletion.
5. Add a local challenge history surface for completed and expired challenges.
6. Add a weekly focus target summary derived from the existing daily goal.
7. Add adversarial regression tests for each fix, including duplicate completion, stale
   worker, conditional clear, cancellation, chain mismatch, and challenge history.
8. Update documentation, CI triggers, and the Phase 0.3 report.
9. Run clean build, full tests, lint, and debug assembly; record exact commands and hashes.
