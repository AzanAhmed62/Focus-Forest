# Future Backend Boundaries

Status: informational boundary document for Phase 0.4. Focus Forest has **no backend**,
**no accounts**, **no network permission**, and **no remote analytics**. Nothing in this
document authorizes adding one; it defines the conditions a future server-backed feature
would have to satisfy before it could be considered.

## 1. Current Non-Negotiable Guarantees

These are product and privacy guarantees, not temporary implementation details:

- The app declares no `android.permission.INTERNET`.
- Core focus timers, Pomodoro Chains, reusable routines, ambient soundscapes, challenges,
  achievements, forest growth, session history, categories, notes, and reflections work
  fully offline.
- Session completion and reward credit are decided on-device by `RewardAuthority` and the
  local reward ledger. A server must never be required to finish a local session or grant
  local XP, seeds, or trees.
- `PACKAGE_USAGE_STATS` is optional, explicitly consented to, and revocable. Raw usage
  snapshots and observed package names stay on the device.
- `allowBackup="false"` and the cloud-backup/device-transfer exclusions remain in force.
- The Room database is the durable local source of truth for sessions, rewards,
  challenges, achievements, forest state, and usage snapshots. DataStore holds preferences
  and timer/chain checkpoints.
- Deleting all local data removes user-owned data and leaves no remote copy behind.

## 2. Data Classification

Any future design must classify data before moving it:

| Class | Examples | Default rule |
| :--- | :--- | :--- |
| Sensitive local-only | Raw app usage durations, observed package names, app labels/icons, focus intentions, session notes/reflections | Never uploaded by default. Requires explicit, separate opt-in if ever shared. |
| User content | Custom challenge titles, routine/category names and descriptions, forest layout, display name, avatar style | May sync only with explicit consent and deletion parity. |
| Derived local data | Daily/weekly focus totals, weekly goal, category/routine insights, challenge history, streaks, levels | Prefer recomputing locally from synced primitives instead of uploading aggregates. |
| Device configuration | Theme, sound preset, notification preferences, quiet hours | Local by default; sync only if it materially improves the feature. |
| Operational metadata | Crash reports, sync diagnostics, install IDs | Not collected today. Any future collection needs a privacy review and an opt-out story. |

## 3. Rules for Any Future Network Feature

1. **Separate module and interface.** Network code must live behind a repository/gateway
   interface in a dedicated module or package. ViewModels, Compose screens, and pure domain
   engines must not call HTTP APIs directly.
2. **Local-first authority.** The local database remains authoritative for local-only
   behavior. A network failure must degrade to the current offline experience, not block a
   timer, reward, challenge, or export.
3. **Explicit opt-in.** Sync, account creation, and any upload are separate user choices.
   Declining them must leave every current feature usable.
4. **No raw usage upload by default.** If usage insights ever sync, send only the minimum
   derived values the feature needs, never package names or raw per-app timelines, unless
   the user explicitly opts in to that specific data.
5. **Data minimization and retention.** Define what is stored, why, for how long, and how it
   is deleted. Prefer pseudonymous identifiers and avoid email/phone collection unless a
   feature truly requires it.
6. **Deletion parity.** "Delete all local data" must also delete or schedule deletion of
   remote user data. A local wipe that leaves a silent cloud copy is a privacy defect.
7. **Offline conflict rules.** Define deterministic merge behavior for sessions, rewards,
   challenge progress, and forest state. Rewards must remain idempotent across devices and
   retries. Do not let a server re-award a locally credited session.
8. **Schema discipline.** Any Room change still requires a version bump, a real migration,
   exported schema, and a migration test. A backend feature is not an excuse for a
   destructive migration.
9. **Manifest discipline.** Adding `INTERNET` or `ACCESS_NETWORK_STATE` is a visible product
   change. It must be called out in the PR, reflected in privacy documentation, and covered
   by a merged-manifest test or inspection.
10. **Secrets discipline.** No API keys, service-account files, signing material, or tokens
    in the repository. Client builds must not embed server secrets.
11. **Test discipline.** Cover offline mode, auth failure, timeout, retry, duplicate sync,
    conflict resolution, deletion propagation, and migration. Do not weaken existing local
    tests to accommodate a network path.
12. **Feature-flag discipline.** Ship network features dark or behind an explicit local
    switch until the privacy, security, and offline behavior are verified.

## 4. Explicitly Out of Scope for Phase 0.4

The following remain prohibited in this phase and are not implied by this document:

- Supabase, Firebase, custom servers, or any remote database.
- Accounts, authentication, cloud sync, or cross-device restore.
- Social features, shared challenge rooms, leaderboards, or public profiles.
- Ads, attribution SDKs, remote analytics, crash-reporting SDKs, or telemetry.
- Billing, subscriptions, Play Billing, or paid cosmetic unlocks.
- AI features, remote recommendations, or uploading focus/usage data to a model.
- Background location, contacts, microphone, camera, or broad package visibility.

## 5. Review Checklist for a Future Proposal

Before implementation, a proposal should answer:

- Which user problem cannot be solved locally?
- What exact data leaves the device, at what granularity, and under which consent?
- Can the feature work offline? What happens on timeout, auth failure, or server error?
- How does the user delete remote data, and how is deletion verified?
- How are duplicate rewards, replayed sessions, and multi-device conflicts handled?
- Does the change add a permission, dependency, SDK, or manifest entry? Why is it needed?
- What is the migration and rollback plan?
- Which tests prove offline behavior, privacy boundaries, and deletion parity?
- Has the privacy document, feature-status table, and README been updated?

## 6. Phase 0.5 Recommendation

Do not add a backend in Phase 0.5. The highest-value next work is device verification and
continued local polish:

1. Run a TalkBack and large-font pass on onboarding, focus, routines, history, challenges,
   insights, and privacy screens.
2. Verify API 36 behavior on a real device or emulator, including notification permission,
   Doze/WorkManager completion, and reboot recovery.
3. Exercise CSV share-sheet behavior, migration from a real Phase 0.3 install, and
   full-data deletion on-device.
4. Continue local-only feature depth only where it can be verified without a server.

If a server-backed feature is ever proposed, treat it as a new architecture phase with its
own threat model, privacy review, migration plan, and explicit user consent design.
