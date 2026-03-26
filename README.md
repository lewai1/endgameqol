# Endgame & QoL

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/endgame-qol) [![Discord](https://img.shields.io/badge/Discord-Join_Us-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/mrCyvJmC28) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/lewai)

Hytale server plugin adding endgame content: bosses, weapons, dungeons, NPCs, crafting, and quality-of-life features.

## Requirements

- Java 25
- Hytale Server (`2026.03.26-89796e57b` or compatible)

## Build

```bash
# Compile only (fast check)
./gradlew compileJava

# Full build with shadow JAR + auto-deploy
./gradlew build

# Shadow JAR only (no deploy)
./gradlew shadowJar
```

The output JAR is in `build/libs/`. The `build` task auto-deploys to the Hytale Mods folder.

## Dependencies

| Dependency | Required | Source |
|---|---|---|
| Hytale:NPC | Yes | Built-in |
| RPGLeveling | Optional | [CurseForge](https://www.curseforge.com/hytale/mods/rpg-leveling-and-stats) |
| EndlessLeveling | Optional | [CurseForge](https://www.curseforge.com/hytale/mods/endless-leveling) |
| OrbisGuard | Optional | [CurseForge](https://www.curseforge.com/hytale/mods/orbisguard) |

Optional dependency JARs go in `libs/` (local Maven repo layout, compileOnly).

## Project Structure

```
src/main/java/endgame/plugin/
  EndgameQoL.java          # Main plugin class
  commands/                 # Slash commands (/eg, /egconfig, /egadmin)
  components/               # ECS components
  config/                   # BuilderCodec config system
  database/                 # Optional SQL persistence (SQLite, MySQL, PostgreSQL, MariaDB)
  events/                   # Event handlers
  integration/              # Optional mod bridges (RPGLeveling, EndlessLeveling, OrbisGuard)
  managers/                 # Game managers (boss, combo, gauntlet, bounty, achievement)
  migration/                # Legacy data migration
  spawns/                   # NPC spawn systems
  systems/                  # ECS systems (boss, weapon, effect, trial, accessory)
  ui/                       # HyUI pages and HUD
  utils/                    # Utilities
  watchers/                 # World watchers

src/main/resources/
  manifest.json             # Plugin manifest
  Server/                   # Server-side JSON assets
    BarterShops/            # Vorthak/Korvyn/Morghul shop data
    Drops/                  # Drop tables
    Entity/                 # Entity effects (slow, burn, etc.)
    Environments/           # Environment configs
    GameplayConfigs/        # Custom gameplay settings
    HytaleGenerator/        # Biome and world gen configs
    Instances/              # Dungeon instance configs
    Item/                   # Items, interactions, recipes, animations
    Languages/              # Localization (en-US, pt-BR, ru-RU)
    Models/                 # NPC model definitions
    NPC/                    # NPC roles, spawn, AI, balancing
    Particles/              # Custom particle systems
    PortalTypes/            # Portal definitions
    Prefabs/                # Dungeon prefabs
    ProjectileConfigs/      # Projectile physics
    Projectiles/            # Projectile definitions
    Weathers/               # Weather configs
  Common/                   # Client-side shared assets
    Blocks/                 # Custom block models
    BlockTextures/          # Block textures
    Docs/                   # In-game documentation (Voile wiki)
    Icons/                  # Item icons
    Items/                  # Item models and textures
    NPC/                    # NPC animations
    Resources/              # Shared resources
    UI/                     # Custom UI pages
```

## License

All rights reserved.

You are free to fork this project for private use or to contribute to the original repository.