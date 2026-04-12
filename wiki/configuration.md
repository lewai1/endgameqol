---
title: Configuration
description: Full configuration reference with every config key, default value, and range organized by section.
order: 16
published: true
---

# Configuration

Every config key, default value, and range — organized by section.

## How to Configure

- **In-game UI** — `/eg config` (requires `endgameqol.config` permission)
- **JSON file** — `Config_Endgame&QoL/EndgameConfig.json`
- **Hot reload** — `/eg admin reload`

## Difficulty

| Key | Default | Description |
|:----|:--------|:------------|
| `Preset` | MEDIUM | EASY / MEDIUM / HARD / EXTREME / CUSTOM |
| `AffectsBosses` | true | Apply difficulty multipliers to bosses |
| `AffectsMobs` | true | Apply difficulty multipliers to regular mobs |
| `CustomHealthMultiplier` | 1.0 | HP multiplier when Preset=CUSTOM (0.1 -- 10.0) |
| `CustomDamageMultiplier` | 1.0 | Damage multiplier when Preset=CUSTOM (0.1 -- 10.0) |

Presets: **Easy** 60%/50% | **Medium** 100%/100% | **Hard** 150%/150% | **Extreme** 250%/200%

## Weapons

| Key | Default | Description |
|:----|:--------|:------------|
| `HederaPoisonEnabled` | true | Hedera Daggers poison on hit |
| `HederaPoisonDamage` | 5.0 | Damage per poison tick (0.1 -- 50) |
| `HederaPoisonTicks` | 4 | Number of poison ticks (1 -- 20) |
| `HederaLifestealEnabled` | true | Hedera Daggers lifesteal |
| `HederaLifestealPercent` | 0.08 | Lifesteal percentage (8%) |
| `BlazefistBurnDamage` | 50.0 | Blazefist burn damage per tick |
| `BlazefistBurnTicks` | 3 | Number of burn ticks (1 -- 10) |

> Prisma Sword and Prisma Daggers abilities (Beam, Judgment, Dash, Razor Storm) are fully defined in JSON and have no runtime config toggles. Balance values (mana cost, damage, cooldown) are edited directly in the interaction JSON files under `Server/Item/Interactions/Weapons/{Sword,Daggers}/`.

## Armor

| Key | Default | Description |
|:----|:--------|:------------|
| `ManaRegenEnabled` | true | Passive mana regen on endgame armor |
| `ManaRegenMithrilPerPiece` | 0.5 | Mana/s per Mithril piece (0 -- 5) |
| `ManaRegenOnyxiumPerPiece` | 0.75 | Mana/s per Onyxium piece |
| `ManaRegenPrismaPerPiece` | 1.0 | Mana/s per Prisma piece |
| `HPRegenEnabled` | true | Passive HP regen on Onyxium/Prisma |
| `HPRegenDelaySec` | 15.0 | Seconds after last damage before regen starts (1 -- 60) |
| `HPRegenOnyxiumPerPiece` | 0.5 | HP/s per Onyxium piece |
| `HPRegenPrismaPerPiece` | 0.75 | HP/s per Prisma piece |

## Combo

| Key | Default | Description |
|:----|:--------|:------------|
| `Enabled` | true | Enable combo meter |
| `TimerSeconds` | 5.0 | Decay timer (1 -- 30) |
| `DamageX2 / X3 / X4 / Frenzy` | 1.10 / 1.25 / 1.50 / 2.00 | Damage multiplier per tier |
| `TierEffectsEnabled` | true | Speed, heal, lifesteal bonuses |

## Bounty

| Key | Default | Description |
|:----|:--------|:------------|
| `Enabled` | true | Enable bounty system |
| `RefreshHours` | 24 | Hours between bounty refresh (1 -- 168) |
| `StreakEnabled` | true | Streak bonus for completing all 3 daily |
| `WeeklyEnabled` | true | Enable weekly bounties |

## Misc

| Key | Default | Description |
|:----|:--------|:------------|
| `PvpEnabled` | false | PvP in endgame instances |
| `EnableDungeonBlockProtection` | true | Block building inside dungeons |
| `EnableWardenTrial` | true | Enable Warden Trial system |
| `AccessoriesEnabled` | true | Enable Trinket Pouch accessories |
| `BossTargetSwitchEnabled` | true | Boss target switching in multi-player |
| `BossTargetSwitchIntervalMs` | 8000 | Target switch interval (2000 -- 30000) |
| `SharedBossKillCredit` | true | All players in instance get boss kill achievements & bounty credit |
| `VorthakEnabled` | true | Vorthak merchant spawning |
| `WardenTrialTimerTier1--4` | 270 / 360 / 450 / 540 | Per-tier wave timer in seconds (0 = disabled, max 600) |

## Integration

Optional mod integrations are **auto-detected** on first boot. Enable/disable in `/eg config` Integration tab.

| Mod | Key | Default | Features |
|:----|:----|:--------|:---------|
| RPG Leveling | `RPGLevelingEnabled` | false | Boss kill XP, bounty XP, achievement XP |
| Endless Leveling | `EndlessLevelingEnabled` | false | Party XP sharing, bounty/trial/achievement XP |
| OrbisGuard | `OrbisGuardEnabled` | false | Auto-protect dungeon instances (block build, PvP, commands) |

## Crafting Toggles

| Key | Default | Description |
|:----|:--------|:------------|
| `EnableGlider` | true | Endgame glider recipe |
| `EnableMithrilOre` | false | Mithril Ore crafting (bypass dungeon) |
| `EnablePortalHedera` | true | Hedera portal key recipe |
| `EnablePortalGolemVoid` | true | Golem Void portal key recipe |

> [!NOTE]
> Individual recipe visibility is managed via `RecipeOverrides.json` (separate file, toggled in /eg config Crafting tab).
