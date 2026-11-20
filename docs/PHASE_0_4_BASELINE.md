# Focus Forest — Phase 0.4 Baseline Audit

Status: baseline complete. This document describes the repository as it exists at the
Phase 0.3 tip, before any Phase 0.4 implementation.

## 1. Repository State

| Item | Value |
| :--- | :--- |
| Branch at audit start | `phase-0.3/product-hardening-and-feature-expansion` |
| Phase 0.3 final commit | `d1b8a8f665ae90fa14870fd9ddc4a550724196e0` |
| Working tree | clean (`git status --porcelain` empty) |
| Phase 0.4 branch | `phase-0.4/focus-workflows-and-personalization`, created from the Phase 0.3 tip |
| Application ID / namespace | `com.azanahmed.focusforest` |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Room | 2.7.0, database version 2, `MIGRATION_1_2` only |
| JDK / Gradle / AGP / Kotlin | 21 / 9.3.1 / 9.1.1 / 2.2.10 |
| Robolectric | 4.16.1, `@Config(sdk = [36])` |
| Baseline tests | 43/43 passed, 0 failed, 0 skipped |
| Baseline lint | 0 errors, 66 informational warnings |
| Baseline APK | 21,050,697 bytes, SHA-256 `1cd46b1a0a7715ec1e32768ffb0be8a0ed101dc877662ac11371c1f7f8a3ab39` |
| Network permissions | none (`INTERNET`, `ACCESS_NETWORK_STATE` absent) |

## 2. Implemented Capabilities

### Focus timer and chain

- Single sessions: `POMODORO` (25m), `DEEP_WORK` (50m), `CUSTOM` (5–180m), `SHORT_BREAK`
  (5m), `LONG_BREAK` (15m).
- Monotonic countdown via `FocusTimerEngine` and `ClockProvider.elapsedRealtimeMs()`;
  pause/resume; reboot detection via boot count; `InterruptedAfterReboot` instead of a
  fabricated completion.
- Pomodoro Chains: 1–8 focus rounds, configurable focus/short/long durations, long-break
  cadence, manual or automatic transitions.
- Exactly-once chain advancement (`lastAdvancedSessionId`) and atomic chain + next-timer
  persistence (`saveChainAndTimer`, `pendingStepSessionId`).
- Cold-start, foreground, and WorkManager recovery all share `recoverChainState()`.
- Conditional timer clearing (`clearActiveTimerIfSession`) prevents a late duplicate
  completion from deleting a newer chain step.
- A synchronous start guard closes the double-tap window.
- Spoken countdown semantics with explicit units, no per-second live region.

### Persistence and rewards

- Room v2 tables: `user_profile`, `focus_sessions`, `daily_stats`,
  `app_usage_snapshots`, `app_usage_items`, `challenges`, `challenge_progress`,
  `achievements`, `forest_progress`, `forest_trees`, `shop_items`, `owned_items`,
  `reward_ledger`.
- `FocusSessionEntity` stores: id, type, planned/completed minutes, status, wall-clock
  start/end, local date, XP, seeds, intention label, reward flag.
- `DailyStatsEntity` stores daily focus minutes, completed sessions, goal snapshot,
  goal-met flag, XP/seeds, and rest-day flag.
- `RewardAuthority` + `reward_ledger` are the single reward authority. Focus completion,
  challenges, achievements, and streak milestones are ledger-deduplicated.
- Break sessions earn 0 XP, 0 seeds, and plant no tree.
- `UserProfileEntity.dailyFocusGoalMinutes` and DataStore `daily_goal_minutes` both exist;
  `FocusRepository.updateProfileNameAndGoal` writes both.
- DataStore persists theme, notification preferences, ambient preferences, usage
  consent/monitored packages/budget, active timer, and active chain snapshots.

### Today, insights, forest, and data controls

- Today shows daily minutes/goal progress, a Quick Start button, active-chain status,
  streak/level pills, usage status, a Monday–Sunday weekly focus card, a highlight
  challenge, and a forest preview.
