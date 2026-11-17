# Privacy & Data Flow

Focus Forest is engineered as a **strictly offline, local-first** Android application.

## Network & Telemetry Guarantees

- **No Internet Access**: `AndroidManifest.xml` declares zero `android.permission.INTERNET` and zero `ACCESS_NETWORK_STATE`.
- **No Analytics, Crash SDKs, or Ads**: Unused Firebase, HTTP, location, camera, and third-party dependencies remain disabled in `app/build.gradle.kts`.
- **No Backup Leakage**: `android:allowBackup="false"` is configured in `AndroidManifest.xml`, backed by explicit full-domain exclusions in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` (Room database, DataStore preferences, SharedPreferences, files, and external storage).

## Permissions Requested

| Permission | Scope | Purpose | Optional? |
| :--- | :--- | :--- | :--- |
| `PACKAGE_USAGE_STATS` | Special App Access (`Settings.ACTION_USAGE_ACCESS_SETTINGS`) | Read daily foreground usage durations and chosen monitored app totals. | Yes — core focus timer, chains, soundscapes, challenges, and forest work without it. |
| `POST_NOTIFICATIONS` | Runtime permission (Android 13 / API 33+) | Gentle session completion alerts, daily intention reminders, and weekly reflection summaries. | Yes — configurable inside Settings with quiet hours. |

### Launcher App Visibility (`<queries>`)

Instead of requesting broad `QUERY_ALL_PACKAGES`, Focus Forest declares only an `<intent>` query for `android.intent.action.MAIN` + `android.intent.category.LAUNCHER`. Launchable app scanning is never performed unless `PACKAGE_USAGE_STATS` is granted and the user accepted the explicit disclosure.

## Honest Share & Export Boundaries

- **Share Text (`ShareFormatter`)**: Formats focus session milestones, streaks, and tree counts only. Observed app names, package names, and raw usage stats are never included in shareable text.
- **CSV Export (`CsvExporter` + `FileProvider`)**: Exports focus sessions and daily focus summaries from `cache/exports/` using a private non-exported `FileProvider`.
- **Delete All Local Data (`DataManagementRepository.deleteAllLocalData`)**: Stops procedural ambient audio, cancels all WorkManager jobs (`FocusWorkers.cancelAll`), clears active notifications, deletes cached CSV exports, wipes all Room tables (`FocusForestDatabase.clearAllUserData`), clears DataStore Preferences, and resets `UsageRepository` memory cache.
