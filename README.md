# Endgame & QoL

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/endgame-qol) [![Discord](https://img.shields.io/badge/Discord-Join_Us-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/mrCyvJmC28) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/lewai)

Hytale server plugin adding endgame content: bosses, weapons, dungeons, NPCs, crafting, and quality-of-life features.

## Features

- **3 Boss encounters** — Dragon Frost, Hedera, Golem Void with multi-phase AI and player scaling
- **40 Weapons** — Longswords, daggers, spears, staves, battleaxes, maces, shields, bows across 7 material tiers
- **2 Dungeons** — Frozen Dungeon and Swamp Dungeon with unique enemies, traders, and loot
- **5 Armor sets** — Mithril, Onyxium, Prisma, Hedera, Frost Bone
- **6 Accessories** — Trinket Pouch with Frostwalkers, Ocean Striders, Void Amulet, Blazefist, Pocket Garden, Hedera Seed
- **Achievement System** — 42 achievements across 8 categories (Combat, Boss, Bounty, Discovery, Crafting, Exploration, Speedrun, Mining)
- **Bounty Board** — 54 bounty templates with daily/weekly quests, mining and exploration bounties, reputation ranks
- **Combo Meter** — Kill streak tracker with tier effects
- **Warden Trials** — 4-tier wave survival challenge
- **Bestiary** — 32 NPC entries with kill milestones
- **Multi-language** — EN, PT-BR, RU (FR, ES prepared — will activate when Hytale adds official support)
- **Database support** — Optional SQL persistence (SQLite, MySQL, MariaDB, PostgreSQL)

## Requirements

- **Java 25** (bundled with Hytale Server)
- **Hytale Server** `2026.03.26` or later

## Dependencies

| Dependency | Type | Bundled in JAR | Source |
|---|---|---|---|
| **Hytale:NPC** | Required | No (engine) | Built-in |
| **[HyUI](https://www.curseforge.com/hytale/mods/hyui)** | Required | Yes (shaded) | CurseForge |
| HikariCP | Internal | Yes (shaded) | Maven Central |
| [RPGLeveling](https://www.curseforge.com/hytale/mods/rpg-leveling-and-stats) | Optional | No | CurseForge |
| [EndlessLeveling](https://www.curseforge.com/hytale/mods/endless-leveling) | Optional | No | CurseForge |
| [OrbisGuard](https://www.curseforge.com/hytale/mods/orbisguard) | Optional | No | CurseForge |

HyUI and HikariCP are bundled inside the plugin JAR via shadow/shading — no separate download needed.
Optional dependencies go in your server's `Mods/` folder.

## Build

```bash
./gradlew compileJava    # Compile only (fast check)
./gradlew build          # Full build + auto-deploy to Mods folder
./gradlew shadowJar      # Shadow JAR only (no deploy)
```

Output JAR in `build/libs/`. The `build` task auto-deploys to `%APPDATA%/Hytale/UserData/Mods/`.

## Project Structure

```
src/main/java/endgame/plugin/
  EndgameQoL.java          # Main plugin class
  commands/                 # Slash commands (/eg, /egconfig, /egadmin)
  components/               # ECS components
  config/                   # BuilderCodec config system
  database/                 # Optional SQL persistence
  events/                   # Event handlers
  integration/              # Optional mod bridges (RPGLeveling, EndlessLeveling, OrbisGuard)
  managers/                 # Game managers (boss, combo, gauntlet, bounty, achievement)
  migration/                # Data migration helpers
  services/                 # Domain services (sound, event bus, boss kill credit)
  spawns/                   # NPC spawn systems
  systems/                  # ECS systems (boss, weapon, effect, trial, accessory)
  ui/                       # HyUI pages and HUD
  utils/                    # Utilities
  watchers/                 # Entity watchers (temple events)

src/main/resources/
  manifest.json             # Plugin manifest
  Server/                   # Server-side JSON assets (items, NPCs, drops, instances, etc.)
  Common/                   # Client-side shared assets (models, textures, icons, UI, docs)
```

## License

All rights reserved.

You are free to fork this project for private use or to contribute to the original repository.
