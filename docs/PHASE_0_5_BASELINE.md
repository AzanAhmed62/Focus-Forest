# Phase 0.5 Baseline — Forest Experience, Visual Identity, Onboarding & Product Polish

- **Branch:** `phase-0.5/forest-experience-and-product-polish`
- **Base commit:** `f9847b570533a6d68b4a0c379af2137f1c24dd16` (`f9847b5`)
- **Base branch:** `phase-0.4/focus-workflows-and-personalization`
- **Room schema:** version 3 (`2.json`, `3.json` exported)
- **Gradle:** 9.3.1 wrapper, AGP 9.1.1, Kotlin 2.2.10, Compose BOM 2024.09.00
- **Baseline tests:** 51 JVM/Robolectric tests, 0 failures, 0 skipped
- **Baseline lint:** 0 errors, 59 warnings
- **Baseline APK:** 21,296,457 bytes, SHA-256 `a6ea0770e22567b32965fad68ab02af6c87f3b00eac164ed82713094e8e0fcb5`

This document records the actual state before Phase 0.5 changes. It is an audit, not a plan.

## Status Key

- **COMPLETE** — implemented, coherent, and adequately covered.
- **PARTIAL** — exists and works, but has a meaningful product or engineering gap.
- **MISSING** — not implemented.
- **NEEDS_REFINEMENT** — implemented but visually, structurally, or behaviorally below the Phase 0.5 bar.

## Repository and Build Baseline

| Check | Result | Evidence |
|---|---|---|
| Worktree clean | PASS | `git status --porcelain` empty before branch creation |
| Expected base | PASS | `f9847b570533a6d68b4a0c379af2137f1c24dd16` |
| Room v3 schema | PASS | `app/schemas/.../2.json`, `3.json` |
| Gradle wrapper | PASS | Gradle 9.3.1 |
| CI coverage | PARTIAL | Workflow covers `main`, `phase-0.2/**`, `phase-0.3/**`, `phase-0.4/**`; not `phase-0.5/**` |
| Network permissions | PASS | No `INTERNET`; `ACCESS_NETWORK_STATE` explicitly removed |
| Backup | PASS | `allowBackup="false"`, cloud backup and device transfer excluded |
| FileProvider | PASS | Cache-only `exports/` path, not exported |
| Secrets | PASS | No signing secrets or API keys in source |

## Design Language and Theme

| Area | Status | Baseline |
|---|---|---|
| Overall identity | PARTIAL | Warm cream, deep forest green, botanical typography, and a Canvas forest already give the app a recognizable direction. The identity is diluted by repeated hardcoded colors, generic Material cards, and a dense Today dashboard. |
| Theme configuration | PARTIAL | `FocusForestTheme` supports system/light/dark and four purchased skins. `ColorScheme` covers primary, secondary, tertiary, surface, outline, and error. There are no semantic success/warning tokens and no dark-aware forest illustration palette. |
| Typography | COMPLETE | Fraunces display/headline, Plus Jakarta Sans body/title, JetBrains Mono for timer numerals. Hierarchy is distinct and appropriate. |
| Color tokens | PARTIAL | Palette lives in `Color.kt`, but screens repeatedly use `Color(0xFF...)`, `copy(alpha = ...)`, and ad-hoc container blends. The forest Canvas has its own fixed palette. |
| Spacing, radius, elevation, motion | MISSING | No shared tokens. Values such as 12/16/18/20/22/28 dp, 1 dp elevation, and animation durations are repeated inline. |
| Component library | PARTIAL | `BotanicalCard`, `StatPill`, `AvatarBadge`, `EmptyStateCard`, `UsageAccessDisclosureDialog`, `AchievementCelebrationDialog`, and botanical charts are reusable. Missing: section headers, consistent screen scaffolding, forest-specific cards, accessible icon-button wrapper, and a reusable confirmation pattern. |
| Adaptive layout | PARTIAL | Top-level chrome switches to a `NavigationRail` at 600 dp. Content columns cap at 600–620 dp. No window size classes; some fixed-height text containers may clip under large font scales. |

## Screen-by-Screen Audit

### Today — NEEDS_REFINEMENT

