---
name: Configuration
description: All config settings and difficulty presets
author: Lewai
sort-index: 2
---

# <gradient data="#55ffff:#aaaaaa:#55ffff">Configuration</gradient>

All settings are managed through <#55ffff>/eg config</#55ffff> (requires <#aaaaaa>endgameqol.config</#aaaaaa> permission). Changes apply immediately and persist across restarts.

---

## <#FFD700>Difficulty Presets</#FFD700>

Choose a global difficulty that scales boss and mob HP and damage.

| Preset | Health | Damage |
|:-------|-------:|-------:|
| <#55ff55>Easy</#55ff55> | 60% | 50% |
| Medium | 100% | 100% |
| <#ff5555>Hard</#ff5555> | 150% | 150% |
| <#ff5555>Extreme</#ff5555> | 250% | 200% |
| Custom | 10-1000% | 10-1000% |

<#aaaaaa>DifficultyAffectsBosses</#aaaaaa> — Apply multipliers to bosses (default: true)

<#aaaaaa>DifficultyAffectsMobs</#aaaaaa> — Apply multipliers to mobs/elites (default: true)

---

## <#ff5555>Per-Boss Configuration</#ff5555>

Each boss can be individually tuned. These override the global difficulty for that specific entity.

<#aaaaaa>HealthOverride</#aaaaaa> — Override max HP (0 = use default with difficulty scaling, max: 1,000,000)

<#aaaaaa>DamageMultiplier</#aaaaaa> — Override damage bonus (0 = use difficulty group, range: -90% to +900%)

<#aaaaaa>PlayerScaling</#aaaaaa> — Extra HP % per additional player (e.g., 50 = +50% HP per player)

!! In addition to HP scaling, bosses also deal <#ff5555>+15% damage</#ff5555> per additional player in the same world. Players wearing <#d16eff>Prisma armor</#d16eff> take <#ff5555>2x damage</#ff5555> from Dragon Frost and Hedera.

<#aaaaaa>DangerZoneStartPhase</#aaaaaa> — Golem Void only: phase when danger zone activates (1-3)

**Default values:**

| Boss | HP | XP |
|:-----|---:|---:|
| <#55ffff>Dragon Frost</#55ffff> | 1400 | 700 |
| <#55ff55>Hedera</#55ff55> | 1800 | 900 |
| <#d16eff>Golem Void</#d16eff> | 3500 | 3500 |
| <#ff5555>Alpha Rex</#ff5555> | 700 | 350 |
| <#ff6600>Dragon Fire</#ff6600> | 1000 | 500 |
| <#d16eff>Zombie Aberrant</#d16eff> | 400 | 200 |
| <#55ff55>Swamp Crocodile</#55ff55> | 900 | 500 |
| <#d16eff>Spectre Void</#d16eff> | 120 | 250 |

! The top 3 bosses scale +50%/player. Elites have no player scaling.

---

## <#ff5555>Boss Enrage System</#ff5555>

Per-boss enrage tuning. Available for Dragon Frost, Hedera, and Golem Void.

<#aaaaaa>EnrageEnabled</#aaaaaa> — Toggle enrage per boss (default: true)

<#aaaaaa>EnrageDamageThreshold</#aaaaaa> — Damage required to trigger within window (default: 200, range: 1-10,000)

<#aaaaaa>EnrageWindow</#aaaaaa> — Time window for burst detection (default: 5s, range: 1-30s)

<#aaaaaa>EnrageDuration</#aaaaaa> — How long enrage lasts (default: 8s, range: 1-60s)

<#aaaaaa>EnrageDamageMultiplier</#aaaaaa> — Damage boost while enraged (default: 1.5x, range: 1.0-5.0x)

<#aaaaaa>EnrageCooldown</#aaaaaa> — Time before enrage can trigger again (default: 15s, range: 0-120s)

---

## <#d16eff>Weapon Effects</#d16eff>

### Hedera Dagger

<#aaaaaa>EnableHederaDaggerPoison</#aaaaaa> — Toggle poison on hit (default: true)

<#aaaaaa>HederaDaggerPoisonDamage</#aaaaaa> — Damage per tick (default: 5.0, range: 0.1-50)

<#aaaaaa>HederaDaggerPoisonTicks</#aaaaaa> — Number of ticks (default: 4, range: 1-20)

<#aaaaaa>EnableHederaDaggerLifesteal</#aaaaaa> — Toggle lifesteal (default: true)

<#aaaaaa>HederaDaggerLifestealPercent</#aaaaaa> — Fraction healed (default: 0.08 = 8%)

### Prisma Sword / Prisma Daggers