- Weekly focus is derived as `dailyGoal × 7`; it is not independently stored.
- Insights has 7D/30D/90D filters, time-of-day buckets, session-type breakdown, 30-day
  trend, monthly calendar, best day/time, personal records, and a recent-session list.
- Forest progression uses real completed focus sessions, tree species per session type,
  Leaf Points, shop ownership, environments, and decorations.
- CSV export exists for focus sessions and daily stats with correct comma/quote/newline
  escaping and ISO offset timestamps.
- Full local deletion clears Room tables, DataStore, WorkManager jobs, notifications,
  cached exports, ambient audio, and the usage memory cache.
- `allowBackup="false"`, backup/data-extraction exclusions, and a non-exported
  `FileProvider` are in place.

## 3. Partially Implemented or Missing Capabilities

### Missing: reusable routines

- There is no routine entity, DAO, repository, or UI.
- The only reusable configuration is the transient `ChainConfig` in `FocusUiState`.
  It is not persisted, has no name, and resets to defaults when the ViewModel is
  recreated.
- There is no way to pin, duplicate, reorder, archive, or delete a saved configuration.
- A chain can be started with an ad-hoc configuration, but it is not reusable and has no
  historical name snapshot.

### Partial: session context

- `FocusSessionEntity.intentionLabel` exists and is entered on the Focus screen.
- There is no category model, category persistence, category picker, or category snapshot.
- There is no routine reference or routine-name/configuration snapshot on sessions.
- There are no session notes or reflections.
- The intention field is capped at 60 characters and is not searchable in history.

### Partial: session history

- Insights shows a recent-session card list, but there is no dedicated history screen.
- There is no date-range, category, routine, status, or text filter.
- There is no session detail view and no way to add a note/reflection after completion.
- `observeAllSessions()` loads the entire session table; there are no bounded or filtered
  session queries.

### Partial: goals

- Daily goal is configurable in Settings (10–480 minutes) and persisted in both the
  profile and DataStore.
- Weekly goal is derived (`daily × 7`) and not editable or independently persisted.
- Today recomputes the current date only when an upstream flow emits; an app left open
  across midnight does not refresh its date until restart or a flow change.

### Partial: insights

- Insights aggregates session type, time of day, calendar cells, and personal records.
- There is no category or routine breakdown, no average completed-session duration, and
  no goal-achievement view.
- Aggregations run over all sessions in memory.

### Missing: routine/category integration with rewards and history

- No snapshots exist, so deleting a routine or renaming a category could not currently
  be tested for historical safety (the concepts do not exist yet).

## 4. Duplicate or Overlapping Functionality

- **Daily goal authority**: `UserProfileEntity.dailyFocusGoalMinutes` and DataStore
  `daily_goal_minutes` both hold the daily goal. Settings writes both; Today reads the
  DataStore value and overrides the stats snapshot. Phase 0.4 must not add a third
  authority for weekly goals.
- **Weekly progress**: Today derives the weekly target inline. Phase 0.4 should move this
  into one persisted weekly preference and one shared calculation.
- **Session lists**: Insights loads all sessions in memory; a dedicated history screen
  must not add a second unbounded query.
- **Timer configuration**: `ChainConfig` is used by both the chain UI and the timer
  engine. Routines must reuse this model rather than introduce a second timer config.

## 5. Technical Debt and Risky Integration Points

- **Room migration surface**: adding routine/category tables and session columns requires
  a genuine v2→v3 migration. `MIGRATION_1_2` must remain untouched and tested.
- **DataStore timer/chain snapshots**: every new active-session field must be written,
  read, cleared, and recovered. Missing one field would silently lose routine/category
  context after process death.
- **Chain step construction**: `FocusRepository.startChainStep` and `applyCompletion`
  build the next `ActiveTimerState`. Routine/category snapshots must be copied there,
  not re-read from the (possibly edited or deleted) routine row.
- **Start guard and recovery**: routine auto-start from Today must respect
  `timerStartPending`, persisted timers, and chain continuation. It must never start a
  second timer.
