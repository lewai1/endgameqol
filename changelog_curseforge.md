## [4.1.5]

### Bug Fixes
- **Plugin crash with other mods using SLF4J** — Fixed classloader conflict (LinkageError) when another plugin (e.g. Ecotale) also bundles SLF4J. HikariCP's SLF4J is now fully shaded

### New Features
- **Mod icon** — Added 256x256 icon displayed in the mod list

### Technical
- **HyUI 0.9.5** — Updated UI library to latest version




## [4.1.4] 2026.03.26-89796e57b Release 4

### New Features
- **Snake Arcade easter egg** — Interact with an Arcade Machine block to play Snake! CRT green-phosphor aesthetic, WASD keyboard + button controls, body gradient, progressive speed, bonus golden food, high score tracking. Craftable at the Workbench (Tinkering)
- **Trinket Pouch expanded to 5 slots** — Holds 5 accessories instead of 3, allowing more build variety
- **Dragon Fire volcano spawn** — Dragon Fire now spawns in the center of the volcano containing gold instead of on the surface
- **Swamp Dungeon prefab update** — Updated Swamp Dungeon layout
- **Boss death animations** — Hedera (2.5s), Golem Void (3s), Alpha Rex (2s), Bramble Elite (2s), Zombie Aberrant (1.5s) now have death explosion particles and delayed item drops
- **Vanilla target switching for Hedera & Golem Void** — Now uses engine-native `TargetSwitchTimer` component. Hedera switches every 8-12s, Golem Void every 6-10s
- **Boss HP scales with Endless Leveling** — Boss HP scales with average player level (+5% per 10 levels above 50). Stacks with player-count scaling
- **Dungeon mob levels via Endless Leveling** — Frozen Dungeon Lv80-110, Swamp Dungeon Lv100-135, Golem Void Lv110-155
- **Plum Lightsource block** — New decorative light block with purple glow

### Bug Fixes
- **Dragon Fire memories name** — Fixed MemoriesNameOverride pointing to Dragon_Frost instead of Dragon_Fire
- **Memory leak on long-running servers** — Added periodic cleanup of invalid entity refs in DangerZoneTickSystem and VoidMarkExpirySystem
- **Dungeon blocks breakable with Silk Touch** — Silk Touch could bypass dungeon block protection. Fixed with `setDamage(0)` failsafe
- **Combo HUD crash across instances** — Fixed crash when a kill was processed on a different instance's world thread
- **Hedera attack crash near containers** — Removed unnecessary `Block_Break` reference in Hedera's Swing Right interaction

### Config Changes
- **Golem Void phase thresholds** — Phase 2/3 HP%, invulnerability duration, and minion counts now configurable
- **Frost Dragon sky bolt tuning** — Bolt/nova cooldowns and spirit spawn cap now in `/egconfig`
- **Boss damage player scaling** — +15% per extra player is now configurable
- **Hedera boss poison** — Poison damage and tick count now configurable separately from dagger poison

### Technical
- **Endless Leveling 6.9 API** — Updated bridge and dependency
- **Data-driven config migration** — 25+ hardcoded values moved to config fields for server customization
- **API migrations** — Updated `addEffect()` and `Inventory` calls to new Hytale API signatures

---

## [4.1.1]

### New Features
- **Shared boss kill credit** — All players in the instance receive achievements and bounty progress on boss kill, not just the last hitter
- **Warden Trial wave timer** — Per-tier time limit (Tier I 4m30 to Tier IV 9m). HUD countdown, last 10s in red

### Bug Fixes
- **Warden Trial mobs instantly despawning** — Increased grace period and added despawn protection for wave mobs
- **Frost Dragon Sky Bolt particle crash** — Fixed store mismatch when spawning particles in instances
- **Combo "New Best" false positive** — Personal best now properly loaded before first kill
- **Fixed some void blocks texture/hitbox**
- **Prisma Pickaxe/Hatchet breaking through claims** — 3x3 break now fires synthetic events per block, respecting claim protection

### Documentation
- **Voile docs update**


## [4.1.0]

### New Features
- **Endless Leveling deep integration** — Party XP sharing, bounty XP, gauntlet milestone XP, warden trial XP, achievement XP, and `/eg status` dashboard
- **`/eg ach` shortcut** — Quick alias for `/eg achievements`
- **Preparation for Void Dungeon**
- **Wiki improvements** — New Quick Start guide, reorganized index with categorized sections
- **Prisma salvage recipes** — All Prisma items recyclable at Salvage Bench (~50% materials back)
- **OrbisGuard integration** — Optional auto-protection for dungeon instances. Disabled by default

### Balance
- **Blazefist burn configurable** — Burn damage and duration now in `/egconfig` Weapons tab

### Config Changes
- **Integration tab** — Merged RPG Leveling + Endless Leveling into unified Integration tab

