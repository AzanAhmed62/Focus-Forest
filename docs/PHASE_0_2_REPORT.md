# Focus Forest — Phase 0.2 Build Recovery & Device QA Report

## 1. Executive Summary

Phase 0.2 recovered the local build/verification loop that had failed in CI and hardened the two riskiest runtime areas without adding product features:

- **Build recovery**: added a genuine Gradle 9.3.1 wrapper with the official distribution SHA-256, confirmed JDK 21 execution, modernized GitHub Actions, and fixed the JDK 17 test failure that broke the previous CI run.
- **Namespace migration**: moved all source sets and the Room schema export from `com.example` to `com.azanahmed.focusforest`. `applicationId` remains unchanged.
- **Pomodoro Chain recovery**: chain progression is now a pure state machine with exactly-once advancement (`lastAdvancedSessionId`), an atomic chain + next-timer write (`pendingStepSessionId`), cold-start/worker reconciliation, and a regression test for a crash between reward credit and chain continuation.
- **Ambient audio hardening**: audio focus request/loss handling, becoming-noisy pause, foreground-only lifecycle, guarded `AudioTrack` state/writes, single synthesis loop via mutex, and release in `finally`.
- **Privacy verification**: no `INTERNET` permission; WorkManager's library-injected `ACCESS_NETWORK_STATE` is now explicitly removed; full-wipe behavior verified on a real device.
- **Verification**: **37/37 JVM + Robolectric tests pass on JDK 21** with Robolectric `sdk = [36]`; lint, debug APK, and real-device QA were run locally. This report distinguishes what was actually verified from what remains blocked.

## 2. Starting Point and Branch

- **Starting commit**: `a3808426d8f8d2dbc5786fe2a44bf4d695f5a675` (`main`, in sync with `origin/main`).
- **Working branch**: `phase-0.2/build-recovery-and-device-qa`.
- No push, force-push, or commit to `main` was performed.
- No Supabase, auth, multiplayer, subscriptions, ads, signing keys, or new product features were added.

## 3. Environment Used

| Item | Value |
| :--- | :--- |
| OS | Linux (local workspace) |
| Test/CI JDK | Eclipse Temurin OpenJDK **21.0.8** |
| Gradle | **9.3.1** via committed wrapper |
| Android Gradle Plugin | **9.1.1** |
| Kotlin | **2.2.10** |
| compileSdk / targetSdk | **36** (compile minor API 1) |
| minSdk | **26** |
| Java bytecode target | **17** |
| Room | **2.7.0**, DB version **2**, `MIGRATION_1_2` preserved |
| Robolectric | **4.16.1**, six test classes use `@Config(sdk = [36])` |
| Device used for QA | OnePlus 6T (`ONEPLUS A6010`), Android **11 / API 30**, not rooted, debug build |

## 4. Build and CI Recovery

### 4.1 Genuine Gradle wrapper

- Generated `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle-wrapper.properties` using the installed Gradle distribution (Gradle-supported `wrapper` task), targeting Gradle 9.3.1.
- Added the official distribution checksum:

  ```
  distributionSha256Sum=b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06
  ```

- `./gradlew --version` reports Gradle 9.3.1 and Launcher JVM 21.0.8.
- `gradle/wrapper/gradle-wrapper.properties` also keeps `validateDistributionUrl=true`.

### 4.2 CI workflow

`.github/workflows/android-ci.yml` now uses:

- `actions/checkout@v7`
- `actions/setup-java@v6` with Temurin JDK 21
- `gradle/actions/setup-gradle@v6.4.1` with `validate-wrappers: true`
- `permissions: contents: read`
- wrapper-only execution (`./gradlew`), never a system `gradle`
- test, lint, and `assembleDebug` steps
- `actions/upload-artifact@v7` for test/lint/APK reports
- an ephemeral debug keystore generated with `keytool` in the runner (ignored locally, never committed) because the AI Studio debug signing config points at `rootDir/debug.keystore`

The original CI failure was confirmed through the GitHub API on run `44028622041`: step **“Run JVM and Robolectric unit tests”** failed on **JDK 17**, so lint and assemble never ran. The workflow now targets JDK 21 as required by API 36 Robolectric.

> CI itself has **not** been re-run on GitHub in this phase. The workflow file was validated locally (YAML parse + command review), but no push was requested or performed.

## 5. Namespace Migration

