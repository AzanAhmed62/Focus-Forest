# Focus Forest — Phase 0.5 Forest Experience & Product Polish Report

## 1. Executive Summary

Phase 0.5 makes the forest the center of Focus Forest and gives the product a coherent,
calm identity without weakening any Phase 0.1–0.4 guarantee:

- **Forest-first product**: the grove is a deterministic, stage-aware scene with honest
  growth copy, species counts, owned environments, decoration placement, a planting
  timeline, milestones, and a non-visual summary. Empty forests explain how to plant the
  first tree instead of showing fabricated progress.
- **Coherent design system**: shared spacing, radius, size, and motion tokens; semantic
  success/warning/leaf colors for light and dark themes; token-based cards, section
  headers, icon buttons, and empty states. The four existing botanical skins remain
  supported.
- **Calm onboarding**: three short, skippable pages capture an optional name, daily goal,
  intention, and default focus session. No account, Usage Access, or notification
  permission is required. Choices survive process recreation and remain editable in
  Settings.
- **Clearer Today**: greeting/date, today's focus, one obvious start/resume action, active
  session or chain context, rhythm stats, routines, weekly intention, recent activity,
  challenge, a small forest preview, and optional usage last. No fabricated stats and no
  stale timer.
- **Grouped Settings**: Appearance, Focus, Goals, Audio, Notifications, Privacy & data, and
  About. Default session and ambient controls are persisted with key-specific setters;
  destructive data deletion remains explicitly confirmed.
- **Honest Shop**: visual previews, equipped/owned ordering, purchase confirmation, and
  `equipOwnedItem` so the Forest screen can never implicitly buy a locked item.
- **No architectural drift**: Room remains version 3 with the existing non-destructive
  migrations, and the reward ledger, `RewardAuthority`, forest thresholds, and economy are
  unchanged. No permission, network dependency, account, SDK, telemetry, ads, billing, or
  AI was added.

## 2. Branch, Base, and Scope

- **Base commit**: `f9847b570533a6d68b4a0c379af2137f1c24dd16` (Phase 0.4 final).
- **Working branch**: `phase-0.5/forest-experience-and-product-polish`.
- No commit or push to `main`; no force-push, reset, or history rewrite; no merge.
- Strictly local-first: no Firebase, Supabase, auth, cloud sync, social features,
  leaderboards, ads, remote analytics, billing, AI, remote images, or new permissions.
- No dependency upgrades. `applicationId`/namespace remain
  `com.azanahmed.focusforest`; `minSdk 26`, `compileSdk 36`, `targetSdk 36`.
- Existing timer, chain, recovery, reward, challenge, history, insights, routine, and
  usage behavior is preserved. Phase 0.4 regression tests still pass.

## 3. Environment

| Item | Value |
| :--- | :--- |
| OS | Linux local workspace |
| JDK | Eclipse Temurin OpenJDK **21.0.8** |
| Gradle | **9.3.1** via committed wrapper |
| Android Gradle Plugin | **9.1.1** |
| Kotlin plugin | **2.2.10** (Gradle-embedded Kotlin 2.2.21) |
| compileSdk / targetSdk | **36** |
| minSdk | **26** |
| Java bytecode target | **17** |
| Room | **2.7.0**, DB version **3**, migrations unchanged |
| Robolectric | **4.16.1**, `@Config(sdk = [36])` |
| Build flags | `--no-daemon -Djava.awt.headless=true`, `--no-build-cache` for final verification |

## 4. Product Additions

### 4.1 Forest Experience

- `ForestScreen` leads with a stage-aware grove canvas, forest level, lifetime Seeds,
  spendable Leaf Points, and the exact Seeds remaining to the next stage.
- The screen shows:
  - three plantable species with honest counts (Pine/Oak/Birch);
  - Fern and Willow as rest-day flora, because breaks never plant trees;
  - owned environments and decorations, with locked items routed to the Shop;
  - a planting timeline from persisted completed sessions;
  - milestones derived from existing achievements;
  - a text summary of level, trees, Seeds, and Leaf Points for non-visual access.
- An empty forest shows a real explanation and a **Plant Your First Tree** action. There
  are no placeholder trees, fake counts, or decorative progress.
- `ForestIllustrationCanvas` draws trees deterministically from persisted tree ids, with
  stable per-tree variation, six growth stages, light/dark environment palettes, and static
  grass/fireflies. It does not run an infinite idle animation.
- `ForestViewModel` exposes forest state, environments, decorations, and equip/toggle
  actions. `ShopAndForestRepository.equipOwnedItem` refuses unowned items with `NotOwned`
  instead of purchasing implicitly.

### 4.2 Design System

- `ui/theme/Dimens.kt` adds `ForestSpacing`, `ForestRadii`, `ForestSizes`, and
  `ForestMotion`.