Current order: greeting/date, daily focus hero, active timer, pinned routines, active chain, first-time empty card, streak/level pills, Android usage card, weekly focus chart, recent activity, highlight challenge, forest preview.

Strengths:
- Uses persisted Room/DataStore data.
- Daily goal, weekly goal, active timer, pinned routines, recent sessions, challenge, and forest preview are all real.
- Quick start/resume is present and test-covered.
- `ON_RESUME` refreshes usage/challenges, reducing stale state.

Gaps:
- Optional Android usage is the fourth content block and visually competes with focus.
- Eight or more cards appear before the forest preview; the primary action is not the only obvious next step.
- The first-time empty card duplicates the hero call to action.
- Streak/level pills and challenge card add density without helping the first session.
- Forest preview is a passive card with no direct “start first session” empty state.
- No section headings or consistent rhythm between primary, secondary, and optional content.

### Focus — COMPLETE (preserve)

- Single-session and Pomodoro-chain modes, session types, custom duration, intention, categories, ambient sound, routines, abort confirmations, completion summary, retry path, and share summary.
- Timer state is persisted and recovered; duplicate completion is suppressed.
- Countdown exposes spoken remaining/paused semantics without a per-second live region.
- Phase 0.4 routine launch and chain behavior are integrated.
- No Phase 0.5 rewrite is justified; only shared design tokens should touch this screen.

### Forest — PARTIAL / NEEDS_REFINEMENT

Current content: header + Shop button, 230 dp Canvas, trees/Leaf Points pills, stage progress, planting timeline (first 20), species counts, all achievements.

Strengths:
- Uses the authoritative `ForestState`, `ForestTree`, `GrowthStage`, and shop ownership.
- Canvas draws five species, three environments, and four decorations.
- Empty forest message exists.
- Timeline and collection use persisted data.

Gaps:
- The forest is a card plus a list, not a place. Environments and decorations are only editable in the Shop.
- Tree drawing ignores growth stage entirely; a mature forest looks the same as a new one.
- Infinite sway animation runs continuously and is not reduced-motion aware.
- Only the first 14 trees are drawn and the first 20 are listed, with no explanation of the cap.
- Tree placement is index-based and recomputed on every composition; there is no stable per-tree variation.
- No per-tree visual distinction beyond species.
- No empty-state route to start a focus session.
- Achievements dominate the bottom of the screen as a long list.
- No accessible text summary of the visual forest.
- No connection from forest milestones to session history.

### Shop — PARTIAL

- Four categories, filters, Leaf Point balance, owned/equipped state, purchase and equip actions, insufficient-points feedback.
- Generic card layout with a colored circle; environments, decorations, themes, and avatars have no meaningful preview.
- Locked items show only a price, with no explanation of how Leaf Points are earned on the item itself.
- No purchase confirmation, no empty-filter state, no sorting by equipped/owned.
- Equip/remove semantics for decorations are not explicit enough (“Remove” is ambiguous).
- No duplicate ownership is possible; repository transaction prevents negative balance.

### Tree Catalog — PARTIAL

- Five species exist: Scots Pine, Ancient Oak, Silver Birch, River Willow, Woodland Fern.
- Species are assigned deterministically by session type (`POMODORO → PINE`, `DEEP_WORK → OAK`, `CUSTOM → BIRCH`, breaks → FERN/WILLOW but breaks do not plant trees).
- The UI shows counts and scientific hints, but no visual tree preview and no explanation of how each species is planted.
- No rarity system exists and none should be invented.

### Environments — PARTIAL

- Three environments: Emerald Clearing (free), Misty Alpine Ridge (8 Leaf Points), Golden Hour Orchard (14).
- Switching environments persists in `ForestProgressEntity.equippedEnvironmentId` and never deletes trees.
- No preview or selection in the Forest screen; only the Shop.
- Canvas palettes are fixed light colors and do not adapt to dark theme.

### Decorations — PARTIAL

- Four decorations: Mossy Stone Lantern (3), Woodland Brook (5), Botanical Wildflowers (4), Evening Fireflies (7).
- Multiple decorations can be equipped; `equippedDecorationsCsv` persists the set.
- Only the Shop exposes them; no forest-side preview or toggle.
- Fireflies animate from the same infinite transition as the sway.

