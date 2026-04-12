# Changelog - Version 4.2.0

## New Features

### Temporal Portal System
Random portals spawn near players every 5-10 minutes (configurable, 80-300 blocks range). Particle-only portals (no block placement). Walk in to enter a temporary dungeon instance. Return portal inside sends you back. Warnings at 5min and 1min before expiration, 30s grace period. Lifetime status (STABLE > DESTABILIZING > CRITICAL > COLLAPSING). Respects OrbisGuard/SimpleClaims protected zones.

**3 temporal instances:** Eldergrove Hollow, Oakwood Refuge, Canopy Shrine. Data-driven dungeon system: per-dungeon enable, portal duration, instance time limit, respawn toggle.

### Pet Companion System
4 boss pets (Dragon Frost, Dragon Fire, Golem Void, Hedera) unlocked by killing bosses (10-15% chance). 6-tier progression (D → SS) via item feeding. Each tier scales damage (1.0x → 2.5x), health, and visual size. Mount system at Tier C+. Passive perks at S, aura at SS. 30s respawn cooldown. EndlessLeveling bridge for cross-mod stat access.

**Command:** `/eg pet`

### Void Realm
New dimension with a floating island arena. Home of the Golem Void boss. Replaces the old Shard of the Void portal key.

### Boss Reworks
- **Dragon Frost** — Fly/walk boss with aerial frost bolt attacks, timed phase cycling (15-20s air, 10-15s ground), controller transitions, stuck recovery
- **Hedera** — 3 new attacks: Vine Grab (pull, Phase 2+), Ground Slam (AOE 7 blocks + camera shake, Phase 2+), Charge (lunge, Phase 3). Lingering poison clouds every 18-25s during Phase 2+

### UI Overhaul
- **Native Journal Page** — Unified Bounty Board, Bestiary, Achievements with 3 tabs
- **Custom Trade UI** — Item icons, affordability coloring, stock display for all merchants
- **Combo Meter** — Migrated to native CustomUIHud
- **Native Config UI** — 7 tabs, dark theme, global search bar, recipe editor

### Other
- **WaveArena framework** — Warden Trials migrated onto a generic data-driven wave engine. Configurable wave compositions, mob pools, scaling, rewards, zone particles, instance blacklist. Public API for external mods
- **Warden Trials blocked in instances** — Warden Challenge items can no longer be used inside dungeon instances
- **Spear pickup after throw** — MC Trident style: thrown spears drop at landing point, 100% return
- **Endgame Memories** — Custom tab in Bench Memories with dedicated icon, 19 NPCs
- **Item ID Migration** — Automatic item conversion on connect/chunk load. Currently migrates: `Big_Rex_Cave_Leather` → `Alpha_Rex_Leather`, `Endgame_Portal_Golem_Void` → `Endgame_Portal_Void_Realm`
- **Integration auto-detection** — RPG Leveling, Endless Leveling, OrbisGuard auto-enable/disable

---

## Balance

### Weapon Reworks
- **Prisma Daggers rework** — All previous abilities removed (Vanish, Blink stance, Void Mark, execution bonus). Replaced with: Signature **Razor Storm** (100 SE, 3 AOE bursts = 240 dmg), Ability3 **Prisma Dash** (60 Mana, 10-block lunge, 80 dmg per entity)
- **Prisma Sword rework** — All previous abilities removed (Mirage clones, mana cost system). Replaced with: Signature **Prismatic Judgment** (100 SE, 10-block AOE slam, 250 dmg + knockup + slow), Ability3 **Prismatic Beam** (80 Mana, 20-block projectile + AOE explosion)
- **Frostbite Blade** — Signature: **Blizzard Stance** (100 SE, 3 mobile AOE pulses = 240 Ice dmg, final pulse freezes). Ability3: **Ice Field** (50 Mana, 3 slow pulses, pure CC)

### Boss Difficulty
- **Hedera Phase 2** (66-33% HP) — Significantly harder with new attacks + poison clouds + spirits
- **Hedera Phase 3** (<33% HP) — Charge attack added (80 Physical)

### Other
- **Accessory tooltips** — `Accessory — store in trinket pouch` marker on all 6 accessories (5 locales)
- **Alpha Rex Leather** — Renamed from Big_Rex_Cave_Leather (auto-migrated)

---

## Removed
- **Gauntlet system** — Infinite-wave arena removed (was disabled by default). Will be replaced by a new wave system integrated into temporal portal instances

---

## Config Changes
- Native Config UI with 7 tabs
- Unified commands under `/eg`: config, admin, pet, journal
- Global search bar, recipe editor, editable value fields
- Respawn Inside Instance toggle (disabled by default)
- LuckPerms compatible (default-allow player commands)

---

## Bug Fixes
- DeathAnimationTime crash — custom templates per boss
- Frostwalker waterlogged blocks — ice no longer replaces solid blocks
- Boss registration race condition — fixed in GolemVoid/GenericBossManager
- Boss friendly fire — bosses no longer damage own minions
- Boss kill sound — only plays for the killer
- Dragon Fire lava immunity — added
- OrbisGuard dungeon chests — access flags on regions
- Prisma 3x3 drops — blocks drop as world items
- Hedera camera shake — invalid CameraEffect ID fixed
- Boss XP cap — raised from 100k to 1M
- Bestiary drops — all boss/elite drop lists updated
- Trinket Pouch only worked in hotbar — accessories now work from any inventory slot
- RPGLeveling config override — no longer overwrites RPGLeveling's config (uses Config Defaults Override API)
- Dragon Fire melee — fixed Dragon Fire not attacking in melee

---

## Technical
- NPC assets reorganized into semantic directories with Endgame_ prefix
- Full HyUI to native .ui migration (HyUI only for boss bars)
- SimpleClaims compatibility for Prisma 3x3 + portal spawning
- Dependencies: EndlessLeveling 7.7.4
