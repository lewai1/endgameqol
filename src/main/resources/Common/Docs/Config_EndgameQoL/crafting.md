---
name: Crafting & Shops
description: Workbench tiers, recipes, Vorthak trades, and shops
author: Lewai
---

# <gradient data="#55ffff:#ff6600:#55ff55">Crafting & Shops</gradient>

---

## <#55ffff>Endgame Workbench</#55ffff>

The <#55ffff>Endgame Workbench</#55ffff> is the central crafting station for all endgame content. It has 5 tiers that unlock progressively stronger recipes.

**Tier 1** — Warden Challenge I

**Tier 2** — Mithril Ore (if enabled in config), Mana Totem, Warden Challenge II

**Tier 3** — Mithril Sword, Mithril Daggers, Mithril Battleaxe, Mithril Mace, Mithril Shortbow, Mithril armor, Backpack Upgrades, Mithril Pickaxe

**Tier 4** — Void-Powered Glider, Onyxium weapons, Shard of the Void, Warden Challenge III

**Tier 5** — Prisma weapons (Sword, Daggers), Prisma armor, Warden Challenge IV

### Other Crafting Stations

**Weapon Bench (Tier 3)** — Longswords, Spears, and Staffs (Copper through Mithril), Mithril Sword, Mithril Battleaxe

**Armor Bench (Tier 3)** — Mithril armor set

**Workbench (Tier 3)** — Mithril Pickaxe, <#d16eff>Prisma Pickaxe</#d16eff>, <#d16eff>Prisma Hatchet</#d16eff>

**Alchemy Bench** — Mana potions (all sizes)

**Furnace** — Prisma Ore into Prisma Bars (4s)

**Campfire** — Cook Alpha Trex Meat (6s)

**<#55ff55>Hedera Autel</#55ff55>** — Hedera Key (found in Swamp Dungeon)

---

## <#ff6600>Key Recipes</#ff6600>

### Mithril Tier

**Mithril Longsword** — 10 Mithril Bars, 4 Storm Leather, 1 Voidheart (Endgame Bench Tier 3, 4s)

**Mithril Sword** — 6 Mithril Bars, 3 Storm Leather, 1 Voidheart (Weapon Bench Tier 3, 5s)

**Mithril Staff** — 8 Mithril Bars, 15 Void Essence, 3 Storm Leather, 3 Sticks (Endgame Bench Tier 3, 4s)

**Mithril Battleaxe** — 10 Mithril Bars, 4 Storm Leather, 1 Voidheart (Weapon Bench Tier 3, 5s)

**Mithril Armor (chest)** — 24 Mithril Bars, 8 Storm Leather, 80 Void Essence (Armor Bench Tier 3, 3s)

**Mithril Pickaxe** — 9 Mithril Bars, 4 Storm Leather (Workbench Tier 3, 5s)

### Prisma Tier

**Prisma Daggers** — 10 Prisma Bars, 4 Prismic Leather, 2 Emerald Gems (Endgame Bench Tier 5, 8s)

**Prisma Armor (chest)** — 8 Prisma Bars, 5 Prismic Leather, 1 Emerald Gem (Endgame Bench Tier 5, 4s)

**Prisma Pickaxe** — 10 Prisma Bars, 3 Prismic Leather, 1 Emerald Gem (Workbench, Tools category). Ability3 toggles 3x3 area mining.

**Prisma Hatchet** — Prisma Bars + Prismic Leather + Emerald Gem (Workbench, Tools category). Ability3 toggles 3x3 area mining.

### Swamp Dungeon

**Hedera Key** — 1 Swamp Ingot, 1 Crocodile Scale, 1 Swamp Gem, 1 Infused Rope, 1 Hedera's Bramble (Hedera Autel)

! <#55ff55>Hedera Daggers</#55ff55> are now trade-only (no crafting recipe). Available from <#55ffff>Morghul</#55ffff> in the Swamp Dungeon for 40 Swamp Currency.

!! <#ff6600>Onyxium Ore</#ff6600> crafting is disabled by default in v4.0.0.

### Warden's Trial

**Tier I** — 5 Adamantite Bars, 3 Storm Hides, 1 Mithril Bar (Bench Tier 1)

**Tier II** — 5 Mithril Bars, 3 Storm Hides, 2 Voidheart (Bench Tier 2)

**Tier III** — 5 Onyxium Bars, 3 Storm Hides, 3 Voidheart (Bench Tier 4)

**Tier IV** — 5 Prisma Bars, 5 Storm Hides, 5 Voidheart (Bench Tier 5)

### Portal Keys & Shards