Prisma weapon abilities (<#d16eff>Prismatic Beam</#d16eff>, <#d16eff>Prismatic Judgment</#d16eff>, <#d16eff>Prisma Dash</#d16eff>, <#d16eff>Razor Storm</#d16eff>) are **data-driven in JSON**. To rebalance mana/signature cost, damage, range, or cooldown, edit the interaction files directly:

- <#aaaaaa>Server/Item/Interactions/Weapons/Sword/Endgame_Sword_Prisma_Beam.json</#aaaaaa>
- <#aaaaaa>Server/Item/Interactions/Weapons/Sword/Endgame_Sword_Prisma_Judgment.json</#aaaaaa>
- <#aaaaaa>Server/Item/Interactions/Weapons/Daggers/Endgame_Daggers_Prisma_Dash.json</#aaaaaa>
- <#aaaaaa>Server/Item/Interactions/Weapons/Daggers/Endgame_Daggers_Prisma_Storm.json</#aaaaaa>
- <#aaaaaa>Server/Projectiles/Weapons/Endgame_Prisma_Beam.json</#aaaaaa> (beam projectile physics + explosion)

No runtime toggles in <#55ffff>/eg config</#55ffff>. Server restart required to apply JSON edits.

---

## <#55ffff>Armor Settings</#55ffff>

### Mana Regen

<#aaaaaa>ManaRegenArmorEnabled</#aaaaaa> — Toggle system (default: true)

<#aaaaaa>ManaRegenMithrilPerPiece</#aaaaaa> — Mana/sec per Mithril piece (default: 0.5)

<#aaaaaa>ManaRegenOnyxiumPerPiece</#aaaaaa> — Mana/sec per Onyxium piece (default: 0.75)

<#aaaaaa>ManaRegenPrismaPerPiece</#aaaaaa> — Mana/sec per Prisma piece (default: 1.0)

### HP Regen

<#aaaaaa>ArmorHPRegenEnabled</#aaaaaa> — Toggle system (default: true)

<#aaaaaa>ArmorHPRegenDelaySec</#aaaaaa> — Seconds without damage before regen starts (default: 15, range: 1-60)

<#aaaaaa>ArmorHPRegenOnyxiumPerPiece</#aaaaaa> — HP/sec per Onyxium piece (default: 0.5)

<#aaaaaa>ArmorHPRegenPrismaPerPiece</#aaaaaa> — HP/sec per Prisma piece (default: 0.75)

---

## <#ff5555>Combat Settings</#ff5555>

<#aaaaaa>MinionSpawnRadius</#aaaaaa> — Distance minions spawn from boss (default: 12)

<#aaaaaa>EyeVoidHealthMultiplier</#aaaaaa> — HP multiplier for Eye Void minions (default: 1.5)

<#aaaaaa>PvpEnabled</#aaaaaa> — Enable PvP in endgame instances (default: false)

---

## <#55ff55>Crafting Toggles</#55ff55>

<#aaaaaa>EnableGliderCrafting</#aaaaaa> — Void-Powered Glider recipe (default: true)

<#aaaaaa>EnableMithrilOreCrafting</#aaaaaa> — Mithril Ore at Bench Tier 2 (default: false)

<#aaaaaa>EnablePortalKeyTaiga</#aaaaaa> — Frozen Dungeon Key at Tier 2 (default: true)

<#aaaaaa>EnablePortalHedera</#aaaaaa> — Swamp Dungeon Key at Tier 3 (default: true)

<#aaaaaa>EnablePortalGolemVoid</#aaaaaa> — Shard of the Void at Tier 4 (default: true)

---

## <#ff6600>RecipeOverrides.json — Full Recipe Control</#ff6600>

For advanced recipe customization, edit <#55ffff>RecipeOverrides.json</#55ffff> in your server's config folder at <#aaaaaa>Saves/save/mods/Config_Endgame&QoL/</#aaaaaa>. This file is auto-generated on first boot with all ~96 mod recipe defaults. New recipes are auto-appended on plugin update.

**Per recipe you can:**
- <#ff5555>Disable</#ff5555> a recipe entirely (<#aaaaaa>"Enabled": false</#aaaaaa>)
- Change <#aaaaaa>ingredients</#aaaaaa> and quantities
- Change the <#aaaaaa>output</#aaaaaa> item or quantity
- Change the <#aaaaaa>bench</#aaaaaa> and tier requirement
- Change the <#aaaaaa>craft time</#aaaaaa>

Edit the file, restart the server, and all players see the updated recipes globally. <#ff5555>Requires a server restart</#ff5555> (unlike /eg config which is live).

The crafting toggles above (<#55ffff>/eg config</#55ffff>) are still useful for quick on/off switches that apply immediately without restart.

---

## <#ff5555>Boss Target Switching</#ff5555>

<#aaaaaa>BossTargetSwitchEnabled</#aaaaaa> — Toggle boss target switching (default: true)

<#aaaaaa>BossTargetSwitchIntervalMs</#aaaaaa> — Interval between target switches (default: 8000ms, range: 2000-30000ms)

Bosses switch targets between players using weighted random: nearest player (40%), highest damage dealer (40%), random (20%). Evaluated every 8-10 seconds.

---

## <#ff6600>Combo Meter</#ff6600>

<#aaaaaa>ComboEnabled</#aaaaaa> — Toggle the combo meter system (default: true)

All tier thresholds, damage multipliers, effects, and decay timers are configurable in the Misc tab.

---

## <#FFD700>Bounty System</#FFD700>

<#aaaaaa>BountyEnabled</#aaaaaa> — Toggle the bounty system (default: true)

Related settings for reward pools, reputation thresholds, and streak bonuses are configurable in the Misc tab.

---

## <#55ffff>Database</#55ffff>

<#aaaaaa>DatabaseEnabled</#aaaaaa> — Toggle SQL database sync (default: false)

When enabled, player data (bounties, leaderboard) syncs to a database (SQLite, MySQL, MariaDB, PostgreSQL). Configuration is in <#aaaaaa>DatabaseConfig.json</#aaaaaa>.

---

## <#aaaaaa>Miscellaneous</#aaaaaa>

<#aaaaaa>EnableDungeonBlockProtection</#aaaaaa> — Protect dungeon blocks (default: <#55ff55>true</#55ff55>)

! Changed in v4.0.0: Block protection is now <#55ff55>enabled by default</#55ff55> for both the Frozen Dungeon and Swamp Dungeon. Disable in <#55ffff>/eg config</#55ffff> Misc tab if you want players to break/place blocks inside dungeons.

<#aaaaaa>VorthakEnabled</#aaaaaa> — Enable/disable Vorthak merchant spawning in the Forgotten Temple (default: <#55ff55>true</#55ff55>)

---

## <#55ff55>RPG Leveling Integration</#55ff55>

<#aaaaaa>RPGLevelingEnabled</#aaaaaa> — Toggle XP rewards from boss kills (auto-detected)

Fully optional. If Zuxaw:RPGLeveling is installed, it activates automatically. Per-boss XP rewards are configurable in the Integration tab.

---

## <#a78bfa>Endless Leveling Integration</#a78bfa>

<#aaaaaa>EndlessLevelingEnabled</#aaaaaa> — Toggle XP rewards from boss kills (auto-detected)

Fully optional. If com.airijko:EndlessLeveling is installed, it activates automatically on first boot. All settings below appear in the Integration tab when enabled.

### XP Sources

Boss kill XP is shared between RPG Leveling and Endless Leveling (same values, configurable per boss in the Integration tab). Additionally, XP is awarded for:

| Activity | Default XP | Config Key |
|:---------|---:|:-----------|
| Boss Kill | per boss | <#aaaaaa>XP Rewards per Boss</#aaaaaa> (shared) |
| Warden Trial | tier x 150 | <#aaaaaa>EndlessLevelingWardenXpBase</#aaaaaa> |
| Achievement Claim | 50 | <#aaaaaa>EndlessLevelingAchievementXp</#aaaaaa> |
| Bounty Complete | per bounty | from bounty template |

### Party XP Sharing

<#aaaaaa>EndlessLevelingXpShareRange</#aaaaaa> — Radius for boss kill XP sharing with nearby players (default: 30m, range: 5-100m)

When a boss is killed, all party members within this radius receive the XP reward (split by Endless Leveling's built-in party system).

### Instance Levels

Endless Leveling automatically scales mob levels inside endgame instances:

| Instance | Mob Levels | Boss Level |
|:---------|:-----------|:-----------|
| Frozen Dungeon | 10-25 | Lv30 |
| Swamp Dungeon | 30-45 | Lv50 |

### Mob Level Overrides

Boss NPC levels are cleared from Endless Leveling's cache on death to prevent memory leaks.

---

## <#60a5fa>OrbisGuard Integration</#60a5fa>

<#aaaaaa>OrbisGuardEnabled</#aaaaaa> — Toggle automatic instance protection (<#ff6600>disabled by default</#ff6600>)

Fully optional. If OrbisGuard:OrbisGuard is installed and enabled in <#55ffff>/eg config</#55ffff>, EndgameQoL automatically creates protection regions on dungeon instance worlds.

### How It Works

1. EndgameQoL watches for new instance worlds every 5 seconds
2. When a world name matches <#55ffff>frozen_dungeon</#55ffff>, <#55ffff>swamp_dungeon</#55ffff>, or <#55ffff>golem_void</#55ffff>, a GLOBAL protection region is created
3. Configured flags are applied to the region
4. Regions are cleaned up when the instance world is removed

### Settings

| Setting | Default | Description |
|:--------|:--------|:------------|
| Block Build | <#55ff55>true</#55ff55> | Prevent block placement and breaking |
| Block PvP | <#55ff55>true</#55ff55> | Disable PvP inside instances |
| Blocked Commands | /spawn, /sethome, /tpa | Commands blocked inside instances |

All settings appear in the <#55ffff>Integration</#55ffff> tab of <#55ffff>/eg config</#55ffff> when OrbisGuard is enabled.

! OrbisGuard protection works independently from EndgameQoL's built-in block protection (<#aaaaaa>EnableDungeonBlockProtection</#aaaaaa>). You can use both — OrbisGuard adds PvP and command blocking on top.

! Changes to OrbisGuard settings require a server restart.

---

<buttons>
    <button topic="index">Home</button>
    <button topic="commands">Commands</button>
    <button topic="bosses">Bosses & Elites</button>
    <button topic="crafting">Crafting</button>
</buttons>
