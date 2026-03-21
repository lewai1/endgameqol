# Endgame & QoL

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/hytale/mods/endgame-qol) [![Discord](https://img.shields.io/badge/Discord-Join_Us-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/mrCyvJmC28) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Me-FF5E5B?style=for-the-badge&logo=ko-fi&logoColor=white)](https://ko-fi.com/lewai)

Hytale server plugin adding endgame content: bosses, weapons, dungeons, NPCs, crafting, and quality-of-life features.

## Requirements

- Java 25
- Hytale Server (`2026.03.20-db226053c` or compatible)

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
  commands/                 # Slash commands
  components/               # ECS components
  config/                   # Config system
  events/                   # Event handlers
  managers/                 # Game managers
  systems/                  # ECS systems (boss, weapon, effect)
  ui/                       # HyUI pages and HUD
  utils/                    # Utilities

src/main/resources/
  manifest.json             # Plugin manifest
  Server/                   # Server-side JSON assets
    Item/                   # Items, interactions, recipes
    NPC/                    # NPC definitions and AI
    Drops/                  # Drop tables
    Prefabs/                # Dungeon prefabs
```

## License

All rights reserved.

You are free to fork this project for private use or to contribute to the original repository.