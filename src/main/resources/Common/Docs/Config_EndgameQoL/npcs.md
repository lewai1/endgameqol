---
name: NPCs & Mobs
description: All endgame NPCs with stats, attacks, and locations
author: Lewai
---

# <gradient data="#55ff55:#ff6600:#d16eff">NPCs & Mobs</gradient>

Endgame & QoL adds new NPCs to Zone 4 using unused vanilla models and animations. All NPCs have custom attack chains with direct damage values (no vanilla Replace/Var bugs).

---

## <#aaaaaa>Zone 4 — Wastes</#aaaaaa>

### Ghoul
**HP** — 193 | **Speed** — 10 | **Damage** — 70 Physical

Fast undead predator with a vicious bite attack (2.5m range, 60-degree arc). Moderate knockback. Spawns day and night.

### Zombie Aberrant
**HP** — 400 | **Speed** — 10 | **Damage** — 119 Physical

High single-hit damage undead. Drops <#aaaaaa>Voidheart</#aaaaaa> (1) and <#aaaaaa>Adamantite Bar</#aaaaaa> (1-2). Spawns day and night (weight 2).

### Onyxium Encounter
**HP** — 700 | **Speed** — 6 | **Damage** — 85 Physical | **Template** — Intelligent (humanoid)

A powerful warrior in full <#ff6600>Onyxium armor</#ff6600> wielding <#ff6600>Onyxium Daggers</#ff6600>. **Passive until attacked** — will not aggro unless provoked. Drops <#ff6600>Onyxium Bar</#ff6600> (2-4), Voidheart (50%), Storm Hide (2-3, 25%), rare Onyxium weapon (10%) or armor (5%). Very rare spawn (day and night).

---

## <#55ff55>Zone 4 — Jungles</#55ff55>

### Saurian Warrior
**HP** — 220 | **Speed** — 6 | **Damage** — 45 Physical

Heavy-hitting melee combatant. Slower but tankier than other Saurians. Spawns in groups with Hunters and Rogues. Daytime only (6:00–18:00).

### Saurian Hunter
**HP** — 180 | **Speed** — 8 | **Damage** — 65 Physical

Mid-speed skirmisher with high damage output. Balanced between the Warrior's tankiness and the Rogue's speed. Daytime only.

### Saurian Rogue
**HP** — 150 | **Speed** — 10 | **Damage** — 55 Physical

The fastest Saurian variant with a 70-degree attack arc and 0.3s attack speed (faster than other NPCs at 0.4s). Low HP but hard to escape. Daytime only.

### Alpha Rex (Elite)
**HP** — 700 | **Speed** — 9 | **Damage** — 80 Physical

Fast aggressive predator with heavy knockback. Rare spawn (weight 0.5). Drops <#aaaaaa>Apex Sovereign Leather</#aaaaaa> (2-3) and <#aaaaaa>Alpha Trex Meat</#aaaaaa> (3-6). XP: 350.

---

## <#ff6600>Enhanced Vanilla Mobs</#ff6600>

These are vanilla NPCs with corrected damage values (vanilla InteractionVars bug caused them to deal only 5 damage).

### Outlander Brute

**Damage** — 35 Physical (swings), 55 (ground slam), 40 (stomp, AOE slow), 25 (grab). Drops <#aaaaaa>Adamantite Bar</#aaaaaa> (2-4), <#aaaaaa>Shadoweave Fabric Scrap</#aaaaaa> (1-4), <#55ffff>Mithril Bar</#55ffff> (1-2, 25%). Vanilla spawn locations.

### Scarak Broodmother

Now spits venom projectiles (25 Physical + 50% slow for 3s with green tint) instead of generic rubble. Drops <#aaaaaa>Adamantite Bar</#aaaaaa> (5-6). Vanilla spawn locations.

### Scarak Fighter

Corrected melee damage (20 Physical instead of vanilla 5). Drops <#aaaaaa>Sturdy Chitin</#aaaaaa> (1-2) and <#55ff55>Venom Sac</#55ff55> (30%). Vanilla spawn locations.

---

## <#55ff55>Spawn Locations</#55ff55>

**Zone 4 Wastes** (day & night):

| NPC | Weight |
|:----|-------:|
| Ghoul | 4 |
| Zombie Aberrant | 2 |
| Onyxium Encounter | 1 (passive) |

**Zone 4 Jungles** (daytime 6:00-18:00):

| NPC | Weight |
|:----|-------:|
| Saurian Warrior | 1 |
| Saurian Hunter | 1 |
| Saurian Rogue | 1 |
| Alpha Rex | 0.5 (rare) |

! Enhanced mobs (Scarak Fighter, Broodmother, Outlander Brute) use vanilla spawn locations — no custom spawns.

---

## <#55ff55>Swamp Dungeon</#55ff55>

### Bramble Elite
**HP** — 550 | **Speed** — 8 | **Damage** — 90 Physical (Bite) + 70 Physical (Swipe) + Poison T3

Elite mini-boss with a 3-attack combo chain. Displays elite boss bar. Drops <#55ff55>Hedera's Bramble</#55ff55> (guaranteed) and <#55ffff>Swamp Currency</#55ffff>.

### Fen Stalker
**HP** — 200 | **Speed** — 10 | **Damage** — 29 Physical

A swift amphibian predator lurking in murky waters. Its powerful jaws snap shut before prey can react. Drops Raw Wildmeat and Medium Hide.

### Grooble
**HP** — 250 | **Speed** — 5.5 | **Damage** — Physical

A burrowing beast that erupts from the ground. Its stone-covered hide deflects weak blows. Drops Raw Wildmeat.

---

## <#55ffff>Frozen Dungeon</#55ffff>

### Frost Feran
**HP** — 150 | **Speed** — 10 | **Damage** — 35 Physical (Sword)

A frost-touched Feran warrior lurking in the frozen dungeon. Wields a bone sword with eerie skill. Drops Medium Hides, Cobalt Bars, and Flocons.

---

## <#ff5555>Warden's Trial Exclusive</#ff5555>

These NPCs only appear in the <#55ffff>Warden's Trial</#55ffff> challenge waves. They do not spawn in the open world.

**Werewolf** — 283 HP, Speed 10, 90 Physical. Fast creature with a grab attack (3m range, 80-degree arc). Extended vertical hitbox catches jumping players. Strong knockback.

**Shadow Knight** — 400 HP, Speed 6. One of the tankiest and hardest-hitting NPCs. 150 Physical per swing with heavy knockback. Extremely dangerous in groups.

**Void Necromancer** — 500 HP, Speed 5, Intelligent template. Spellcaster wielding a <#d16eff>Demon Spellbook</#d16eff>. Casts void orbs (35 Physical ranged) and summons <#ff5555>6 random skeletons</#ff5555> (Soldiers, Fighters, Archers, Mages, Scouts, Knights). Melee fallback at 55 Physical.

**Goblin Duke** — 350 HP, Speed 6, Intelligent template. A powerful goblin leader wielding a <#ff6600>Doomed Club</#ff6600>. 75 Physical damage per swing with a wide 90-degree arc. Immune to Goblin and Self damage. High knockback.

---

<buttons>
    <button topic="index">Home</button>
    <button topic="bosses">Bosses & Elites</button>
    <button topic="warden-trial">Warden's Trial</button>
    <button topic="weapons">Weapons</button>
</buttons>