- `Color.kt` adds semantic success/warning/leaf colors with light and dark variants;
  `Theme.kt` provides `ForestSemanticColors` through `LocalForestSemanticColors`.
- Shapes are token-based (`12/16/22/28 dp`), and `CommonComponents.kt` provides token-based
  `BotanicalCard`, `SectionHeader`, `ForestIconButton`, and `EmptyStateCard`.
- Screens use the tokens instead of one-off paddings and radii. The four existing theme
  skins and both light/dark modes remain coherent.
- No heavy gradients, glassmorphism, neon, gambling-like reward presentation, or tiny
  labels were introduced.

### 4.3 Onboarding and Defaults

- Three skippable pages:
  1. welcome and product promise;
  2. rhythm — optional display name, daily goal, primary intention;
  3. first session — default focus type/length, a plain summary, and an optional Usage
     Access note.
- Skipping writes the same safe defaults and enters the app; it never requests a
  permission.
- In-progress page and choices are stored in `SavedStateHandle`, so process recreation
  restores the flow. Completion is persisted only after profile, goal, weekly goal, and
  default-session writes finish.
- `default_session_type` and `default_session_minutes` are local DataStore keys. They are
  clamped to 5–240 minutes and reject break types.
- `FocusViewModel` applies the default only when no timer and no active chain are running.
  A completed chain counts as idle; timer/chain recovery always wins.

### 4.4 Today

Today now follows one hierarchy:

1. greeting and date;
2. today's focus and one obvious start/resume action;
3. active session or chain context;
4. rhythm stats (today/week);
5. pinned routines;
6. weekly intention;
7. recent activity;
8. challenge;
9. small forest preview;
10. optional usage last.

The duplicate first-time card was removed. Optional Usage Access is explicitly described
as optional, and the app states that it works fully without it. No stat is fabricated and
no stale timer is shown when none exists.

### 4.5 Settings

Settings is grouped into:

- **Appearance** — theme and skin;
- **Focus** — default session type/length;
- **Goals** — daily and weekly targets and profile;
- **Audio** — ambient preset, volume, and play-with-session;
- **Notifications** — completion and reminder preferences;
- **Privacy & data** — privacy screen, CSV export, and confirmed local-data deletion;
- **About** — app version and local-first statement.

Ambient settings use key-specific DataStore setters (`setAmbientPreset`,
`setAmbientVolume`, `setAmbientPlayWithSession`), so changing one value cannot clobber the
others. The previous “Focus Forest Plus (Planned)” roadmap card was removed.

### 4.6 Shop

- Items show visual previews and equipped/owned items sort first.
- Loading and empty states are explicit.
- Locked copy is honest; the Forest screen routes locked environments/decorations to the
  Shop rather than buying them.
- Purchases require confirmation before spending Leaf Points.
- Decoration removal has explicit “remove” semantics rather than an ambiguous toggle.

### 4.7 Accessibility

- The focus countdown keeps the Phase 0.3 spoken-duration semantics and does not expose a
  per-second live region.
- The forest canvas exposes a non-visual level/trees/Seeds/Leaf Points summary.
- New controls have labels and test tags; headings use section semantics.
- Decorative motion is disabled when system animators are disabled.
- Full TalkBack and large-font device verification remains deferred and is not claimed.

## 5. Economy and Data Integrity

- No Room schema change: database version remains **3**; `2.json` and `3.json` are
  unchanged from the Phase 0.4 baseline.
- `MIGRATION_1_2` and `MIGRATION_2_3` remain the only migrations, and no destructive
  fallback is registered.
- Growth remains: six stages at **0/3/10/25/55/100** lifetime Seeds; forest level
  `1 + seeds/12`.
- Session rewards are unchanged: Pomodoro/Custom `minutes × 2 XP`,
  `max(1, minutes/10)` Seeds; Deep Work `minutes × 2 + 20 XP`,
  `max(2, minutes/10)` Seeds; breaks `0 XP / 0 Seeds`.
- Every session, challenge, achievement, and streak credit still goes through
  `RewardAuthority` and the unique `reward_ledger` key. Duplicate completion cannot
  double-credit.
- Breaks still cannot plant trees, add Seeds, or add Leaf Points. Buying cosmetics spends
  Leaf Points only and never reduces lifetime Seeds or growth.
- Full details are in [FOREST_ECONOMY.md](FOREST_ECONOMY.md).

## 6. Phase 0.4 `FocusContextWorkflowTest` Audit

The Phase 0.4 fix runs the blocking test driver on `Dispatchers.IO`:

```kotlin
fun `routine start copies snapshots ...`() = runBlocking(Dispatchers.IO) {
    // Test-only driver isolation ...
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    ...
}
```

Audit conclusion:

- The ViewModel under test still uses its normal `viewModelScope` (Main dispatcher); no
  production dispatcher logic was moved to IO.
