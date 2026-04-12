---
title: Items & Weapons
description: All weapons, tools, consumables, boss materials, and crafting benches.
order: 7
published: true
---

# Items & Weapons

40+ weapons, 8 tools, consumables, boss materials, and crafting benches.

> [!TIP]
> **Creative Library:** All items are available in Creative Mode under the **Endgame** category (7 subcategories: Weapons, Armor, Tools, Materials, Consumables, Portals, Misc).

## Special Weapons

Legendary weapons with unique signature abilities, passives, and special effects.

### Prisma Sword

*Legendary*

The pinnacle sword. Heavy melee with two prismatic active abilities: a long-range AOE projectile (**Prismatic Beam**) and a devastating ground slam ultimate (**Prismatic Judgment**).

| Attack slot   | Ability                                      | Cost           |
|---------------|----------------------------------------------|----------------|
| Primary       | Sword swings + thrust (vanilla chain)        | Stamina        |
| Secondary     | Guard / block (vanilla)                      | Stamina        |
| Ability1 (SIG)| **Prismatic Judgment** — ground slam         | 100 SE         |
| Ability3      | **Prismatic Beam** — ranged projectile       | 80 Mana        |

**Base melee damage:** Swing 38–42 Physical, Swing Down 72, Thrust 105.

#### Prismatic Beam (Ability3 — 80 Mana)
Fires a fast prismatic projectile forward (~20 blocks range). On impact, triggers an AOE explosion in a 3-block radius. Hits the primary target plus any clustered enemies nearby.
- **Damage:** 100 Physical direct + AOE
- **Identity:** Ranged poke — complements the sword's melee kit.

#### Prismatic Judgment (Signature — 100 Signature Energy)
Raises the sword, then slams the ground in a massive 10-block radius AOE burst. Single devastating hit with crowd control.
- **Damage:** 250 Physical single hit
- **Knockup:** Force 22 straight up
- **Debuff:** Applies **Prisma Shatter** (60% slow, 4s) on all hit targets
- **Identity:** Melee nuke — the sword's ultimate, clears adds in a single press.

### Prisma Daggers

*Legendary*

Assassin-class daggers. Fast multi-hit combat with a directional mobility skill (**Prisma Dash**) and a 360° omnidirectional burst ultimate (**Razor Storm**).

| Attack slot   | Ability                                      | Cost           |
|---------------|----------------------------------------------|----------------|
| Primary       | Dagger swings + stabs + pounce (vanilla chain) | Stamina      |
| Secondary     | Guard / block (vanilla)                      | Stamina        |
| Ability1 (SIG)| **Razor Storm** — 3 chained AOE bursts       | 100 SE         |
| Ability3      | **Prisma Dash** — lunge forward              | 60 Mana        |

**Base melee damage:** Swing 15–19, Stab 48–62 (head crit 62–72), Pounce Sweep 125, Pounce Stab 152 (head 198). Backstab (180° angle) adds ~50% bonus on all primary hits.

#### Prisma Dash (Ability3 — 60 Mana)
Player lunges 10 blocks straight forward via an `ApplyForce` impulse. A Stab selector hits every entity along the dash path. Leaves a prismatic particle trail.
- **Damage:** 80 Physical per entity hit in the line
- **Knockback:** Force 8, direction Y+1 / Z−2
- **Identity:** Directional mobility skill — gap closer, escape tool, multi-hit sweep.

#### Razor Storm (Signature — 100 Signature Energy)
Short charge (0.4s), then releases 3 chained AOECircle bursts around the player, spaced 0.2s apart (total duration ~0.6s). Each burst hits everything within 5 blocks.
- **Damage:** 80 Physical × 3 bursts = **240 total**
- **Knockback:** Radial (Type: Point) — pushes enemies outward. Last burst has increased force (15).
- **Identity:** Omnidirectional AOE burst — complements Dash's line damage with a circular cleave.

### Hedera Daggers

*Legendary*

Nature-themed daggers. Every hit applies **Poison** (5 dmg per tick, 4 ticks = 20 total) and **Lifesteal** (8% of damage dealt heals the wielder).