- **History metadata writes**: note/reflection updates must use a narrow SQL update that
  cannot touch XP, seeds, duration, status, or the reward ledger.
- **Full wipe ordering**: new tables must be cleared inside the existing transaction,
  and new DataStore keys must be removed by `clearAll()`.
- **Today recomposition**: Today already combines eight flows and renders a forest
  canvas. Adding routine/active-timer flows must not introduce a one-second ticker or
  per-recomposition database queries.
- **Navigation**: `FocusRoute` is a `data object`. Auto-starting a routine or Quick Start
  from Today needs a typed argument and one-shot consumption so a ViewModel recreation
  does not restart a timer.
- **Accessibility**: new cards, steppers, dialogs, and chips need labels, semantics, and
  touch targets that survive font scaling. No new per-second live regions.
- **CSV stability**: append new columns rather than reordering existing headers; keep
  escaping and locale-independent timestamps.

## 6. Opportunities for Phase 0.4

1. **One-tap routine start from Today** using the existing chain engine.
2. **Routine management** with starter presets, validation, pinning, duplication,
   archive/delete, and snapshot safety.
3. **Lightweight session context**: categories, optional intention, optional note, and a
   dismissible reflection that never affects rewards.
4. **Independent weekly goal** initialized from the previous effective weekly intention
   (`daily × 7`) and persisted in DataStore.
5. **Bounded, filterable history** with a detail view for persisted facts and metadata.
6. **Category/routine insights** derived from real completed sessions, with honest sample
   sizes and no causal claims.
7. **CSV columns** for the new fields, appended after the stable existing headers.
8. **Accessibility and calm UX**: explicit labels, semantics, and one-action starts.

## 7. Phase 0.4 Design Decisions

### Room v3

Add:

- `focus_routines`: id, name, description, focus/short/long minutes, long-break cadence,
  rounds, auto-transition, ambient preset, default category id, pinned, sort order,
  archived, built-in flag, created/last-used timestamps.
- `focus_categories`: id, name, archived, sort order, built-in flag, created timestamp.
- `focus_sessions` new columns: `routineId`, `routineNameSnapshot`, `routineSnapshot`,
  `categoryId`, `categoryNameSnapshot`, `note`, `reflection`.
- Indexes for `routineId` and `categoryId` in addition to the existing date/status
  indexes.

`MIGRATION_2_3` adds the columns with non-null defaults and creates the two tables. It
also seeds the documented starter categories/routines for existing installs. New installs
seed them when onboarding completes. A full wipe removes all rows; onboarding may seed
the same documented system defaults again.

### Snapshots, not foreign keys

Routines and categories are referenced by id for filtering, but display always uses the
session's name/configuration snapshot. There are no foreign keys, so deleting a routine
or archiving a category cannot corrupt or erase historical sessions. Editing a routine
never updates an active timer or a completed session because both hold copies.

### Goals

- Daily goal remains 10–480 minutes in DataStore and the profile.
- Weekly goal is a new DataStore key, defaulting once to the previous effective weekly
  intention (`daily × 7`) and then independently editable (0 disables the weekly target,
  maximum 3360 minutes).
- `daily_stats.goalMinutes` remains the daily snapshot written at completion.

### Rewards

`RewardAuthority` and `reward_ledger` remain the only reward path. Metadata is copied onto
the completed session before the existing completion transaction. Notes, reflections,
categories, and routine CRUD never call the reward authority.

### History and insights

History uses bounded SQL queries (filters in SQL, `LIMIT`), not full-table loads.
Insights keeps the existing 7D/30D/90D windows and adds category/routine breakdowns from
completed focus sessions in the selected bounded window.

## 8. Baseline Verification

Phase 0.3 results are treated as the baseline and will be re-verified locally after the
Phase 0.4 implementation:

```bash
JAVA_HOME=/opt/jdk-21 ./gradlew clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
  --console=plain --no-daemon -Djava.awt.headless=true
```

No physical API 36 device, TalkBack pass, or remote CI run is claimed in this baseline.
