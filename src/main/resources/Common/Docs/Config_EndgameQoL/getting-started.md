---
name: Server Setup
description: Installation, configuration, and setup for server owners
author: Lewai
---

# <gradient data="#ff6600:#FFD700">Server Setup</gradient>

This page is for <#ff6600>server owners</#ff6600> and <#ff6600>solo players</#ff6600> who want to install and configure Endgame & QoL.

---

## <#55ffff>Installation</#55ffff>

Drop the <#d16eff>EndgameAndQoL</#d16eff> JAR into your server's <#55ffff>Mods/</#55ffff> folder. The plugin includes its own asset pack — no separate download needed.

**Required dependency** — <#aaaaaa>Hytale:NPC</#aaaaaa> (bundled with the server)

**Optional dependencies:**
- <#aaaaaa>Zuxaw:RPGLeveling</#aaaaaa> — XP rewards on boss kills (auto-detected)
- <#aaaaaa>com.airijko:EndlessLeveling</#aaaaaa> — XP rewards, party sharing, mob level scaling (auto-detected)
- <#aaaaaa>OrbisGuard:OrbisGuard</#aaaaaa> — Auto-protect dungeon instances: block build, PvP, commands (disabled by default)
- <#aaaaaa>IwakuraEnterprises:Voile</#aaaaaa> — In-game documentation browser (this wiki!)

---

## <#55ffff>First Launch</#55ffff>

On first startup the plugin creates a default <#aaaaaa>EndgameConfig.json</#aaaaaa> with balanced settings. All content loads automatically:

- 62+ weapons, 5 armor sets, 6 accessories
- 10+ new NPCs in Zone 4
- 3 boss encounters + 2 dungeon instances
- Warden's Trial (4-tier wave survival)
- Vorthak, Korvyn, and Morghul merchants
- Endgame Workbench with 5 crafting tiers
- Bounty System, Achievements, Bestiary, Combo Meter
- Multi-language support (EN, FR, ES, PT-BR, RU)

---

## <#55ffff>Quick Configuration</#55ffff>

Open the config UI with <#55ffff>/egconfig</#55ffff> (requires <#aaaaaa>endgameqol.config</#aaaaaa> permission). All changes apply immediately.

| Tab | What you can tune |
|:----|:------------------|
| **Difficulty** | Preset (Easy/Medium/Hard/Extreme) or custom multipliers |
| **Scaling** | Per-boss HP, damage, player scaling, enrage (sub-tabs: Bosses, Mobs, Zone 4) |
| **Integration** | RPG Leveling, Endless Leveling, OrbisGuard toggles |
| **Weapons** | Hedera poison/lifesteal, Prisma clones, Void Mark, blink mode |
| **Armor** | Mana regen per tier, HP regen, system toggles |
| **Crafting** | Enable/disable specific recipes (glider, portal keys, Mithril Ore) |
| **Misc** | PvP toggle, dungeon block protection, boss target switch, combo meter |

---

## <#ff6600>Permissions</#ff6600>

Player commands (<#55ffff>/eg bounty</#55ffff>, <#55ffff>/eg bestiary</#55ffff>, etc.) are **allowed by default** — all players can use them. To restrict a command, add the negated permission (e.g. <#ff5555>-endgameqol.bounty</#ff5555>).

Admin commands (<#55ffff>/egconfig</#55ffff>, <#55ffff>/egadmin</#55ffff>) are **denied by default** — grant <#aaaaaa>endgameqol.config</#aaaaaa> and <#aaaaaa>endgameqol.admin</#aaaaaa> explicitly. Server operators have them automatically.

| Command | Permission |
|:--------|:-----------|
| /egconfig | endgameqol.config |
| /eg status, /egadmin | endgameqol.admin |
| /eg bestiary | endgameqol.bestiary |
| /eg achievements | endgameqol.achievements |
| /eg bounty, /bounty | endgameqol.bounty |
| /eg lang, /voile | None (all players) |

---

## <#ff6600>Recipe Customization</#ff6600>

Beyond the quick toggles in <#55ffff>/egconfig</#55ffff>, you can fully customize all ~96 recipes via <#55ffff>RecipeOverrides.json</#55ffff> in <#aaaaaa>Saves/save/mods/Config_Endgame&QoL/</#aaaaaa>. Auto-generated on first boot.

**Per recipe you can:** disable it, change ingredients/quantities, change the output, change the bench/tier requirement, change craft time.

! RecipeOverrides requires a <#ff5555>server restart</#ff5555>. Use <#55ffff>/egconfig</#55ffff> Crafting tab for instant on/off toggles.

---

## <#ff6600>Difficulty Recommendations</#ff6600>

| Server Type | Recommended Preset |
|:------------|:-------------------|
| Casual / new players | <#55ff55>Easy</#55ff55> (60% HP/50% Dmg) |
| Standard experience | Medium (100%/100%) |
| Experienced groups | <#ff5555>Hard</#ff5555> (150%/150%) |
| Hardcore / challenge | <#ff5555>Extreme</#ff5555> (250%/200%) |

You can also set Custom multipliers (10-1000%) and fine-tune each boss individually in the Scaling sub-tabs.

---

## <#55ffff>Recommended Setup</#55ffff>

**Small server (1-5 players):**
- Default settings work great out of the box
- Consider Easy preset if players are new to Hytale combat
- Database: optional (SQLite if you want backup)

**Medium server (5-20 players):**
- Enable RPG Leveling or Endless Leveling for XP progression
- Enable OrbisGuard to protect dungeon instances from griefing
- Database: SQLite or MySQL for persistent player data

**Large server (20+ players):**
- Tune per-boss player scaling in <#55ffff>/egconfig</#55ffff> (default +50%/player may need adjustment)
- MySQL/MariaDB for cross-instance player sync
- Consider restricting some commands via permissions

---

<buttons>
    <button topic="index">Home</button>
    <button topic="configuration">Full Config Reference</button>
    <button topic="database">Database Setup</button>
    <button topic="commands">Commands & Permissions</button>
</buttons>