- `namespace` changed from `com.example` to `com.azanahmed.focusforest` in `app/build.gradle.kts`.
- Source sets moved with `git mv`:
  - `app/src/main/java/com/example/` → `app/src/main/java/com/azanahmed/focusforest/`
  - `app/src/test/java/com/example/` → `app/src/test/java/com/azanahmed/focusforest/`
  - `app/src/androidTest/java/com/example/` → `app/src/androidTest/java/com/azanahmed/focusforest/`
  - `app/schemas/com.example.data.local.db.FocusForestDatabase/` → `app/schemas/com.azanahmed.focusforest.data.local.db.FocusForestDatabase/`
- All package/import references updated; `applicationId` remains exactly `com.azanahmed.focusforest`.
- The exported Room schema stays at version 2 and the `1 -> 2` migration test still passes.

## 6. Pomodoro Chain Reliability

### Problem

Chain progression was previously split across UI and worker code. A process death between “session rewards committed” and “next chain step persisted” could leave a chain unadvanced, double-advance a step, or start a duplicate timer.

### Implementation

- Added pure `ChainStateMachine`:
  - focus completion → increment focus count, choose short/long break, complete the chain at the configured count;
  - break completion → increment break count and return to Pomodoro;
  - interruption → keep the chain alive but require an explicit user step.
- `ActiveChainState` now carries:
  - `pendingStepSessionId`: the session id that the persisted chain is currently running;
  - `lastAdvancedSessionId`: exactly-once guard for chain advancement.
- `UserPreferencesDataStore.saveChainAndTimer(chain, timer)` writes both keys in one DataStore transaction, so a crash cannot persist one without the other.
- `FocusRepository`:
  - serializes completion/continuation with a `Mutex`;
  - `continueChainAfterCompletion()` is the single production advancement path shared by ViewModel and Worker;
  - `recoverChainState()` reconciles chain + timer + completed/aborted session after process death, worker-only completion, or reboot;
  - duplicate completion still clears the stale timer and drives the idempotent continuation (tested).
- `SessionCompletionCoordinator` now recovers the chain even when rewards were already credited (UI crash before continuation) instead of returning a stale-worker result.
- `FocusViewModel` re-arms `SessionCompletionWorker` when recovering a running timer, covering force-stop/OEM job cancellation scenarios.
- **Session completion self-cancellation fixed**: the ticker coroutine calls `completeSession()` when time elapses, and `completeSession()` previously cancelled `tickerJob` before saving. That cancelled the very coroutine performing the save and surfaced “Session finished, but saving failed (StandaloneCoroutine was cancelled)”. Completion now cancels the ticker only when invoked from a different coroutine, and coroutine cancellation is no longer converted into a storage error.

### Regression coverage

`TimerWorkerAndChainTest` now has **8 tests**, including:

- auto chain recovery after process death exactly once;
- worker completes focus and break steps with no Activity/ViewModel alive;
- manual chain waits for the user;
- reboot pauses an auto chain instead of auto-starting;
- final focus completes the chain without another timer;
- crash between reward credit and chain continuation is recovered by the worker without duplicate XP.

## 7. Ambient Audio Hardening

`AndroidAmbientAudioController` now:

- requests audio focus for the playback session and handles `GAIN`, `LOSS`, `LOSS_TRANSIENT`, and `LOSS_TRANSIENT_CAN_DUCK`;
- pauses on `ACTION_AUDIO_BECOMING_NOISY` (headphone unplug);
- starts only while the app is foreground and pauses on `ON_STOP`;
- runs a single synthesis loop guarded by a `Mutex` and playback generation counter;
- checks `AudioTrack` minimum buffer, initialization state, play state, and negative write results;
- releases every track in `finally` and marks `UNAVAILABLE` only for the current generation.

Device QA confirmed the controller requests audio focus and an active `AudioTrack` appears in `dumpsys media.audio_flinger`; audibility/quality itself was not measurable from the automation environment.

## 8. Tests Executed and Actual Results

