# Changelog - Version 4.2.0

## New Features

### Temporal Portal System
Random portals spawn near players every 5-10 minutes (configurable). Walk into the portal to enter a temporary dungeon instance. A return portal inside the dungeon sends you back automatically. Portals auto-despawn after 3 minutes and respect OrbisGuard/SimpleClaims protected zones.

**Admin command:** `/eg admin portal <frozen|swamp>`

### Pet Companion System
4 boss pets unlocked by killing bosses (10-15% chance per kill):

| Pet | Source Boss | Special |
|-----|-----------|---------|
| Dragon Frost | Dragon Frost | Alternates fly/walk |
| Dragon Fire | Dragon Fire | Fast ground companion |
| Golem Void | Golem Void | Slow, intimidating |
| Hedera | Hedera | Nature companion |

Pets follow the player, teleport if too far, and chase the owner's attack target. Configurable in `/eg config`.

**Command:** `/eg pet`
**API for modders:** `PetAPI.getPetOwner()`, `PetAPI.getPetDamage()`, `PetAPI.getPetKill()`

### Void Realm
New dimension with a floating island arena surrounded by smaller islands. Home of the Golem Void boss. Accessible via portal key.

### Dragon Frost rework
Fly/walk boss with aerial frost bolt attacks, controller transitions, stuck recovery, and timed phase system (15-20s air, 10-15s ground cycle).

### Hedera Rework
Three new attacks added to Hedera:
- **Vine Grab** (Phase 2+) - Pulls the player toward Hedera
- **Ground Slam** (Phase 2+) - AOE 7 blocks + camera shake
- **Charge** (Phase 3 only) - Forward lunge, 80 Physical damage

Lingering **Poison Clouds** spawn every 18-25s during Phase 2+, last 6 seconds, 5-block radius.

### UI Overhaul
- **Native Journal Page** - Unified Bounty Board, Bestiary, and Achievements into a single page with 3 tabs
- **Custom Trade UI** - All 3 merchants use a custom interface with item icons, affordability coloring, and stock display
- **Combo Meter** - Migrated to native UI, positioned lower to avoid overlap
- **Native Config UI** - Rewritten with 7 tabs, dark theme, global search bar, recipe editor

### Other
- **Endgame Memories Category** - Custom "Endgame" tab in the Bench Memories with dedicated icon. 19 NPCs grouped under one category
- **Pet toggle in /eg config** - Enable/disable the pet system from the Misc tab
- **Integration tab rework** - Auto-detection status per mod (DETECTED/NOT FOUND), addons section for future extensions
- **Integration auto-disable** - Mods auto-disable when their JAR is removed (RPG Leveling, Endless Leveling, OrbisGuard)
- **SimpleClaims compatibility** - 3x3 area break and portal spawning respect claimed chunks
- **Item ID Migration** - Automatic item ID conversion on player connect and chunk load
- **Unified commands** - All commands now under `/eg`: config, admin, pet, journal, gauntlet

---

## Bug Fixes
- **DeathAnimationTime crash** - Mods overriding Template_Intelligent.json could strip engine parameters. Fixed with custom templates per boss
- **Frostwalker destroying waterlogged blocks** - Ice no longer replaces stairs, slabs, or other solid blocks in water
- **Boss registration race condition** - Fixed in GolemVoidBossManager and GenericBossManager
- **Dragon Fire not tracked by boss system** - Added missing config for Dragon Fire and Zombie Aberrant
- **Hedera Summon not spawning spirits** - Spawn marker delay and trigger range fixed
- **Boss friendly fire** - Bosses no longer damage their own minions
- **Hedera/Spirit_Root attacking each other** - Fixed with shared attitude group
- **Boss kill sound** - Now only plays for the killer, not all players
- **Dragon Fire burns in lava** - Added fire immunity
- **OrbisGuard blocks chests in dungeons** - Added access flags on dungeon regions
- **AccessoryAttackSystem crash** - Added ref validity check on burn apply
- **Prisma 3x3 drops lost** - Blocks now drop as world items when inventory is full
- **Endless Leveling level override** - Dungeon mob levels handled natively
- **Boss XP cap** - Increased from 100k to 1M
- **Empty "beast" Memories category** - Grooble reclassified from "Beast" to "Predator"
- **Bestiary drops outdated** - Updated all drop lists to match actual drop tables

---

## Balance
- **Hedera Phase 2** (66-33% HP) - Significantly harder with new attacks + poison clouds + spirits
- **Hedera Phase 3** (<33% HP) - Charge attack added
- **Alpha Rex Leather** - Renamed from Big_Rex_Cave_Leather (auto-migrated)

---

## Config Changes
- Native Config UI with 7 tabs and dark theme
- Global search bar across all settings
- Recipe Override Editor with category filters and pagination
- Numeric settings support direct text input
- RPG Leveling and Endless Leveling auto-detected

---

## Technical
- NPC assets reorganized into semantic directories
- NPC IDs uniformized with `Endgame_` prefix
- Full HyUI to native .ui migration (HyUI only for boss bar overlays)
- Custom NPC actions: SetMotionController, EnterTemporalPortal, OpenTradeUI
- Dependencies: RPGLeveling 0.3.4, EndlessLeveling 7.3.4
- Hygradle build system with hot-reload