### Forest History — PARTIAL

- A planting timeline lists recent trees with date and focus minutes.
- History screen has full session filtering and reflection editing.
- No link from the forest timeline to history, and no milestone explanation connecting focused sessions to forest growth.

### Achievements — COMPLETE

- Nine achievements with bonus XP, ledger-backed idempotent claims, progress labels, and celebration dialog.
- No fake rarity or random rewards.

### XP / Seed Economy — COMPLETE (documented in `docs/FOREST_ECONOMY.md`)

- `RewardAuthority` and `RewardLedgerEntity` are the single source of truth.
- Session rewards, streak milestones, challenge rewards, and achievement bonuses are deduplicated by reward key.
- Breaks grant 0 XP and 0 seeds.
- Metadata edits do not grant rewards.
- Leaf Points are spendable seeds; no paid currency, loot boxes, or gambling.
- No rebalancing is planned for Phase 0.5.

### Challenges — COMPLETE

- Daily, weekly, and custom challenges; validation; edit/delete/archive; anti-farming cap for custom challenge rewards; local history.
- Challenge rewards flow through `RewardAuthority`.

### Insights — COMPLETE

- Focus and usage tabs, 7/30/90-day filters, calendar selection, category/routine breakdowns, sample counts, usage permission states, and local-only disclosure.
- No Phase 0.5 rewrite required beyond shared visual tokens.

### Session History — COMPLETE

- Bounded, searchable, filterable history with date range, category, routine, status, detail dialog, notes, and reflections.
- Snapshot fields preserve routine/category names after deletion.
- Notes/reflections never affect rewards.

### Routines — COMPLETE

- Create/edit/delete/archive/restore routines and categories.
- Pinned routines on Today, saved routines on Focus, one-tap start from multiple surfaces.
- Deletion preserves historical snapshots.

### Onboarding — PARTIAL

Current flow: four pages (welcome, name/daily goal/intention, usage access, summary), then finish.

Strengths:
- No account, no internet, no notification permission, and Usage Access is optional and skippable.
- Completion is persisted in DataStore; catalog and starter routines are seeded before completion.
- Usage disclosure is explicit and local-only.

Gaps:
- No overall skip action.
- No default focus duration/type choice, despite Settings later needing one.
- Usage Access is presented during onboarding, which lengthens the first-run path.
- Page/name/goal/intention state is held only in the ViewModel; process recreation loses the in-progress page.
- Four pages and a long privacy card delay the first session.
- The welcome page uses a large raster image but the unused `img_forest_meadow.jpg` adds 672 KB to the APK.

### Settings — PARTIAL / NEEDS_REFINEMENT

Current content: profile/name/daily goal/weekly goal/usage budget, appearance theme chips, notification switches, privacy link, CSV export, “Plus planned” roadmap card, delete-all button.

Gaps:
- One ungrouped scroll; no appearance/focus/goals/audio/notifications/privacy/about sections.
- No default focus session/duration controls.
- Ambient sound preferences exist in DataStore and Focus but are not exposed in Settings.
- “Focus Forest Plus (Planned)” describes cloud sync and billing, which conflicts with the local-first release and is not implemented.
- No app version/about information.
- Save button groups name and three goals together; partial saves are not obvious.
- Delete-all confirmation exists and is appropriately destructive.

### Permissions and Privacy — COMPLETE (preserve)

- `PACKAGE_USAGE_STATS` optional and user-initiated; disclosure before system settings.
- `POST_NOTIFICATIONS` optional; no repeated prompts; timer works without it.
- No claim that the app blocks distractions.
- No `INTERNET` or `ACCESS_NETWORK_STATE`.
- Backup and device transfer excluded.
- Local reset clears Room, DataStore, and exports.

### Navigation — COMPLETE / minor refinement

- Five top-level destinations: Today, Focus, Challenges, Insights, Forest.
- Secondary destinations: Routines, History, Shop, Settings, Privacy.
- Bottom bar on compact widths, rail at ≥600 dp, state save/restore, single-top navigation.
- Active timer lives in DataStore, not navigation state, so it survives navigation.
- Forest cannot currently open History or start a focus session directly; those callbacks should be added without adding destinations.