Command (JDK 21, wrapper only):

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew :app:testDebugUnitTest
```

Result: **BUILD SUCCESSFUL — 37 tests executed, 37 passed, 0 failed, 0 skipped.**

| Suite | Tests | Result |
| :--- | ---: | :--- |
| `DomainEnginesTest` | 10 | passed |
| `TimerWorkerAndChainTest` | 8 | passed |
| `PersistenceAndExportTest` | 3 | passed |
| `RoomMigrationAndLedgerTest` | 3 | passed |
| `FocusTimerEngineTest` | 3 | passed |
| `UsageAnalyticsEngineTest` | 3 | passed |
| `AndroidUsageStatsGatewayTest` | 2 | passed |
| `NotificationHelperTest` | 1 | passed |
| `FocusViewModelCompletionTest` (Robolectric `sdk = [36]`) | 1 | passed |
| `FocusForestUiTest` (Robolectric `sdk = [36]`) | 1 | passed |
| `ExampleUnitTest` + `ExampleRobolectricTest` | 2 | passed |

Notes:

- Robolectric initially stalled downloading the 213 MB API 36 `android-all-instrumented` jar. The jar was retrieved in parallel chunks, **SHA-512 verified** against the published checksum, and seeded into `~/.m2/repository`; subsequent runs use the cache.
- `FocusViewModelCompletionTest` starts a 5-minute break, advances the fake clock, and lets the real ticker coroutine complete it. It asserts a non-null completion outcome, no error message, a cleared active timer, and one persisted `COMPLETED` row. This directly guards the ticker self-cancellation bug in virtual time without a five-minute device wait.
- One UI assertion was genuinely below the fold after creating a custom challenge. The test now scrolls the Challenges list (tag `challenges_lazy_column`) before asserting visibility; the assertion strength was not reduced.

## 9. Lint and APK

- Lint: **BUILD SUCCESSFUL** — `:app:lintDebug` completed in **7m** on JDK 21. Report summary: **0 errors, 66 warnings**. Warnings are informational: newer dependency/AGP/Gradle versions available, unused resources, launcher-icon shape/location guidance, one redundant activity label, and one obsolete SDK check. No correctness, security, or manifest errors.
- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL**.
- APK: `app/build/outputs/apk/debug/app-debug.apk`, ~21.2 MB.
- APK metadata verified with `aapt2`:
  - package `com.azanahmed.focusforest`, versionCode 1, versionName 1.0.0
  - targetSdk 36, compileSdk 36
  - debug build
  - permissions: `PACKAGE_USAGE_STATS`, `POST_NOTIFICATIONS`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, AndroidX dynamic-receiver permission
  - **no `INTERNET`, no `ACCESS_NETWORK_STATE`**
- APK SHA-256 (final rebuilt artifact containing the completion fix): `8de349762584c05f730dfe8ae69acd8cb920d59293d2af0f444334413564aecf`.

## 10. Real-Device QA (OnePlus 6T, Android 11 / API 30)

The only available device was a physical OnePlus 6T on **API 30**. No API 36 emulator/device was available: the SDK has API 36 platforms, but only API 28/30 system images and no AVDs. The following was verified on-device with the debug APK:

| Check | Result |
| :--- | :--- |
| Install and cold launch | Passed (`LaunchState: COLD`, MainActivity focused) |
| Four-page onboarding (skip usage, default goal/intention) | Passed |
| Usage Access disclosure and Android Settings handoff | Passed |
| Granted `PACKAGE_USAGE_STATS` query | Passed — Today showed **423 min observed today**, top apps listed locally |
| Single Pomodoro start | Passed — 25:00 countdown, “Growing Scots Pine” |
| Pause / resume | Passed — countdown frozen while paused, resumed cleanly |
| WorkManager completion job | Passed — one `SystemJobService` job with ~25 min latency |
| Process death (single session) | Passed — `am force-stop` then relaunch recovered the running timer |
| Ambient soundscape preview | Passed — audio focus granted, active `AudioTrack` in `media.audio_flinger`, UI showed pause control |
| Ambient auto-play with focus session | Passed — “Playing Soft Rain (100% offline DSP)” |
| Pomodoro Chain start | Passed — “Round 1 of 4”, current/next step shown |
| Process death (chain) | Passed — relaunch restored “Round 1 of 4”, timer, and completion job |
| End-session confirmation / abort | Passed — interrupted session saved with no completion rewards, job cancelled |
| Full local wipe | Passed — returned to onboarding, **0 WorkManager jobs**, **0 `AudioTrack`s**, usage/forest state cleared |
| Worker-only completion after process kill | Passed — two 5m breaks saved as `COMPLETED` by `SessionCompletionWorker` while no Activity/process existed |
| Completion notification | Passed — “Short Break complete / Restorative 5m break finished.” posted at importance 3 after opting in and disabling default quiet hours |
| Break reward integrity | Passed — every completed 5m break stored `0 XP`, `0 seeds`, `rewardGranted=0`, and zero forest trees |
| Foreground ticker completion | Verified by `FocusViewModelCompletionTest` (Robolectric, virtual clock), not by a device run: another app repeatedly took the foreground during the 5-minute window |

Limitations of this device QA:

- API 30, not API 36: Android 12+ notification runtime permission behavior, exact API 36 background restrictions, and API 36 foreground-service rules are not exercised.
- Audio was verified as an active focus-owning `AudioTrack`, not by listening to speaker/headphone output.
- Doze/standby overnight completion, OEM battery killers, reboot rescheduling over a real reboot, and CSV share-sheet/TalkBack checks remain unverified.
- Notifications are opt-in and default quiet hours are 22:00–07:00. An initial completion check produced no notification because notifications were off; a second check was suppressed by quiet hours, as designed. Delivery was only claimed after enabling notifications and disabling quiet hours.
- WorkManager/JobScheduler ran the completion job roughly 30 seconds before the nominal 5-minute mark on this OxygenOS build; the app’s own elapsed-time evaluation and persisted session were correct.

## 11. Privacy and Integrity Verification

- **No network permission** in the final APK. Source has no `HttpURLConnection`, OkHttp, Retrofit, or socket usage.
- **`ACCESS_NETWORK_STATE` corrected**: the first APK inspection showed WorkManager's library manifest injected this normal permission, contradicting the previous documentation. Because Focus Forest schedules no network-constrained work, it is now removed with `tools:node="remove"`; the rebuilt APK contains neither `INTERNET` nor `ACCESS_NETWORK_STATE`.
- `allowBackup="false"`, cloud-backup and device-transfer exclusions, and a non-exported `FileProvider` are intact.
- Reward integrity remains enforced by `RewardAuthority` + `reward_ledger`; breaks grant 0 XP/0 seeds and plant no tree. The new worker recovery path only replays idempotent chain continuation and never re-credits rewards.
- Full wipe on-device cancels WorkManager jobs, stops audio, clears notifications/CSV/Room/DataStore (verified by the return to onboarding and zero jobs/tracks).

## 12. Files Changed (High Level)

New:

- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- `app/src/main/java/com/azanahmed/focusforest/domain/engine/ChainStateMachine.kt`
- `docs/PHASE_0_2_REPORT.md`

Key modifications:

- `.github/workflows/android-ci.yml`, `gradle/wrapper/gradle-wrapper.properties`, `app/build.gradle.kts`
- all source/test packages moved to `com/azanahmed/focusforest/`
- `app/src/main/AndroidManifest.xml` (explicit `ACCESS_NETWORK_STATE` removal)
- `FocusRepository.kt`, `FocusModels.kt`, `UserPreferencesDataStore.kt`, `FocusWorkers.kt`, `FocusViewModel.kt`
- `AmbientSoundEngine.kt`, `AppContainer.kt`
- `TimerWorkerAndChainTest.kt`, `FocusViewModelCompletionTest.kt`, `FocusForestUiTest.kt`, `ChallengesScreen.kt`
- `README.md`, `docs/ARCHITECTURE.md`, `docs/FEATURE_STATUS.md`, `docs/PRIVACY_AND_DATA_FLOW.md`, `docs/TESTING.md`

## 13. Commits

On `phase-0.2/build-recovery-and-device-qa`:

- `bf9eac9` — build: add genuine Gradle 9.3.1 wrapper with checksum
- `e9f15e8` — ci: run Android checks on JDK 21 with wrapper validation
- `2930586` — fix: migrate namespace and harden chain, audio, and completion recovery

This report and the supporting documentation updates are committed as the branch tip after those three commits. No push, force-push, or commit to `main` was performed.

## 14. Final Verification Status

- Tests: **37/37 passed** (JDK 21, API 36 Robolectric), including the ticker self-cancellation regression test.
- Lint: **0 errors, 66 informational warnings**.
- Debug APK: built; SHA-256 `8de349762584c05f730dfe8ae69acd8cb920d59293d2af0f444334413564aecf`.
- CI: updated but not re-run on GitHub (no push).
- API 36 device/emulator QA: **blocked** — no API 36 AVD/system image or device available. API 30 physical QA completed as listed above.
- Foreground ticker completion: verified by the Robolectric regression test; the physical-device attempt was interrupted by another app taking the foreground.
- Overnight Doze, reboot, OEM usage-stat quirks, audio audibility, and CSV/TalkBack checks: **not verified**.

## 15. Remaining Work / Recommended Next Steps

1. Re-run the GitHub Actions workflow on a pushed branch and confirm the JDK 21 test/lint/assemble artifact flow.
2. Provision an API 36 emulator or device and repeat the device matrix, especially notification runtime permission, foreground-service behavior, and Doze completion.
3. Manually listen to all five soundscapes with speaker and headphones, and test audio-focus interruptions during a live session.
4. Reboot a physical device with an active chain to confirm WorkManager rescheduling, then test usage queries across midnight/timezone changes and OEM battery savers.
5. Run a TalkBack/font-scale pass over onboarding, focus, insights, and privacy screens.
