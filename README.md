# Focus Forest

**Grow your focus. Reclaim your time.**

Focus Forest is a strictly offline, local-first native Android digital wellness application built with Kotlin, Jetpack Compose, Material 3, Room, DataStore Preferences, and WorkManager.

## Core Capabilities

- **Monotonic Focus Timer & Pomodoro Chain Mode**: Single focus blocks (`Pomodoro`, `Deep Work`, `Custom Focus`) and intentional rests (`Short Break`, `Long Break`), plus structured multi-round Pomodoro Chains with configurable focus/break durations, long-break cadence, and auto-vs-manual step transitions. Timer state persists across backgrounding, process death, and device reboots via `ClockProvider` monotonic elapsed realtime checks. Phase 0.3 adds a synchronous start guard, conditional timer clearing, remaining-time recovery for persisted chain steps, and cancellation-safe completion workers.
- **Offline Ambient Soundscapes**: Pure procedural PCM DSP audio synthesis (`AndroidAmbientAudioController` backed by `AudioTrack`) for Soft Rain, Forest Wind, Ocean Waves, Hearth Ember, and Deep Focus Hum—zero audio assets downloaded or streamed.
- **Optional Android Usage Insights**: Explicit, plain-language consent flow (`PACKAGE_USAGE_STATS`) and least-privilege `<queries>` launcher discovery (no `QUERY_ALL_PACKAGES`), exact millisecond aggregation including sub-minute apps (`<1m`), distinct `COMPLETE` / `PARTIAL_TODAY` / `ZERO_USAGE` / `UNAVAILABLE` states, daily usage budget tracking, and honest completed-day and 7-day-vs-7-day comparisons (partial today is never compared against finished days).
- **Reusable Routines & Session Context**: Named focus templates reuse the existing timer/chain engines and start in one action from Today, Focus, or the Routines screen. Optional categories, intentions, notes, and reflections are stored locally; completed sessions keep routine/category snapshots so editing or deleting a template never rewrites history. Notes and reflections never affect XP, seeds, trees, achievements, or challenges.
- **Filterable History & Category Insights**: A dedicated bounded history screen with date-range, category, routine, status, and text filters plus a metadata-only reflection editor. Insights adds category/routine breakdowns and average completed-block duration scoped to the selected 7D/30D/90D window with honest sample counts.
- **Independent Weekly Goal**: The daily goal is preserved, and the weekly target is now a separate local preference initialized once from the previous effective intention (`daily × 7`). Set it to 0 to disable the weekly target; later daily-goal edits do not rewrite it.
- **Challenges, Achievements, Botanical Shop & Virtual Forest**: Transactional `RewardAuthority` with `RewardLedgerEntity` deduplication across sessions, deterministic daily/weekly challenges, user-defined custom challenges (with validation, editing, deletion/archiving, and anti-farming daily caps), local challenge history for completed and expired goals, milestone achievements, Leaf Points, and interactive Canvas botanical forest scenes.
- **Local Privacy & Data Portability**: Zero `INTERNET` permission, `allowBackup="false"`, excluded cloud backup & device transfer rules, privacy-safe share formatting, CSV exports via `FileProvider`, and complete one-tap local reset (`DataManagementRepository.deleteAllLocalData`). Routines, categories, history, notes, reflections, and the weekly goal are stored only in local Room/DataStore storage and are cleared by the same wipe.

## Project Configuration

- **Application ID**: `com.azanahmed.focusforest`
- **Build Namespace**: `com.azanahmed.focusforest` (source packages live under `com/azanahmed/focusforest/`)
- **Min / Compile / Target SDK**: API 26 / API 36 / API 36
- **Java / Kotlin Toolchain**: Java 17 bytecode target, JDK 21 for Gradle/tests/CI, Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, Room 2.7.0 (KSP schema export)

## Build & Verification

The repository ships a genuine Gradle wrapper (Gradle 9.3.1 with the official distribution checksum). Use JDK 21:

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew :app:testDebugUnitTest --no-daemon -Djava.awt.headless=true
JAVA_HOME=/opt/jdk-21 ./gradlew :app:lintDebug --no-daemon -Djava.awt.headless=true
JAVA_HOME=/opt/jdk-21 ./gradlew :app:assembleDebug --no-daemon -Djava.awt.headless=true
```

Phase 0.4 local verification: **51/51 JVM + Robolectric tests passed**, lint completed with **0 errors**, and a debug APK was assembled. See the [Phase 0.4 report](docs/PHASE_0_4_REPORT.md) for exact results, migration coverage, and the APK SHA-256. CI is configured for `main`, `phase-0.2/**`, `phase-0.3/**`, and `phase-0.4/**`, but was not pushed or re-run remotely in this phase.

## Privacy & Optional Usage Access

- Android `PACKAGE_USAGE_STATS` permission is completely optional; timers, Pomodoro Chains, routines, ambient audio, challenges, history, and forest growth all work without granting it.
- Focus Forest declares zero `INTERNET` permission, `allowBackup="false"`, and least-privilege launcher discovery (`<intent>` filter without `QUERY_ALL_PACKAGES`).
- All records stay in local Room and DataStore storage and can be exported to CSV or permanently erased in Settings.

## Known Limitations & Roadmap

- Physical API 36 device/emulator QA is still unavailable in this environment. Phase 0.2 device QA was performed on a OnePlus 6T running Android 11 / API 30; Phase 0.3 and Phase 0.4 logic is covered by JVM/Robolectric tests, not by a new physical run.
- TalkBack behavior is not claimed as verified. The focus countdown exposes an explicit spoken duration and new controls carry labels, but a full TalkBack/font-scale pass remains a device task.
- On-device migration from a real Phase 0.3 install, Doze/reboot recovery, and CSV share-sheet behavior remain deferred device checks.
- Cloud sync, social challenge rooms, and Google Play Billing entitlements are intentionally deferred to a future server-backed phase. Any such work must first satisfy [Future Backend Boundaries](docs/FUTURE_BACKEND_BOUNDARIES.md); the current app remains local-only.

See the detailed project documentation:
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy & Data Flow](docs/PRIVACY_AND_DATA_FLOW.md)
- [Feature Status](docs/FEATURE_STATUS.md)
- [Testing](docs/TESTING.md)
- [Phase 0.1 Engineering Report](docs/PHASE_0_1_REPORT.md)
- [Phase 0.2 Build Recovery & Device QA Report](docs/PHASE_0_2_REPORT.md)
- [Phase 0.3 Baseline](docs/PHASE_0_3_BASELINE.md)
- [Phase 0.3 Hardening & Feature Report](docs/PHASE_0_3_REPORT.md)
- [Phase 0.4 Baseline](docs/PHASE_0_4_BASELINE.md)
- [Phase 0.4 Focus Workflows & Personalization Report](docs/PHASE_0_4_REPORT.md)
- [Future Backend Boundaries](docs/FUTURE_BACKEND_BOUNDARIES.md)