### Empty, Loading, and Error States — PARTIAL

- Empty states exist on Today, Forest, History, Insights, Challenges, and Routines.
- Today has an explicit loading indicator; other screens use initial empty state instead of a loading state.
- Focus has retry/error handling for failed completion; usage has query errors.
- Empty forest lacks a primary action.
- Empty/filter-empty shop has no dedicated state.

### Accessibility — PARTIAL

- Timer countdown has explicit spoken remaining/paused semantics and no per-second live region.
- Most icon-only controls have content descriptions.
- Progress indicators expose Material progress semantics.
- Missing: heading semantics, forest non-visual summary, semantics tests, consistent 48 dp minimum interactive targets, and large-font review of fixed-height rows.
- Physical TalkBack verification is not claimed.

### Illustrations and Assets — PARTIAL

| Asset | Size | Use |
|---|---|---|
| `img_onboarding_forest.jpg` | 756 KB | Onboarding hero |
| `img_forest_meadow.jpg` | 672 KB | **Unused** |
| `ic_launcher_art.jpg` | 284 KB | Adaptive launcher foreground |
| `fraunces.ttf` | 36 KB | Display/headline |
| `plus_jakarta_sans.ttf` | 32 KB | Body/title |
| `jetbrains_mono.ttf` | 60 KB | Timer numerals |

- The two forest JPGs are polished botanical illustrations, but no provenance/license record exists in the repository.
- The Canvas forest is original code and is the primary illustration system.
- No remote images or image libraries are used.
- Unused raster assets should be removed or deliberately used; new assets should remain vector/Canvas-first.

### Compose Performance — PARTIAL

- Forest Canvas runs an infinite transition for sway and fireflies.
- Tree layout is recalculated from index on every draw and has no stable per-tree variation.
- `TodayViewModel` combines 12 flows; acceptable but should not grow further.
- Lazy lists use stable keys in the main collections.
- No network, image loading, or unbounded background work.
- Startup seeds the catalog and refreshes usage/challenges; this is bounded but should remain so.

### UI Tests — PARTIAL

- One 227-line end-to-end Robolectric journey covers onboarding, Focus, routines, chain mode, ambient audio, challenges, Insights, Shop, History, Settings, and privacy.
- No dedicated forest rendering/state tests, no onboarding skip/process-recreation test, no settings audio/default-duration test, no accessibility semantics test, and no forest empty-state test.
- Phase 0.4 regression coverage is strong across repository, migration, reward ledger, timer, and context workflows.

## Product Decisions for Phase 0.5

1. Keep the existing five species, six growth stages, four categories, three environments, four decorations, four themes, and five avatars. Do not invent rarity, currency, or species.
2. Keep Room v3 and the existing reward ledger. No schema change is planned.
3. Keep the existing Fraunces / Plus Jakarta Sans / JetBrains Mono identity and warm cream/sage palette. Refine tokens and components rather than replace them.
4. Make the forest canvas the product centerpiece: deterministic composition, stage-aware tree scale, stable per-tree variation, environment-aware palettes, no infinite idle animation by default.
5. Make onboarding three short pages with a skip action, default focus length, optional name/goal, and process-recreation-safe state. Move Usage Access education out of the mandatory path.
6. Group Settings into appearance, focus, goals, audio, notifications, privacy/data, and about.
7. Keep Today’s primary action above the fold and demote optional usage to the bottom.
8. Add behavior-focused tests for forest economy, onboarding persistence, forest UI, and accessibility semantics; do not chase test count.
9. Add `phase-0.5/**` to CI triggers.
10. Preserve Phase 0.4 timer, history, CSV, migration, wipe, and reward behavior.

## Known Constraints

- Physical-device and multi-form-factor QA is deferred to Phase 0.6.
- No physical TalkBack or large-font verification is claimed.
- Remote CI will not be run unless the branch is pushed; this phase does not push.
- The Phase 0.4 `FocusContextWorkflowTest` `runBlocking(Dispatchers.IO)` workaround must be audited but not “fixed” by moving production Android main-thread work to IO.
