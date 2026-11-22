# Forest Economy

This document describes the forest, reward, and shop economy **as implemented** in the
current codebase. It is intentionally narrow: only mechanics that exist in code are listed.
There is no premium currency, no purchasable Leaf Points, no loot box, no random reward, and
no network component.

Source of truth:

- `domain/model/FocusModels.kt` — session types and their tree species.
- `domain/model/GamificationModels.kt` — species, growth stages, forest state, shop categories.
- `domain/engine/XpLevelEngine.kt` — XP, Seed, and level rules.
- `domain/engine/ForestGrowthEngine.kt` — growth-stage and forest-level thresholds.
- `data/repository/FocusRepository.kt` — the session-completion transaction.
- `data/repository/RewardAuthority.kt` — ledger-deduplicated reward credits.
- `data/repository/ShopAndForestRepository.kt` — catalog, purchases, equipping.

## Design Principles

1. **Focus is the only source of growth.** A tree is planted only by a completed focus
   session. Breaks restore attention but never plant trees and never award XP or Seeds.
2. **Deterministic.** Reward values are pure functions of session type and completed
   minutes. Challenge rewards are fixed templates; nothing is randomized.
3. **Single authority.** Every XP, Seed, and challenge/achievement credit goes through
   `RewardAuthority` and a unique `reward_ledger.reward_key`. Retries, worker/ViewModel
   races, and duplicate completion calls cannot double-credit.
4. **No paid progress.** Leaf Points are earned only by focusing. The shop spends Leaf
   Points on cosmetics; it never sells progress or currency.

## What a Completed Session Produces

On a successful completion, `FocusRepository.recordCompletedSession` performs one Room
transaction that:

1. Persists the completed session.
2. Updates daily stats (focus minutes and session count exclude breaks).
3. Inserts exactly one `ForestTreeEntity` for non-break sessions, using the session id as
   the tree id and the session type's species.
4. Credits session XP and Seeds through the reward ledger.
5. Recalculates the streak, credits any streak milestone once, evaluates challenges, and
   credits achievements once.
6. Recomputes `totalSeeds`, `spendableLeafPoints`, `forestLevel`, and `currentStage` from
   persisted totals.

Aborted or interrupted sessions, metadata edits, and duplicate completion calls produce no
new tree, XP, or Seeds.

### Tree species by session type

| Session type | Tree species | Plantable? | Notes |
| :--- | :--- | :--- | :--- |
| Pomodoro | Scots Pine (`PINE`) | Yes | Default 25 minutes |
| Deep Work | Ancient Oak (`OAK`) | Yes | Default 50 minutes |
| Custom Focus | Silver Birch (`BIRCH`) | Yes | Default 35 minutes; user-selected length |
| Short Break | Woodland Fern (`FERN`) | No | Rest-day flora in the artwork |
| Long Break | River Willow (`WILLOW`) | No | Rest-day flora in the artwork |

Fern and willow exist as visual rest-day flora only. A break session never inserts a tree,
increments tree counts, or changes forest progress.

## Session Rewards

`XpLevelEngine.calculateSessionReward` is the only session-reward formula. `completedMinutes`
is the actually completed duration, not the planned duration.

| Session type | XP | Seeds |
| :--- | :--- | :--- |
| Pomodoro | `minutes × 2` | `max(1, floor(minutes / 10))` |
| Custom Focus | `minutes × 2` | `max(1, floor(minutes / 10))` |
| Deep Work | `minutes × 2 + 20` | `max(2, floor(minutes / 10))` |
| Short Break | `0` | `0` |
| Long Break | `0` | `0` |

Examples:

| Session | XP | Seeds |
| :--- | ---: | ---: |
| 25 min Pomodoro | 50 | 2 |
| 50 min Deep Work | 120 | 5 |
| 35 min Custom | 70 | 3 |
| 5 min Short Break | 0 | 0 |

A session with `completedMinutes <= 0` always returns `0 XP` and `0 Seeds`.

## Seeds, Growth Stages, and Forest Level

Seeds are both the lifetime growth measure and the source of spendable Leaf Points.

### Growth stages

`ForestGrowthEngine.stageForSeeds(totalSeeds)` returns the last stage whose threshold is
met. Thresholds are cumulative lifetime Seeds and are never spent.

| Stage | Label | Minimum lifetime Seeds |
| :--- | :--- | ---: |
| `SEED` | Seed | 0 |
| `SPROUT` | Sprout | 3 |
| `SAPLING` | Sapling | 10 |
| `YOUNG_TREE` | Young Tree | 25 |
| `MATURE_TREE` | Mature Tree | 55 |
| `ANCIENT_GROVE` | Ancient Grove | 100 |

`seedsToNextStage(totalSeeds)` reports the exact remaining Seeds to the next threshold, or
`null` at Ancient Grove. The Forest screen uses these values for its honest “N seeds to
X” copy and progress bar.

### Forest level

```
forestLevel = 1 + floor(max(0, totalSeeds) / 12)
```

Examples: 0–11 Seeds → Level 1; 12–23 → Level 2; 24–35 → Level 3.

Forest level is a display of cumulative growth. It is separate from the XP-based
`UserProfile.currentLevel`.

### Leaf Points

- `spendableLeafPoints` starts at 0.
- Every Seed credited by a completed focus session also credits one spendable Leaf Point.
- Challenge rewards credit their `rewardSeeds` to both lifetime Seeds and spendable Leaf
  Points.
