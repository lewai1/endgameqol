---
name: Combo Meter
description: Kill streak tracker with damage tiers and effects
author: Lewai
---

# <gradient data="#ff5555:#ff6600:#FFD700">Combo Meter</gradient>

The Combo Meter tracks your kill streaks and rewards consecutive kills with escalating damage bonuses and special effects.

---

## <#ff6600>Damage Tiers</#ff6600>

Build your combo by killing enemies in quick succession. Each tier multiplies your damage output.

| Tier | Multiplier | Name |
|:-----|:-----------|:-----|
| 1 | <#55ffff>x2 damage</#55ffff> | — |
| 2 | <#55ff55>x3 damage</#55ff55> | — |
| 3 | <#ff6600>x4 damage</#ff6600> | — |
| 4 | <#ff5555>MAX</#ff5555> | <#ff5555>FRENZY</#ff5555> |

---

## <#d16eff>Tier Effects</#d16eff>

Each damage tier can optionally grant special effects:

**Speed Boost** — Increased movement speed during combo

**Heal on Kill** — Restore HP with each kill

**Crit Chance** — Bonus critical hit chance

**Lifesteal** — Heal a percentage of damage dealt

Effects are configurable per tier in <#55ffff>/egconfig</#55ffff> under the Misc tab.

---

## <#55ffff>HUD Overlay</#55ffff>

A real-time HUD overlay displays your current combo count, active damage tier, and decay timer. Your personal best combo streak is also tracked.

---

## <#aaaaaa>Combo Decay</#aaaaaa>

Your combo decays over time if you don't score another kill. The decay timer is configurable. When the timer expires, your combo resets to zero.

**Combo also resets on death.**

---

## <#ff5555>Configuration</#ff5555>

<#aaaaaa>ComboEnabled</#aaaaaa> — Toggle the combo system on/off (default: true)

All tier thresholds, damage multipliers, effects, and decay timers are configurable in <#55ffff>/egconfig</#55ffff> under the Misc tab.

---

<buttons>
    <button topic="index">Home</button>
    <button topic="achievements">Achievements</button>
    <button topic="bounty-system">Bounty System</button>
    <button topic="configuration">Configuration</button>
</buttons>