- The change only prevents the test from blocking Robolectric's main thread while it
  advances the test scheduler and waits for DataStore callbacks.
- Assertions and scenarios are unchanged; no behavior was weakened or skipped.
- The helper still advances the test scheduler and polls a deadline, so the test remains
  deterministic rather than relying on arbitrary sleeps alone.
- The full suite passed repeatedly, including this workflow test, on the final clean run.

## 7. Tests and Verification

### 7.1 Commands

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew --version
JAVA_HOME=/opt/jdk-21 ./gradlew clean --no-build-cache \
  --no-daemon -Djava.awt.headless=true
JAVA_HOME=/opt/jdk-21 ./gradlew :app:testDebugUnitTest --no-build-cache \
  --no-daemon -Djava.awt.headless=true
JAVA_HOME=/opt/jdk-21 ./gradlew :app:lintDebug --no-build-cache \
  --no-daemon -Djava.awt.headless=true
JAVA_HOME=/opt/jdk-21 ./gradlew :app:assembleDebug --no-build-cache \
  --no-daemon -Djava.awt.headless=true
```

### 7.2 Test Suites

| Suite | Tests | Result |
| :--- | ---: | :--- |
| `DomainEnginesTest` | 13 | passed |
| `FocusTimerEngineTest` | 3 | passed |
| `UsageAnalyticsEngineTest` | 3 | passed |
| `AndroidUsageStatsGatewayTest` | 2 | passed |
| `PersistenceAndExportTest` | 7 | passed |
| `RoomMigrationAndLedgerTest` | 5 | passed |
| `FocusContextWorkflowTest` | 5 | passed |
| `NotificationHelperTest` | 1 | passed |
| `TimerWorkerAndChainTest` | 12 | passed |
| `FocusViewModelCompletionTest` (Robolectric SDK 36) | 1 | passed |
| `ForestScreenTest` (Robolectric SDK 36) | 2 | passed |
| `OnboardingViewModelTest` (Robolectric SDK 36) | 3 | passed |
| `SettingsViewModelTest` (Robolectric SDK 36) | 2 | passed |
| `FocusForestUiTest` (Robolectric SDK 36) | 1 | passed |
| `ExampleUnitTest` + `ExampleRobolectricTest` | 2 | passed |
| **Total** | **62** | **62 passed, 0 failed, 0 errors, 0 skipped** |

New Phase 0.5 coverage includes:

- three-page onboarding state restoration, skip completion without Usage Access, persisted
  profile/goal/default session, and rejection of break defaults;
- empty-forest explanation, non-visual summary, start-first-focus CTA, stage progress,
  owned environment equipping, locked environment routing, and decoration controls;
- settings default-session persistence/clamping and independent ambient preference keys;
- forest next-stage thresholds and `equipOwnedItem` purchase safety;
- default-session persistence, clamping, and break rejection in the persistence suite;
- the updated UI journey through the three-page onboarding, forest environment selection,
  shop, dark theme, and privacy screen.

The existing Phase 0.1–0.4 tests all still pass, including timer/chain workers, recovery,
migration, reward-ledger deduplication, metadata invariants, CSV export, and full wipe.

### 7.3 Lint

`lintDebug` completed with **0 errors, 64 warnings**. Categories:

| Category | Count |
| :--- | ---: |
| `GradleDependency` | 25 |
| `UnusedResources` | 16 |
| `NewerVersionAvailable` | 11 |
| `IconLauncherShape` | 6 |
| `AndroidGradlePluginVersion` | 2 |
| `IconLocation` | 2 |
| `RedundantLabel` | 1 |
| `ObsoleteSdkInt` | 1 |

All are dependency-update notices, launcher-icon hygiene, or pre-existing unused-resource
notices. No correctness, security, manifest, or changed-Kotlin-source warning was reported.
The count can fluctuate with the local dependency metadata cache; the Phase 0.4 baseline
was 59.

### 7.4 Debug APK

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Size: **20,675,293 bytes**
- SHA-256: `f4c48097ed03d61177b927f6c781b2e0f5b93d96f21cc172e0adee82d6fc7617`
- Package: `com.azanahmed.focusforest`, versionCode 1, versionName 1.0.0
- minSdk 26, targetSdk 36, compileSdk 36, debuggable
- Merged permissions: `PACKAGE_USAGE_STATS`, `POST_NOTIFICATIONS`, `WAKE_LOCK`,
  `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, and
  `com.azanahmed.focusforest.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- **No `INTERNET` and no `ACCESS_NETWORK_STATE`.**
- `android:allowBackup="false"` with `backup_rules.xml` and `data_extraction_rules.xml`.
- The unused `img_forest_meadow.jpg` (686,456 bytes) was deleted; the APK is smaller than
  the Phase 0.4 build despite the new UI code.

### 7.5 Clean Verification

Verification used the committed Gradle wrapper, JDK 21, `clean`, and `--no-build-cache` so
no test or lint task could be restored from cache.

- `--version`: Gradle **9.3.1**, Launcher JVM **21.0.8** (Eclipse Adoptium 21.0.8+9-LTS),
  Gradle-embedded Kotlin 2.2.21, Linux.
- `clean`: **BUILD SUCCESSFUL in 31s**.
- `:app:testDebugUnitTest`: **BUILD SUCCESSFUL in 4m 48s**; **62/62 tests passed**.
- `:app:lintDebug`: **BUILD SUCCESSFUL in 5m 25s**; **0 errors, 64 warnings**.
- `:app:assembleDebug`: **BUILD SUCCESSFUL in 1m 22s**; APK evidence in section 7.4.
- Room schema diff against the Phase 0.4 base: none.
- Final `git status --short`: clean after the documentation commit; branch not pushed or
  merged.

## 8. Privacy Review

- No new permission, network dependency, account, SDK, telemetry, ads, or billing.
- Onboarding data (name, goal, intention, default session) is stored only in the local
  Room profile and DataStore. In-progress onboarding state lives in `SavedStateHandle` and
  does not outlive the process.
- The default session and ambient controls are local DataStore keys.
- Forest artwork is drawn locally from persisted state; no remote image is fetched.
- Usage Access remains optional and is demoted to the end of Today. The app never claims
  blocking or usage data without permission.
- The full-wipe path clears the new preferences with the existing Room/DataStore wipe.
- See [PRIVACY_AND_DATA_FLOW.md](PRIVACY_AND_DATA_FLOW.md) for the detailed review.

## 9. Readiness Assessment

| Area | Status | Basis |
| :--- | :--- | :--- |
| Product experience | **PASS** | Cohesive Today, onboarding, settings, shop, and forest hierarchy; local verification and UI tests pass. |
| Forest | **PASS** | Deterministic stage-aware canvas, honest empty state, species/environment/decoration/timeline/milestone coverage, non-visual summary. |
| Design system | **PASS** | Tokenized spacing/radii/sizes/motion, semantic colors, shared components, coherent light/dark skins. |
| Timer regression | **PASS** | All Phase 0.1–0.4 timer, chain, recovery, worker, and reward tests pass; default session applies only when idle. |
| Database | **PASS** | Room v3 unchanged, both migrations intact, migration/ledger tests pass, no destructive fallback. |
| Privacy | **PASS** | No new permission or network capability; manifest verified; backup disabled; Usage Access optional. |
| Accessibility | **CONDITIONAL** | Static/automated semantics and spoken timer text verified; no physical TalkBack or large-font pass. |
| Tests | **PASS** | 62/62 tests on a clean, no-cache run. |
| Device QA | **NOT VERIFIED** | No physical device/emulator or multi-form-factor pass in this phase; deferred to Phase 0.6. |

## 10. Verification Limits

- No API 36 physical device or emulator was available. Robolectric SDK 36 exercises logic
  and Compose semantics, not real hardware rendering, touch, or performance.
- No physical TalkBack, switch-access, or large-font pass was performed.
- Multi-form-factor layouts (foldables, tablets, landscape, very small screens) were not
  device-verified; responsive composition is implemented but deferred to Phase 0.6 QA.
- Audio audibility, Doze/overnight completion, reboot rescheduling, OEM usage-stat
  behavior, and CSV share-sheet behavior were not re-verified in this phase.
- On-device migration from a real Phase 0.4 installation was not performed; migrations are
  covered by Robolectric SQLite tests.
- Remote CI was not run and nothing was pushed or merged.

## 11. Commits

On `phase-0.5/forest-experience-and-product-polish`:

- `8ece571` — chore: establish phase 0.5 baseline
- `9bd7afb` — feat(theme): add forest design tokens and semantic colors
- `1f729bf` — feat(forest): make the grove the main product surface
- `71a76c7` — feat(onboarding): add skippable setup and default session preference
- `5a63086` — feat(ui): restructure Today and grouped settings
- `8c0ac06` — test: cover forest, onboarding, settings, and phase 0.5 flows
- final documentation commit — README, architecture, feature status, testing, privacy,
  forest economy, and this report

No push, force-push, reset, history rewrite, or merge was performed.

## 12. Deferred Work

- Physical-device and multi-form-factor QA (API 26–36, landscape, tablets, foldables,
  large fonts, TalkBack) belongs to Phase 0.6.
- Real-device migration from Phase 0.4 and a full onboarding/wipe cycle remain unverified
  outside Robolectric.
- Cloud sync, accounts, social features, leaderboards, ads, analytics, billing, and AI
  remain intentionally out of scope.
- Dependency and Compose/Material upgrades remain deferred to avoid an incompatible
  toolchain change in a polish phase.