**Frozen Dungeon Key** — 10 Adamantite Bars, 5 Mithril Bars, 30 Fire Essence (Endgame Bench Tier 2)

**Swamp Dungeon Key** — 10 Adamantite Bars, 5 Mithril Bars, 30 Fire Essence (Endgame Bench Tier 3)

**Shard of the Void** — 5 Onyxium Bars, 50 Void Essence (Endgame Bench Tier 4, toggleable)

**Void-Powered Glider** — 1 Onyxium Bar, 2 Prismic Leather, 10 Linen Fabric, 6 Sticks (Bench Tier 4, 5s)

**Endgame Backpack Upgrade I** — 1 Voidheart, 8 Adamantite Bars, 8 Apex Sovereign Leather (Bench Tier 3, 2s)

**Mana Totem** — 10 Life Essence, 5 Blue Crystal, 5 Thorium Bars (Bench Tier 2)

**Mana Potion** — 1 Empty Potion, 10 Life Essence, 5 Crystal Blue (Alchemy Bench, 1s)

**Prisma Bar** — 1 Prisma Ore (Furnace, 4s)

---

## <#aaaaaa>Toggleable Recipes</#aaaaaa>

Server admins can enable or disable specific recipes in <#55ffff>/egconfig</#55ffff> under the Crafting tab:

| Recipe | Default | Bench Tier |
|:-------|:-------:|:-----------|
| Void-Powered Glider | Enabled | Tier 4 |
| Mithril Ore | Disabled | Tier 2 |
| Frozen Dungeon Key | Enabled | Tier 2 |
| Swamp Dungeon Key | Enabled | Tier 3 |
| Shard of the Void | Enabled | Tier 4 |

! Disabled recipes are hidden from the crafting UI for all players. Changes apply immediately.

For full recipe customization (changing ingredients, outputs, bench tier, craft time), edit <#55ffff>RecipeOverrides.json</#55ffff> in <#aaaaaa>Saves/save/mods/Config_Endgame&QoL/</#aaaaaa>. Auto-generated on first boot; new recipes are auto-appended on plugin update. See the Configuration page for details. Requires a server restart.

---

## <#55ff55>Vorthak — Forgotten Temple Merchant</#55ff55>

Located in the Forgotten Temple area. Stock refreshes every time a player enters the Forgotten Temple.

### Fixed Trades (always available)

| You Get | Cost | Stock |
|:--------|:-----|------:|
| 3x Mana Potion (Large) | 1 Mithril Bar | 10 |
| 3x Mana Regen Potion (Large) | 1 Mithril Bar | 10 |
| 1x Apex Sovereign Leather | 4 Storm Leather | 6 |
| 1x Mithril Bar | 30 Adamantite Bars | 2 |

### Rotating Pool (3 random trades from 8 options)

| You Get | Cost |
|:--------|:-----|
| 3x Greater Health Potion | 2 Adamantite Bars |
| 3x Greater Stamina Potion | 2 Adamantite Bars |
| 5x Antidote | 1 Adamantite Bar |
| 3x Large Health Regen | 2 Adamantite Bars |
| 2x Greater Signature Potion | 3 Adamantite Bars |
| 3x Large Stamina Regen | 2 Adamantite Bars |
| 3x Purify Potion | 1 Adamantite Bar |
| 1x Concentrated Life Essence | 5 Adamantite Bars |

---

## <#55ff55>Morghul — Swamp Dungeon Trader</#55ff55>

Located inside the Swamp Dungeon. Trades <#55ffff>Swamp Currency</#55ffff> for various items.

| You Get | Cost |
|:--------|:-----|
| <#55ff55>Swamp Ingot</#55ff55> | 6 Swamp Currency |
| <#ff6600>Onyxium gear</#ff6600> | Various prices |
| <#55ff55>Hedera Daggers</#55ff55> | 40 Swamp Currency |
| Potions | Various prices |
| <#55ff55>Forest Essence</#55ff55> | Various prices |

! Hedera Daggers are trade-only — there is no crafting recipe.

---

## <#55ffff>Korvyn — Frozen Dungeon Merchant</#55ffff>

Hidden inside the Frozen Dungeon. Sells Mithril gear and potions for <#55ffff>Flocons</#55ffff> currency.

**<#55ffff>Frostbite Blade</#55ffff>** — 45 Flocons (stock: 3)
Plus Mithril weapons, potions, and consumables.

---

<buttons>
    <button topic="index">Home</button>
    <button topic="weapons">Weapons</button>
    <button topic="armor-tools">Armor & Tools</button>
    <button topic="configuration">Configuration</button>
</buttons>
