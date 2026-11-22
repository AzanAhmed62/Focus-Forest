# Privacy & Data Flow

Focus Forest is engineered as a **strictly offline, local-first** Android application.

## Network & Telemetry Guarantees

- **No Internet Access**: `AndroidManifest.xml` declares zero `android.permission.INTERNET`. WorkManager's library manifest normally injects `ACCESS_NETWORK_STATE` for network-constrained work; Focus Forest schedules no network-constrained jobs and explicitly removes that permission with `tools:node="remove"` in the merged manifest.
- **No Analytics, Crash SDKs, or Ads**: Unused Firebase, HTTP, location, camera, and third-party dependencies remain disabled in `app/build.gradle.kts`.
- **No Backup Leakage**: `android:allowBackup="false"` is configured in `AndroidManifest.xml`, backed by explicit full-domain exclusions in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` (Room database, DataStore preferences, SharedPreferences, files, and external storage).

## Permissions Requested

| Permission | Scope | Purpose | Optional? |
| :--- | :--- | :--- | :--- |
| `PACKAGE_USAGE_STATS` | Special App Access (`Settings.ACTION_USAGE_ACCESS_SETTINGS`) | Read daily foreground usage durations and chosen monitored app totals. | Yes — core focus timer, chains, soundscapes, challenges, and forest work without it. |
| `POST_NOTIFICATIONS` | Runtime permission (Android 13 / API 33+) | Gentle session completion alerts, daily intention reminders, and weekly reflection summaries. | Yes — configurable inside Settings with quiet hours. |

### Launcher App Visibility (`<queries>`)

Instead of requesting broad `QUERY_ALL_PACKAGES`, Focus Forest declares only an `<intent>` query for `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`. Launchable app scanning is never performed unless `PACKAGE_USAGE_STATS` is granted and the user accepted the explicit disclosure.

### Library-Injected Permissions

The merged manifest also contains normal (non-dangerous) permissions required by AndroidX/WorkManager for reliable background session completion and reboot rescheduling: `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, and the AndroidX `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. These do not grant network, location, camera, microphone, or contacts access. `ACCESS_NETWORK_STATE` is explicitly removed because no network-constrained work is scheduled.

## Honest Share & Export Boundaries

- **Share Text (`ShareFormatter`)**: Formats focus session milestones, streaks, and tree counts only. Observed app names, package names, and raw usage stats are never included in shareable text.
- **CSV Export (`CsvExporter` + `FileProvider`)**: Exports focus sessions and daily focus summaries from `cache/exports/` using a private non-exported `FileProvider`. Session CSV columns include routine/category ids and snapshots plus note/reflection; these are user-owned local data and are exported only on explicit user action.
- **Delete All Local Data (`DataManagementRepository.deleteAllLocalData`)**: Stops procedural ambient audio, cancels all WorkManager jobs (`FocusWorkers.cancelAll`), clears active notifications, deletes cached CSV exports, wipes all Room tables (`FocusForestDatabase.clearAllUserData`, including routines, categories, session notes/reflections, and metadata), clears DataStore Preferences (including the independent weekly goal), and resets `UsageRepository` memory cache. Challenge history is stored in the existing `challenges` / `challenge_progress` tables, so the same wipe removes it. Only the documented starter routines/categories may be seeded again by onboarding after a wipe.

## Phase 0.3 Privacy Review

Phase 0.3 did not add a permission, network dependency, account, SDK, or database table.

- **Challenge History** reads archived/expired rows from the existing local challenge tables and never uploads them.
- **Weekly Focus Intention** is computed in memory from existing `daily_stats` rows; no new profile or analytics record is written.
- **Cancellation and recovery fixes** change control flow only. They do not add logging, telemetry, or data collection.
- **No schema change in Phase 0.3**: at that point the Room database remained version 2
  with `MIGRATION_1_2`; no destructive migration was introduced. Phase 0.4 adds the
  non-destructive v3 migration described below.
- **No backend boundary crossed**: the merged manifest still declares zero `INTERNET` permission. Any future network feature must follow [FUTURE_BACKEND_BOUNDARIES.md](FUTURE_BACKEND_BOUNDARIES.md).

## Phase 0.4 Privacy Review

Phase 0.4 added no permission, network dependency, account, SDK, remote analytics, or
advertising component.

- **Room v3**: new `focus_routines` and `focus_categories` tables plus session-context
  columns (`routineId`, `routineNameSnapshot`, `routineSnapshot`, `categoryId`,
  `categoryNameSnapshot`, `note`, `reflection`). `MIGRATION_2_3` is additive and
  non-destructive; `MIGRATION_1_2` is unchanged. No foreign keys are used.
- **User content stays local**: routine/category names and descriptions, session notes, and
  reflections are stored only in Room. They are never uploaded, used for rewards, or
  included in share text. CSV export includes them only when the user explicitly exports.
- **Snapshots protect history**: completed sessions keep routine/category snapshots, so
  editing or deleting a template cannot rewrite past records. Deleting a routine does not
  cascade into session history.
- **Metadata-only updates**: `updateSessionMetadata` can only change `note` and
  `reflection` on finished sessions. It cannot alter XP, seeds, duration, status, or the
  reward ledger.
- **Independent weekly goal**: stored in DataStore Preferences, initialized once from the
  previous effective intention (`daily × 7`), and removable by `clearAll()`.
- **Full wipe**: `deleteAllLocalData` clears the new tables, all session metadata, the
  weekly goal, scheduled work, notifications, and cached exports. Only the documented
  starter routines/categories may be re-seeded after onboarding.
- **No device or remote verification is claimed** for privacy behavior in this phase; see
  [PHASE_0_4_REPORT.md](PHASE_0_4_REPORT.md).

## Phase 0.5 Privacy Review

Phase 0.5 added no permission, network dependency, account, SDK, remote analytics,
advertising component, or Room schema change.

- **Onboarding stays local and optional**: the display name, daily goal, intention, and
  default session are stored only in the local Room profile and DataStore preferences.
  Skipping onboarding requires no account, Usage Access, or notification permission.
  In-progress page state lives in `SavedStateHandle` and is not persisted beyond the
  process.
- **Default session preference**: `default_session_type` and `default_session_minutes`
  are local DataStore keys. They are read only when no timer or chain is active, so a
  recovered session is never rewritten.
- **Ambient audio settings**: preset, volume, and “start with sessions” are local
  DataStore keys. Audio is still synthesized on-device; no audio file is downloaded.
- **Forest artwork is local**: the grove canvas and thumbnails are drawn from local
  Compose primitives and persisted forest state. No remote image is fetched. An unused
  bundled forest JPEG was deleted to reduce APK size.
- **Usage Access is demoted, not required**: onboarding mentions it only as an optional
  later choice. Today places optional usage last and explicitly states that the app works
  fully without it.
- **Full wipe** clears the new DataStore keys along with all other preferences and Room
  data; only documented starter routines/categories may be re-seeded after onboarding.
- **No device or remote verification is claimed** for privacy behavior in this phase; see
  [PHASE_0_5_REPORT.md](PHASE_0_5_REPORT.md).