### Bug Fixes
- **Boss bar permanent freeze** — Fixed race condition when a new boss spawned right after previous one died
- **Boss bar not disappearing** — Distance check was skipped when no Golem Void boss was active
- **Prisma Sword launching enemies** — Changed knockback from additive to set velocity
- **Blazefist burning players with PvP off** — AOE burn now respects PvP setting
- **Pocket Garden not fertilizing** — Fixed Y-level check for tilled soil
- **Command permissions** — Individual permission nodes for all `/eg` subcommands
- **Prisma Pickaxe 3x3 not working on dirt** — Added soils to gather type whitelist
- **Prisma Pickaxe 3x3 always horizontal** — Fixed face detection to use look direction
- **Warden/Gauntlet wave stuck** — Auto-cleanup for glitched mobs
- **Prisma Mirage fixes** — Clones now spawn in correct world, no loot on despawn
- **Thread safety fixes** — Vorthak shop reset, Prisma mana, Combo HUD

### Technical
- **Dragon Fire NPC renamed** — `Dragon_Fire` → `Endgame_Dragon_Fire` to avoid mod conflicts
- **Async config reload** — `/egadmin reload` no longer blocks the game thread

## [4.0.6]

> ⚠️ **BREAKING CHANGE**: The Prisma Pickaxe Void Pocket (mini-inventory) has been removed and replaced by a 3x3 Area Break toggle. Existing Void Pocket contents will be lost.

### New Features
- **Prisma Pickaxe 3x3 area break** — Replaced Void Pocket with 3x3 Area Break toggle (Ability3 key). Orange glow when active

### Balance
- **Prisma Pickaxe instant mining** — Power increased to 10.0, one-shots all mineable blocks

### Bug Fixes
- **Accessories intermittently not working** — Fixed unreliable store access during ECS ticks
- **Boss damage tracking memory leak fix**
- **Achievement state stale on fast reconnect fix**
- **Player scaling broken by single bad ref** — Invalid player ref no longer aborts the entire count
- **AT_FULL_HP bounty bonus always impossible** — Now properly checks player health
- **Blazefist burn not applying damage** — Fixed to use vanilla damage calculator pattern

## [4.0.5]

### New Features
- **Onyxium salvage recipes** — All 15 Onyxium items recyclable at Salvage Bench (~50% materials back)
- **Prisma Hatchet 3x3 toggle** — Ability3 key to switch between normal and 3x3 area break

### Balance
- **Trinket Pouch crafting tier lowered** — Now Endgame Bench Tier 2 (was Tier 3)
- **Stack sizes increased** — Swamp Ingot, Hedera Gem, Swamp Gem, Crocodile Scale, Hedera's Bramble, Infused Rope (1→100), Hedera Key (1→10)

### Technical
- **Per-player ECS data persistence** — Migrated player data from JSON files to Hytale's auto-persisted ECS system. Eliminates 128KB buffer crashes on large servers. Auto-migrates legacy data
- **Updated Voile wiki**

### Bug Fixes
- **Prisma Hatchet destroying furniture** — 3x3 now only affects wood and soft blocks
- **Speed kill bounty never completing** — Timer tracking implemented
- **Golem Void kills not counting for bounties** — Fixed
- **"Defeat 5 unique bosses" counting duplicates** — Now tracks distinct boss types
- **Portal key duplication exploit** — Removed refund system
- **RPG Leveling passive mob XP** — Crocodile/Raven no longer give XP
- **Frost Dragon leaving arena** — Tightened leash to 40/50 blocks, 3-5s timer
- **Prisma Hatchet crash on big trees** — Added player validity checks

---

## [4.0.4]

