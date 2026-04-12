---
name: Commands
description: Commands and permissions
author: Lewai
sort-index: 1
---

# <gradient data="#55ffff:#d16eff">Commands</gradient>

---

All EndgameQoL commands are under <#55ffff>/eg</#55ffff>.

---

## <#55ffff>/eg config</#55ffff>

Opens the EndgameQoL configuration UI with 7 tabs (Difficulty, Scaling, Weapons, Armor, Crafting, Misc, Integration), global search, and full recipe override editor.

**Permission** — <#aaaaaa>endgameqol.config</#aaaaaa>

See the Configuration page for a full list of all settings.

---

## <#55ffff>/eg journal</#55ffff>

Opens the Journal — a unified page with 3 tabs:
- **Bounty Board** — Daily bounties with progress tracking, streak bonuses, weekly bounty, and reputation ranks
- **Bestiary** — NPC entries with kill counts, discovery milestones, category filters, and mob portraits
- **Achievements** — Progress bars, category badges, and claimable rewards

**Permission** — <#aaaaaa>endgameqol.journal</#aaaaaa> (default-allow)

---

## <#55ffff>/eg status</#55ffff>

Live diagnostics dashboard showing difficulty, database health, active encounters, feature toggles, player count, recipe/locale stats.

**Permission** — <#aaaaaa>endgameqol.admin</#aaaaaa>

---

## <#55ffff>/eg lang <code></#55ffff>

Change your display language. Supported: <#aaaaaa>EN</#aaaaaa>, <#aaaaaa>FR</#aaaaaa>, <#aaaaaa>ES</#aaaaaa>, <#aaaaaa>PT-BR</#aaaaaa>, <#aaaaaa>RU</#aaaaaa>. Per-player setting, persists across sessions. No permission required.

---

## <#ff5555>/eg admin</#ff5555>

Admin subcommands for server operators.

**<#55ffff>/eg admin debug boss</#55ffff>** — Debug active boss state

**<#55ffff>/eg admin reset bounties</#55ffff>** — Reset all player bounty progress

**<#55ffff>/eg admin reload</#55ffff>** — Hot-reload config without restart

**Permission** — <#aaaaaa>endgameqol.admin</#aaaaaa>

---

## <#d16eff>/voile</#d16eff>

Opens the Voile documentation browser (requires the Voile mod). Browse this wiki and other server documentation in-game.

---

## <#aaaaaa>Permissions Summary</#aaaaaa>

| Command | Permission |
|:--------|:-----------|
| /eg config | endgameqol.config |
| /eg journal | endgameqol.journal (default-allow) |
| /eg status, /eg admin | endgameqol.admin |
| /eg lang, /voile | None (all players) |

! All commands have a 1-second per-player rate limit.

!v **Player commands use default-allow.** Journal commands work for all players by default. To restrict a command, add the negated permission to the player or group (e.g. <#ff5555>-endgameqol.journal</#ff5555>). Use <#ff5555>-endgameqol.*</#ff5555> to deny all at once.

!v NPC-triggered UIs (Bounty Board, Bestiary NPCs) bypass command permissions — players can always interact with NPCs regardless of command permissions.

!! **Admin commands use default-deny.** <#aaaaaa>endgameqol.config</#aaaaaa> and <#aaaaaa>endgameqol.admin</#aaaaaa> must be explicitly granted (ops have them automatically).

---

<buttons>
    <button topic="index">Home</button>
    <button topic="configuration">Configuration</button>
    <button topic="getting-started">Getting Started</button>
</buttons>
