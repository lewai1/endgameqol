---
name: Bosses & Elites
description: All boss encounters, elite mobs, and enrage system
author: Lewai
---

# <gradient data="#ff5555:#ff6600:#d16eff">Bosses & Elites</gradient>

Endgame & QoL adds three major boss encounters and several elite mobs. All have configurable HP, damage, player scaling, enrage thresholds, and XP rewards via <#55ffff>/egconfig</#55ffff>.

---

## <#aaaaaa>Boss Overview</#aaaaaa>

| Boss | HP | XP |
|:-----|---:|---:|
| <#55ffff>Dragon Frost</#55ffff> | 1400 | 700 |
| <#55ff55>Hedera</#55ff55> | 1800 | 900 |
| <#d16eff>Golem Void</#d16eff> | 3500 | 3500 |

All three bosses scale <#ff6600>+50% HP</#ff6600> per additional player.

---

## <#d16eff>Boss Target Switching</#d16eff>

In multiplayer, bosses intelligently switch targets between players:

| Strategy | Chance | Description |
|:---------|:------:|:------------|
| Nearest Player | 40% | Targets the closest player |
| Highest Damage | 40% | Targets the top damage dealer |
| Random | 20% | Picks a random nearby player |

Evaluated every 8-10 seconds (configurable: 2000-30000ms in <#55ffff>/egconfig</#55ffff> Misc tab).

---

## <#ff6600>Boss Damage Scaling</#ff6600>

When multiple players are fighting a boss, the boss deals more damage to compensate:

- **+15% damage** per additional player in the same world
- 1 player = normal damage, 2 players = 1.15x, 3 = 1.30x, 4 = 1.45x, etc.
- Stacks with difficulty multiplier and enrage bonus

---

!! Players wearing any <#d16eff>Prisma armor</#d16eff> piece take <#ff5555>2x damage</#ff5555> from <#55ffff>Dragon Frost</#55ffff> and <#55ff55>Hedera</#55ff55>. This prevents using endgame armor to trivialize earlier bosses.

---

## <#ff5555>Boss Enrage System</#ff5555>

All three main bosses can enter an <#ff5555>enraged state</#ff5555> when taking heavy burst damage.

| Parameter | Value |
|:----------|:------|
| Trigger | 200+ damage in 5 seconds |
| Effect | 1.5x damage for 8 seconds |
| Cooldown | 15 seconds |

! All thresholds are configurable per-boss in <#55ffff>/egconfig</#55ffff> (Combat > Bosses > Enrage System).

---

## <#55ffff>Dragon Frost</#55ffff>

**HP** — 1400 (scales +50% per extra player)
**XP Reward** — 700
**Location** — Frozen Dungeon (accessed via <#55ffff>Frozen Dungeon Key</#55ffff>)

A massive frost dragon with ice-based attacks and a 3-phase fight.

**Phase 1 — Frozen Calm** (100-70% HP)
Basic melee bites and claw swipes. 27 Physical + 10 Ice damage, moderate knockback.

**Phase 2 — Ice Storm** (70-40% HP)
Gains a sky bolt barrage: ice projectiles rain from above onto nearby players. Chase speed increases.

**Phase 3 — Blizzard Fury** (40-0% HP)
Frost Nova ring pulses outward. Summons up to 4 <#55ffff>Spirit Frost</#55ffff> minions. Most dangerous phase.

**Drops** — <#aaaaaa>Dragon Heart</#aaaaaa> (guaranteed), <#aaaaaa>Mithril Bar</#aaaaaa> (4-5), <#55ffff>Frostbite Blade</#55ffff> (2% rare). Plus one of: Storm Hide (40%), Ice Essence (35%), Sapphire (25%).

---

## <#55ff55>Hedera</#55ff55>

**HP** — 1800 (scales +50% per extra player)
**XP Reward** — 900
**Location** — Swamp Dungeon (accessed via <#55ff55>Swamp Dungeon Key</#55ff55>)

A void-corrupted nature boss using poison and root attacks with intelligent combat AI.

**Phase 1 — Nature's Wrath** (100-67% HP)
Melee strikes with poison application. Root traps sprout around the arena (5-block radius).

**Phase 2 — Toxic Bloom** (67-34% HP)
Poison damage intensifies. Root AOE expands to 7-block radius. Aggressive strafe behavior.

**Phase 3 — Death Blossom** (34-0% HP)
Root AOE reaches 9 blocks. Extremely aggressive with minimal cooldowns. 3s invulnerability on each phase transition.

**Drops** — <#aaaaaa>Essence of the Forest</#aaaaaa> (10-15), <#aaaaaa>Onyxium Bar</#aaaaaa> (3-7), <#55ff55>Hedera Gem</#55ff55> (guaranteed), <#aaaaaa>Void Essence</#aaaaaa> (10-20), <#aaaaaa>Voidheart</#aaaaaa> (guaranteed).

**Weakness** — Takes 30% bonus damage from the <#55ffff>Frostbite Blade</#55ffff>.

---

## <#d16eff>Golem Void</#d16eff>

**HP** — 3500 (scales +50% per extra player)
**XP Reward** — 3500
**Location** — Void Realm (accessed via <#d16eff>Shard of the Void</#d16eff>)

The hardest boss in the mod. A massive void golem with 13 unique attacks and a 3-phase minion-spawning fight.

**Phase 1** (100-67% HP)
Full attack repertoire: Slam Left/Right, Grind (360 spin), Rumble (ground shockwave), Pound, Throws (single and double boulder). <#d16eff>Void runic circle</#d16eff> telegraphs appear on the ground before heavy attacks.

**Phase 2** (67-34% HP)
Spawns 2 <#d16eff>Eye Void</#d16eff> minions (250 HP each). 3s invulnerability during transition. All attacks continue.

**Phase 3** (34-0% HP)
Spawns 4 <#d16eff>Eye Void</#d16eff> minions. Danger zone activates (configurable start phase). Maximum aggression.

**All Attacks** — Swing Left/Right, Swing Charge, Slam Left/Right, Grind, Rumble, Ground Slam, Pound, Pound Double, Throw, Throw Double, Clap. Larger telegraph circle = larger AOE.

**Drops** — <#aaaaaa>Onyxium Bar</#aaaaaa> (6-10), <#d16eff>Prisma Bar</#d16eff> (3-7), <#aaaaaa>Emerald</#aaaaaa> (1-3, 50%), <#aaaaaa>Shard of the Radiant Light</#aaaaaa> (guaranteed).

---

## <#ff6600>Elite Mobs</#ff6600>

Elites are powerful creatures found in the open world. They have more HP than normal mobs but do not scale with player count.

**<#ff5555>Alpha Rex</#ff5555>** — 700 HP, 80 Physical damage. Fast aggressive predator with heavy knockback. Drops Apex Sovereign Leather (2-3) and Alpha Trex Meat (3-6). Added to <#55ff55>Zone 4 Jungles</#55ff55> spawns (does not spawn in vanilla, rare). XP: 350.

**<#55ff55>Swamp Crocodile</#55ff55>** — 900 HP, 80 Physical damage. Slow-moving tank found near water. Drops <#aaaaaa>Swamp Crocodile Scale</#aaaaaa>, <#aaaaaa>Bone Fragment</#aaaaaa>, <#d16eff>Void Essence</#d16eff>, <#ff6600>Onyxium Bar</#ff6600>, and <#55ffff>Swamp Currency</#55ffff>. Spawns in <#55ff55>Swamp Dungeon</#55ff55> and swamp biomes. XP: 500.

**<#d16eff>Zombie Aberrant</#d16eff>** — 400 HP, 119 Physical damage. High single-hit damage. Drops Voidheart or Adamantite Bars. Added to <#aaaaaa>Zone 4 Wastes</#aaaaaa> spawns (does not spawn in vanilla). XP: 200.

**<#ff6600>Dragon Fire</#ff6600>** — 1000 HP, 35 damage fireballs. Wild fire dragon. Explosion radius 3 blocks. Spawns in the <#ff6600>center of the volcano</#ff6600> containing gold. XP: 500.

**<#ff8800>Bramble Elite</#ff8800>** — 550 HP, 90 Physical (Bite) + 70 Physical (Swipe) + Poison T3. A massive thorned mini-boss found deep in the Swamp Dungeon. 3-attack combo chain with elite boss bar. Drops <#55ff55>Hedera's Bramble</#55ff55> (guaranteed) and <#55ffff>Swamp Currency</#55ffff>.

---

<buttons>
    <button topic="index">Home</button>
    <button topic="npcs">NPCs & Mobs</button>
    <button topic="frozen-dungeon">Frozen Dungeon</button>
    <button topic="weapons">Weapons</button>
</buttons>