[![Discord](https://img.shields.io/badge/Discord-Join_Us-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/mrCyvJmC28) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/lewai)

### New Features
- **Instance portal key refund** — Portal key returned if dungeon instance fails to load
- **Onyxium salvage recipes** — All 15 Onyxium items recyclable at Salvage Bench

### Bug Fixes
- **Vorthak shop "restocks in 739,676 days"** — Fixed timer to use game time instead of real time
- **BestiaryData.json corruption** — Fixed concurrent save race condition
- **Craft bounty detection** — Fixed recipe ID vs output item ID mismatch
- **Crafting toggle persistence** — Unified on `RecipeOverrides.json` as single source of truth
- **RPG Leveling XP broadcast** — XP now only awarded to the actual killer
- **Dragon Fire spawn crash** — Fixed empty "Kind" string error

### Balance
- **Morghul Swamp Ingot stock** — 10 → 99 per restock
- **Dragon Heart stack size** — 1 → 100

### Technical
- **BountyData corruption fix** — Synchronized concurrent saves
- **Config serialization safety** — CODEC getters return snapshots instead of live references

---

## [4.0.1-3]

[![Discord](https://img.shields.io/badge/Discord-Join_Us-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/mrCyvJmC28) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/lewai)

### Config Changes
- **Crafting toggles in GUI** — Glider, Mithril Ore, dungeon keys, portals now in Config UI Crafting tab
- **Vorthak spawn toggle** — Enable/disable in Misc tab

### Commands
- **`/bounty` → `/eg bounty`** — Moved to subcommand
- **`/gauntlet` → `/eg gauntlet`** — Moved to subcommand
- **`/eg status` version fix**

### Bug Fixes
- **RPG Leveling passive mob XP** — Dungeon mobs no longer give XP
- **Prisma armor tooltip** — Replaced missing Ice resistance with Fall resistance
- **BestiaryData/AchievementData corruption** — Thread safety fixes
- **Recipe overrides resetting on restart** — Removed `syncDefaults()` override
- **Combo HUD thread safety** — Volatile fields, proper synchronization
- **Gauntlet wave/kill counter losses** — Fixed under load
- **BountyData corruption** — Thread-safe collections
- **Ocean Striders activating on land** — Now requires full submersion
- **Ocean Striders speed persisting after pouch removal** — Retry on failed reset
- **Void Amulet debug log spam** — Removed
- **Accessory state not cleaning up on disconnect**
- Clarified dungeon-specific bounty descriptions

### New Content
- **Dragon Fire wild spawn** — Can now spawn rarely in Zone 4 Wastes

### Balance
- **Vorthak Mithril trade nerf** — Adamantite cost 10→30, stock 3→2
- **Morghul trade** — Health Potion → Greater Health Potion

### Technical
- **World thread safety** — Added `world.isAlive()` guards before all `world.execute()` calls

---

## [4.0.0]

### New Systems
- **Achievement System** — 24 achievements across 5 categories with XP rewards and item unlocks
- **Bounty Board** — Daily quests with 4 types, 5 difficulty tiers, streak bonuses, weekly challenges
- **Reputation Ranks** — 4 ranks (Novice/Veteran/Elite/Legend) with bonus multipliers
- **Enhanced Bestiary** — 32 NPC entries with kill milestones and discovery rewards
- **Combo Meter** — Kill streak tracker with 4 tiers, effects (speed, heal, crit, lifesteal), and HUD overlay
- **Trinket Pouch** — 3-slot portable container with 6 accessories:
  - **Frostwalkers** — Walk on water by freezing the surface beneath you
  - **Ocean Striders** — 2x swim speed for faster underwater exploration
  - **Void Amulet** — Cheat death once (prevents fatal damage, then consumed)
  - **Blazefist** — AOE burn effect on melee hits
  - **Pocket Garden** — HP regeneration when near farming crops + auto-fertilize nearby soil
  - **Hedera Seed** — Chance to root enemies on hit
- **The Gauntlet** — Wave survival mode with scaling difficulty and leaderboard (in development)
- **Multi-language support** — 5 languages (EN, FR, ES, PT-BR, RU)
- **Database support** — Optional SQL persistence (SQLite, MySQL, MariaDB, PostgreSQL)

### New Content
- **Frozen Dungeon** — Multi-level ice cavern with 12 enemy types, Korvyn trader, 6 chest zones, Dragon Frost boss
- **Swamp Dungeon** — Poison environment, locked doors, tiered loot, new NPCs and items
- **Morghul, Swamp Trader** — New NPC merchant
- **Hedera Autel crafting bench** — Craft Hedera Key from 5 swamp ingredients
- **Trinket Pouch recipe** — Craftable at Endgame Bench Tier 2
- **Prisma Hatchet** — Legendary hatchet with 3x3 breaking
- **Boss AI Target Switching** — Weighted random targeting every 8-10s
- **Accessory drops** — 6 accessories from bosses, dungeons, and mobs

### Commands
- **`/eg status`** — Live diagnostics
- **`/eg achievements`** — View and claim achievements
- **`/eg lang`** — Change language
- **`/bounty`** — View bounties
- **`/egadmin`** — Admin tools

### Balance
- **Endgame weapons blocked on early bosses** — Prisma/Hedera weapons deal no damage to Dragon Frost and Hedera
- **Prisma armor vulnerability** — 2x damage from Dragon Frost and Hedera
- **Boss damage player scaling** — +15% per extra player

### Config Changes
- **BossTargetSwitchEnabled/IntervalMs** — Target switching toggle and interval
- **Crafting tab overhaul** — All recipes with editable fields and search
- **Config UI beautification** — Better contrast and spacing

### Bug Fixes
- Fixed Prisma tools using wrong crafting bench
- Fixed Prisma Pickaxe Voidpocket conflicting with torch placement
- Fixed boss bar flickering
- Fixed 26+ items missing from Creative Library
- Fixed 66 items missing tooltip descriptions
- Fixed RPG Leveling tab showing raw key
- Fixed Alpha Rex Meat filename typo
- Fixed 4 root interactions missing Attack Tags
- Fixed recipe sync on mod updates
- Removed boss egg spawners
- Renamed 29 JSON files with `Endgame_` prefix
- Renamed boss NPCs to strict `Endgame_` prefix
- **Dungeon keys replaced** — Removed old shards, added Swamp/Frozen Dungeon Keys
- Instance spawn protection — bosses auto-despawn outside dungeon

### Technical
- **Thread safety hardening** — Fixed race conditions across multiple managers