- Purchases subtract Leaf Points only; lifetime Seeds and the growth stage never decrease.
- Achievements and streak milestones award XP only, never Seeds or Leaf Points.

Because breaks award no Seeds, they also award no Leaf Points.

## XP and Profile Levels

XP is separate from forest growth. It drives the profile level and streak milestones.

- Cumulative XP for level `L` (level 1 = 0 XP):
  `100 × (L − 1) + 25 × (L − 1) × L`.
  Level 2 = 150 XP, Level 3 = 350 XP, Level 4 = 600 XP, Level 5 = 900 XP.
- `levelForXp` uses Long arithmetic and a 10,000-level ceiling so extreme values cannot
  overflow.
- Streak milestone bonuses are one-time ledger credits: 3 days +25 XP, 7 days +50 XP,
  14 days +80 XP, 30 days +150 XP, 60 days +250 XP, 100 days +400 XP.
- Achievement bonuses range from +40 XP (First Focus) to +300 XP (Thirty-Day Woodland).
  Achievements award XP only.

XP does not affect the forest stage, forest level, or Leaf Points.

## Challenge Rewards

Challenge rewards are fixed by deterministic templates and are credited once per challenge
id through the ledger.

### Daily templates

| Challenge | Target | XP | Seeds |
| :--- | :--- | ---: | ---: |
| Single Pomodoro Rhythm | 1 Pomodoro | 35 | 2 |
| Fifty Mindful Minutes | 50 focus minutes | 50 | 3 |
| Steady Cadence | 2 focus sessions | 45 | 2 |
| Deep Canopy Block | 1 Deep Work session | 60 | 4 |
| Mindful Pause | 1 planned break | 25 | 1 |

When a usage baseline exists, the third daily slot may be replaced by “Lighter Completed
Day” (10% usage reduction, 55 XP, 3 Seeds). Planned-break challenges can be completed by
breaks, but the break session itself still awards 0 XP and 0 Seeds.

### Weekly templates

| Challenge | Target | XP | Seeds |
| :--- | :--- | ---: | ---: |
| Weekly Grove Builder | 5 focus sessions | 120 | 6 |
| 300 Reclaimed Minutes | 300 weekly focus minutes | 180 | 9 |
| Intentional Habits Week | 15% usage improvement (baseline only) | 160 | 7 |

### Custom challenges

Custom challenges use the same evaluation and ledger path. Rewards are capped at **3
rewarded custom challenges per local day**; further completions are recorded but credit no
XP or Seeds. Deleting or archiving a custom challenge preserves ledger auditability.

## Botanical Shop

The catalog is seeded locally on first run (`ensureCatalogSeeded`). Starter items are
granted at zero cost; everything else is unlocked by spending Leaf Points.

| Category | Item | Cost (Leaf Points) |
| :--- | :--- | ---: |
| Theme | Classic Forest Sage | 0 (starter) |
| Theme | Dawn Mist Meadow | 4 |
| Theme | Autumn Amber Canopy | 8 |
| Theme | Midnight Pine Sanctuary | 12 |
| Avatar | Sprouting Seedling | 0 (starter) |
| Avatar | Evergreen Pine | 3 |
| Avatar | Golden Acorn | 6 |
| Avatar | Forest Botanist | 10 |
| Avatar | Woodland Owl | 15 |
| Environment | Emerald Clearing | 0 (starter) |
| Environment | Misty Alpine Ridge | 8 |
| Environment | Golden Hour Orchard | 14 |
| Decoration | Mossy Stone Lantern | 3 |
| Decoration | Woodland Brook | 5 |
| Decoration | Botanical Wildflowers | 4 |
| Decoration | Evening Fireflies | 7 |

### Purchase and equip rules

- Buying an item requires `spendableLeafPoints >= costLeafPoints`. Insufficient balance
  leaves the balance unchanged and reports the shortfall.
- The Shop asks for confirmation before spending Leaf Points.
- Equipping an owned theme changes the app palette; equipping an owned avatar changes the
  profile emblem; equipping an owned environment changes the grove scene; decorations can
  be placed or removed individually.
- Equipping is free and never changes Leaf Points.
- `ShopAndForestRepository.equipOwnedItem` refuses unowned items (`NotOwned`) instead of
  purchasing them implicitly. The Forest screen routes locked items to the Shop.
- Progress, trees, and lifetime Seeds are never reset by buying or equipping cosmetics.

## Reward-Ledger Invariants

- Session credit key: `session:<sessionId>`.
- Challenge credit key: `challenge:<challengeId>`.
- Achievement and streak credits use their own unique keys.
- A ledger insert is “first writer wins”; a duplicate insert returns `-1` and credits
  nothing.
- Completion is transactional: session row, tree row, daily stats, forest totals, profile,
  challenges, achievements, and ledger entries commit together or not at all.
- Breaks, aborted sessions, metadata-only edits, and duplicate completions cannot mint XP,
  Seeds, Leaf Points, or trees.

## Explicit Non-Features

- No real-money purchases, subscriptions, ads, or billing integration.
- No paid or randomized Leaf Points, seeds, trees, cosmetics, or “mystery” rewards.
- No streak-loss punishment, tree withering, or currency decay.
- No social comparison, leaderboards, or cloud sync.
- No usage data is required for any forest or economy mechanic.

These boundaries are enforced by the local-only architecture documented in
[PRIVACY_AND_DATA_FLOW.md](PRIVACY_AND_DATA_FLOW.md).
