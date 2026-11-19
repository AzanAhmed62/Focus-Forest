# Focus Forest

**Grow your focus. Reclaim your time.**

Focus Forest is a strictly offline, local-first native Android digital wellness application built with Kotlin, Jetpack Compose, Material 3, Room, DataStore Preferences, and WorkManager.

## Core Capabilities

- **Monotonic Focus Timer & Pomodoro Chain Mode**: Single focus blocks (`Pomodoro`, `Deep Work`, `Custom Focus`) and intentional rests (`Short Break`, `Long Break`), plus structured multi-round Pomodoro Chains with configurable focus/break durations, long-break cadence, and auto-vs-manual step transitions. Timer state persists across backgrounding, process death, and device reboots via `ClockProvider` monotonic elapsed realtime checks.
- **Offline Ambient Soundscapes**: Pure procedural PCM DSP audio synthesis (`AndroidAmbientAudioController` backed by `AudioTrack`) for Soft Rain, Forest Wind, Ocean Waves, Hearth Ember, and Deep Focus Hum—zero audio assets downloaded or streamed.
- **Optional Android Usage Insights**: Explicit, plain-language consent flow (`PACKAGE_USAGE_STATS`) and least-privilege `<queries>` launcher discovery (no `QUERY_ALL_PACKAGES`), exact millisecond aggregation including sub-minute apps (`<1m`), distinct `COMPLETE` / `PARTIAL_TODAY` / `ZERO_USAGE` / `UNAVAILABLE` states, daily usage budget tracking, and honest completed-day and 7-day-vs-7-day comparisons (partial today is never compared against finished days).
- **Challenges, Achievements, Botanical Shop & Virtual Forest**: Transactional `RewardAuthority` with `RewardLedgerEntity` deduplication across sessions, deterministic daily/weekly challenges, user-defined custom challenges (with validation, editing, deletion/archiving, and anti-farming daily caps), milestone achievements, Leaf Points, and interactive Canvas botanical forest scenes.
- **Local Privacy & Data Portability**: Zero `INTERNET` permission, `allowBackup="false"`, excluded cloud backup & device transfer rules, privacy-safe share formatting, CSV exports via `FileProvider`, and complete one-tap local reset (`DataManagementRepository.deleteAllLocalData`).

## Project Configuration

- **Application ID**: `com.azanahmed.focusforest`
- **Build Namespace**: `com.azanahmed.focusforest` (source packages live under `com/azanahmed/focusforest/`)
- **Min / Compile / Target SDK**: API 26 / API 36 / API 36
- **Java / Kotlin Toolchain**: Java 17 bytecode target, JDK 21 for Gradle/tests/CI, Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, Room 2.7.0 (KSP schema export)

## Build & Verification

The repository ships a genuine Gradle wrapper (Gradle 9.3.1 with the official distribution checksum). Use JDK 21:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleDebug
```

## Privacy & Optional Usage Access

- Android `PACKAGE_USAGE_STATS` permission is completely optional; timers, Pomodoro Chains, ambient audio, challenges, and forest growth all work without granting it.
- Focus Forest declares zero `INTERNET` permission, `allowBackup="false"`, and least-privilege launcher discovery (`<intent>` filter without `QUERY_ALL_PACKAGES`).
- All records stay in local Room and DataStore storage and can be exported to CSV or permanently erased in Settings.

## Known Limitations & Roadmap

- Physical device verification across Doze/WorkManager, real `AudioTrack` output, and OEM `UsageStatsManager` quirks remains pending (no API 36 emulator or physical device was available in the Phase 0.2 environment).
- Cloud sync, social challenge rooms, and Google Play Billing entitlements are intentionally deferred to a future server-backed phase.

See the detailed project documentation:
- [Architecture](docs/ARCHITECTURE.md)
- [Privacy & Data Flow](docs/PRIVACY_AND_DATA_FLOW.md)
- [Feature Status](docs/FEATURE_STATUS.md)
- [Testing](docs/TESTING.md)
- [Phase 0.1 Engineering Report](docs/PHASE_0_1_REPORT.md)
- [Phase 0.2 Build Recovery & Device QA Report](docs/PHASE_0_2_REPORT.md)

