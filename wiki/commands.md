---
title: Commands
description: All commands, permission nodes, and the config UI reference.
order: 15
published: true
---

# Commands & Permissions

All EndgameQoL commands are under `/eg`.

## /eg

| Subcommand | Description | Permission |
|:-----------|:------------|:-----------|
| `/eg journal` | Open the Journal (Bounty Board, Bestiary, Achievements) | endgameqol.journal |
| `/eg config` | Open the configuration UI (7 tabs, search, recipe editor) | endgameqol.config |
| `/eg status` | Diagnostics dashboard | endgameqol.admin |
| `/eg lang <locale|auto>` | Set display language (EN, FR, ES, PT-BR, RU) | None |

## /eg config

**Opens the native configuration UI** with 7 tabs: Difficulty, Scaling, Weapons, Armor, Crafting, Misc, Integration.

Features: global search bar, editable value fields, recipe override editor with per-recipe editing.

Permission: **endgameqol.config** (op-only by default)

## /eg admin

Permission: **endgameqol.admin** (op-only)

| Subcommand | Description |
|:-----------|:------------|
| `/eg admin debug boss <type>` | Dump active boss state |
| `/eg admin reset bounties <player|all>` | Force refresh bounties |
| `/eg admin reload` | Reload config from disk (async) |

## Permission Model

**Default-allow** — `/eg journal`, `/eg lang` work for all players by default.

**Op-only** — `endgameqol.admin` and `endgameqol.config` require operator.

**Deny** — use negation: `-endgameqol.journal` or `-endgameqol.*`