- **On-Hit — Poison:** 5 dmg/tick x 4 = 20 total
- **Passive — Lifesteal:** 8% of damage dealt

### Frostbite Blade

*Epic*

Ice weapon obtained from the Frozen Dungeon. Purchase from **Korvyn** for 45 Flocons. Every hit applies **Frost Chill** (50% slow, 5s). Signature finisher applies **Frost Frozen** (full freeze, 3s).

- **On-Hit Effect — Frost Chill:** 50% slow for 5s
- **Signature — Frost Frozen:** Full freeze for 3s
- **Source — Korvyn Trader:** 45 Flocons (Frozen Dungeon)

## Standard Weapons

Tiered weapon progression from early-game to endgame materials. All crafted at the Weapon Bench or Endgame Bench.

| Type       | Materials Available                                                                     |
|------------|-----------------------------------------------------------------------------------------|
| Sword      | Bone Frost, Mithril, Onyxium, Frozen, Prisma                                           |
| Daggers    | Mithril, Onyxium, Hedera, Prisma                                                       |
| Longsword  | Copper, Iron, Thorium, Cobalt, Adamantite, Mithril, Onyxium (7 tiers)                  |
| Spear      | Crude, Copper, Iron, Thorium, Cobalt, Adamantite, Mithril, Onyxium (8 tiers)           |
| Staff      | Copper, Iron, Thorium, Cobalt, Adamantite, Mithril, Onyxium + Crystal Ice, Crystal Flame (9 total) |
| Shortbow   | Mithril, Onyxium                                                                       |
| Shield     | Mithril, Onyxium                                                                       |
| Battleaxe  | Mithril, Onyxium                                                                       |
| Mace       | Onyxium                                                                                 |

## Tools

8 tools spanning pickaxes, hatchets, and shovels.

### Prisma Pickaxe

3x3 area break (toggle via `Ability3`). Instant mining on all rocks and ores. The ultimate mining tool for endgame resource gathering.

**Note:** Void Pocket was removed in v4.0.6. This is a pure mining tool.

### Prisma Hatchet

3x3 area break for wood and soft blocks. Does **not** break benches or furniture — only natural blocks like logs, leaves, and soil.

### Other Tools

- **Mithril Pickaxe** — Mid-tier pickaxe
- **Onyxium Pickaxe** — High-tier pickaxe
- **Onyxium Hatchet** — High-tier hatchet
- **Iron Shovel** — Early-tier shovel
- **Thorium Shovel** — Mid-tier shovel
- **Cobalt Shovel** — Mid-tier shovel

## Consumables

Mana potions in three sizes, each with an instant and regen variant.

| Potion                    | Type                |
|---------------------------|---------------------|
| Small Mana Potion         | Instant mana restore |
| Medium Mana Potion        | Instant mana restore |
| Large Mana Potion         | Instant mana restore |
| Small Mana Regen Potion   | Mana regen over time |
| Medium Mana Regen Potion  | Mana regen over time |
| Large Mana Regen Potion   | Mana regen over time |

## Crafting Benches

Four specialized benches for endgame crafting and salvaging.

### Endgame Bench

Central crafting station with 3 upgrade tiers. Unlocks Onyxium armor, Prisma armor, portals, challenge items, and accessories.

*Crafted at Workbench (2 Thorium Bars + 10 Wood + 5 Rock)*

### Weapon Bench

Dedicated weapon crafting. Handles swords, daggers, longswords, spears, staffs, bows, maces, and battleaxes across all material tiers.

*Crafted at Workbench (2 Copper Bars + 10 Wood + 5 Rock)*

### Hedera Autel

Swamp-themed crafting station. The only bench that can craft the Hedera Key to unlock the Swamp Dungeon.

*Found in the swamp biome*

### Salvage Bench

Break down unwanted gear to recover materials. Prisma and Onyxium items return approximately 50% of their crafting cost.

*Reclaim resources from obsolete equipment*

## Salvage System

Use the **Salvage Bench** to break down **Prisma** and **Onyxium** items. You will recover approximately **50%** of the original crafting materials. This is the best way to recycle gear as you upgrade through material tiers.
