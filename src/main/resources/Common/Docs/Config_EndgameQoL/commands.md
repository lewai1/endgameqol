---
name: Commands
description: Commands and permissions
author: Lewai
---

# <gradient data="#55ffff:#d16eff">Commands</gradient>

---

## <#55ffff>/egconfig</#55ffff>

Opens the EndgameQoL configuration UI. Alias: <#55ffff>/egcfg</#55ffff>

**Permission** — <#aaaaaa>endgameqol.config</#aaaaaa>

The config UI has tabs for Difficulty, Scaling (with Bosses/Mobs/Zone4 sub-tabs), RPG Leveling, Weapons, Armor, Crafting, and Misc. Every change saves immediately and syncs to all connected players.

See the Configuration page for a full list of all settings.

---

## <#55ffff>/eg</#55ffff>

Parent command with several subcommands:

**<#55ffff>/eg status</#55ffff>** — Live diagnostics dashboard showing difficulty, database health, active encounters, feature toggles, player count, recipe/locale stats. Permission: <#aaaaaa>endgameqol.admin</#aaaaaa>

**<#55ffff>/eg achievements</#55ffff>** — View your achievement progress across all 5 categories. Claim item rewards for completed achievements. Alias: <#55ffff>/eg ach</#55ffff>. Permission: <#aaaaaa>endgameqol.achievements</#aaaaaa>

**<#55ffff>/eg lang <code></#55ffff>** — Change your display language. Supported: <#aaaaaa>EN</#aaaaaa>, <#aaaaaa>FR</#aaaaaa>, <#aaaaaa>ES</#aaaaaa>, <#aaaaaa>PT-BR</#aaaaaa>, <#aaaaaa>RU</#aaaaaa>. Per-player setting, persists across sessions. No permission required.

**<#55ffff>/eg bestiary</#55ffff>** — View the NPC bestiary with kill counts and discovery milestones. Permission: <#aaaaaa>endgameqol.bestiary</#aaaaaa>

**<#55ffff>/eg bounty</#55ffff>** — Opens the Bounty Board. View available daily bounties, claim completed rewards, track streaks, and check your reputation rank progress. Permission: <#aaaaaa>endgameqol.bounty</#aaaaaa>

**<#55ffff>/eg gauntlet</#55ffff>** — Starts a Gauntlet run (wave survival mode). <#ff5555>Currently disabled</#ff5555> — the feature is still in development. Permission: <#aaaaaa>endgameqol.gauntlet</#aaaaaa>

! Standalone <#55ffff>/bounty</#55ffff> and <#55ffff>/gauntlet</#55ffff> commands also exist as aliases and use the same permissions.

---

## <#ff5555>/egadmin</#ff5555>

Admin command collection for server operators.

**<#55ffff>/egadmin debug boss</#55ffff>** — Debug active boss state

**<#55ffff>/egadmin reset leaderboard</#55ffff>** — Reset the Gauntlet leaderboard

**<#55ffff>/egadmin reset bounties</#55ffff>** — Reset all player bounty progress

**<#55ffff>/egadmin reload</#55ffff>** — Hot-reload config without restart

**Permission** — <#aaaaaa>endgameqol.admin</#aaaaaa>

---

## <#d16eff>/voile</#d16eff>

Opens the Voile documentation browser (requires the Voile mod). Browse this wiki and other server documentation in-game.

---

## <#aaaaaa>Permissions Summary</#aaaaaa>

| Command | Permission |
|:--------|:-----------|
| /egconfig | endgameqol.config |
| /eg status, /egadmin | endgameqol.admin |
| /eg bestiary | endgameqol.bestiary |
| /eg achievements | endgameqol.achievements |
| /eg bounty, /bounty | endgameqol.bounty |
| /eg gauntlet, /gauntlet | endgameqol.gauntlet |
| /eg lang, /voile | None (all players) |

! All commands have a 1-second per-player rate limit.

!v **Player commands use default-allow.** Bestiary, achievements, bounty, and gauntlet commands work for all players by default. To restrict a command, add the negated permission to the player or group (e.g. <#ff5555>-endgameqol.bounty</#ff5555>). Use <#ff5555>-endgameqol.*</#ff5555> to deny all at once.

!v NPC-triggered UIs (Bounty Board, Bestiary NPCs) bypass command permissions — players can always interact with NPCs regardless of command permissions.

!! **Admin commands use default-deny.** <#aaaaaa>endgameqol.config</#aaaaaa> and <#aaaaaa>endgameqol.admin</#aaaaaa> must be explicitly granted (ops have them automatically).

---

<buttons>
    <button topic="index">Home</button>
    <button topic="configuration">Configuration</button>
    <button topic="getting-started">Getting Started</button>
</buttons>
