package endgame.plugin.utils;

import java.util.*;

/**
 * Static registry of all endgame NPC data for the bestiary UI.
 * Data sourced from NPC role JSON definitions and drop tables. Avoids runtime asset parsing.
 *
 * Icon files are stored in Common/UI/Custom/Bestiary/ and referenced via
 * HyUI static img (requires @2x.png suffix on disk).
 */
public final class BestiaryRegistry {

    public enum Category {
        BOSS("#ff5555", "Boss"),
        ELITE("#ff8800", "Elite"),
        ELEMENTAL("#55aaff", "Elemental"),
        CREATURE("#55ff55", "Creature"),
        ENDGAME("#bb88ff", "Endgame"),
        SPECIAL("#FFD700", "Special");

        private final String color;
        private final String label;

        Category(String color, String label) {
            this.color = color;
            this.label = label;
        }

        public String getColor() { return color; }
        public String getLabel() { return label; }
    }

    public record KillMilestone(int threshold, String dropTable) {}

    private static final Map<Category, List<KillMilestone>> KILL_MILESTONES = Map.of(
        Category.BOSS, List.of(
            new KillMilestone(3, "Endgame_Drop_Reward_10"),
            new KillMilestone(10, "Endgame_Drop_Reward_20"),
            new KillMilestone(25, "Endgame_Drop_Bounty_Weekly")),
        Category.ELITE, List.of(
            new KillMilestone(5, "Endgame_Drop_Reward_5"),
            new KillMilestone(15, "Endgame_Drop_Reward_10"),
            new KillMilestone(30, "Endgame_Drop_Reward_20")),
        Category.ELEMENTAL, List.of(
            new KillMilestone(10, "Endgame_Drop_Reward_5"),
            new KillMilestone(25, "Endgame_Drop_Reward_10"),
            new KillMilestone(50, "Endgame_Drop_Reward_15")),
        Category.CREATURE, List.of(
            new KillMilestone(15, "Endgame_Drop_Reward_5"),
            new KillMilestone(40, "Endgame_Drop_Reward_5"),
            new KillMilestone(75, "Endgame_Drop_Reward_10")),
        Category.ENDGAME, List.of(
            new KillMilestone(10, "Endgame_Drop_Reward_5"),
            new KillMilestone(25, "Endgame_Drop_Reward_10"),
            new KillMilestone(50, "Endgame_Drop_Reward_20")),
        Category.SPECIAL, List.of(
            new KillMilestone(3, "Endgame_Drop_Reward_10"),
            new KillMilestone(10, "Endgame_Drop_Reward_20"),
            new KillMilestone(25, "Endgame_Drop_Bounty_Weekly"))
    );

    public static final int[] DISCOVERY_MILESTONES = {5, 10, 15, 20, 25, -1}; // -1 = all
    public static final String[] DISCOVERY_DROP_TABLES = {
        "Endgame_Drop_Reward_5", "Endgame_Drop_Reward_10", "Endgame_Drop_Reward_10",
        "Endgame_Drop_Reward_15", "Endgame_Drop_Reward_20", "Endgame_Drop_Bounty_Weekly"
    };
    static { assert DISCOVERY_MILESTONES.length == DISCOVERY_DROP_TABLES.length; }

    public static List<KillMilestone> getKillMilestones(Category category) {
        return KILL_MILESTONES.getOrDefault(category, List.of());
    }

    public record MobInfo(
        String npcTypeId,
        String displayName,
        Category category,
        int health,
        String damageInfo,
        String description,
        String[] notableDrops,
        String location,
        String iconFile
    ) {}

    private static final LinkedHashMap<String, MobInfo> ALL = new LinkedHashMap<>();

    static {
        // === BOSSES ===
        reg("Endgame_Dragon_Frost", "Dragon Frost", Category.BOSS, 1400,
            "27 Physical + 10 Ice",
            "An ancient frost wyrm guarding the frozen depths. Its breath can freeze a warrior solid.",
            new String[]{"Dragon Heart", "Mithril Bar", "Storm Hide", "Ice Essence", "Frozen Sword"},
            "Frozen Dungeon", "Dragon_Frost.png");

        reg("Endgame_Hedera", "Hedera", Category.BOSS, 1800,
            "Poison + Root",
            "A colossal swamp guardian wreathed in toxic vines. Its roots span the entire dungeon floor.",
            new String[]{"Hedera Gem", "Onyxium Bar", "Forest Essence", "Voidheart"},
            "Swamp Dungeon", "Hedera.png");

        reg("Endgame_Golem_Void", "Golem Void", Category.BOSS, 3500,
            "AOE Slam",
            "A towering construct of void energy. Three devastating phases of increasing rage.",
            new String[]{"Onyxium Bar", "Prisma Bar", "Emerald", "Portal Luminia"},
            "Void Instance", null);

        // === ELITES ===
        reg("Endgame_Dragon_Fire", "Dragon Fire", Category.ELITE, 1000,
            "27 Physical + 10 Fire",
            "A wild fire dragon that roams the overworld. Fiercely territorial and relentless in pursuit.",
            new String[]{"Storm Hide", "Adamantite Bar", "Voidheart"},
            "Summoned (Spawner)", null);
        reg("Alpha_Rex", "Alpha Rex", Category.ELITE, 700,
            "80 Physical",
            "The apex predator. Massive jaws and thundering charge make it the most feared beast in the wilds.",
            new String[]{"Rex Leather", "Raw Alpha Rex Meat"},
            "Zone 4 — Jungles", "Alpha_Rex.png");

        reg("Endgame_Swamp_Crocodile", "Swamp Crocodile", Category.ELITE, 900,
            "Physical",
            "A massive crocodilian lurking in the murky swamps. Its ambush strikes are nearly unavoidable.",
            new String[]{"Swamp Crocodile Scale", "Bone Fragment", "Void Essence", "Onyxium Bar"},
            "Swamp Dungeon", "Swamp_Crocodile.png");

        // === ELEMENTALS ===
        reg("Endgame_Golem_Crystal_Frost", "Golem Crystal Frost", Category.ELEMENTAL, 300,
            "Physical",
            "A crystalline construct infused with frost energy. Shards of ice orbit its body.",
            new String[]{"Ice Essence", "Sapphire", "Cobalt Bar", "Flocon"},
            "Frozen Dungeon", "Golem_Crystal_Frost.png");

        reg("Spirit_Frost", "Spirit Frost", Category.ELEMENTAL, 200,
            "Ice",
            "An ethereal frost spirit drifting through frozen caves. Its touch chills to the bone.",
            new String[]{"Ice Essence", "Flocon"},
            "Frozen Dungeon", "Spirit_Frost.png");

        reg("Spirit_Root", "Spirit Root", Category.ELEMENTAL, 200,
            "Nature",
            "A restless nature spirit haunting the swamp. Entangles prey with spectral roots.",
            new String[]{"Forest Essence"},
            "Swamp Dungeon", "Spirit_Root.png");

        reg("Spectre_Void", "Spectre Void", Category.ELEMENTAL, 120,
            "50 Physical",
            "A flickering void apparition. Fast and unpredictable, phasing in and out of reality.",
            new String[]{"Void Essence", "Voidheart"},
            "Void Instance", "Spectre_Void.png");

        reg("Golem_Eye_Void", "Golem Eye Void", Category.ELEMENTAL, 250,
            "25 Physical",
            "A floating void sentinel with a piercing crystalline eye. Guards the rift passages.",
            new String[]{},
            "Void Instance", "Golem_Eye_Void.png");

        // === CREATURES ===
        reg("Endgame_Yeti", "Yeti", Category.CREATURE, 400,
            "Physical",
            "A towering snow beast of the frozen wastes. Territorial and surprisingly swift for its size.",
            new String[]{"Heavy Hide", "Storm Hide", "Ice Essence", "Mithril Bar", "Flocon"},
            "Frozen Dungeon", "Yeti.png");

        reg("Endgame_Rat_Frost", "Rat Frost", Category.CREATURE, 35,
            "Physical",
            "A frost-touched rodent skittering through icy tunnels. Harmless alone, dangerous in packs.",
            new String[]{"Light Hide", "Raw Wildmeat", "Flocon"},
            "Frozen Dungeon", "Rat_Frost.png");

        reg("Endgame_Toad_Frost", "Toad Frost", Category.CREATURE, 124,
            "18-35 Physical",
            "An oversized amphibian adapted to the cold. Its tongue strike pulls prey close.",
            new String[]{"Medium Hide", "Ice Essence", "Raw Wildmeat", "Flocon"},
            "Frozen Dungeon", "Toad_Frost.png");

        reg("Void_Frog", "Void Frog", Category.CREATURE, 15,
            "Passive",
            "A tiny frog suffused with void energy. Hops aimlessly through the rift, seemingly unaffected.",
            new String[]{},
            "Void Instance", "Void_Frog.png");

        reg("Endgame_Bramble_Elite", "Bramble Elite", Category.ELITE, 550,
            "90 Physical (Bite) + 70 Physical (Swipe) + Poison T3",
            "A massive thorned beast lurking in the deepest reaches of the swamp dungeon. Its 3-attack combo chain delivers devastating blows alongside a potent toxin.",
            new String[]{"Hedera's Bramble"},
            "Swamp Dungeon", "Bramble_Elite.png");

        // === ENDGAME ===
        reg("Endgame_Ghoul", "Ghoul", Category.ENDGAME, 193,
            "Physical",
            "A decaying horror that prowls at night. Relentless and drawn to the scent of the living.",
            new String[]{"Bone Fragment", "Void Essence"},
            "Zone 4 — Wastes", "Ghoul.png");

        reg("Endgame_Shadow_Knight", "Shadow Knight", Category.ENDGAME, 400,
            "Physical",
            "A fallen warrior consumed by darkness. Still fights with disciplined swordsmanship.",
            new String[]{"Bone Fragment", "Mithril Bar", "Voidheart"},
            "Warden Trial", "Shadow_Knight.png");

        reg("Endgame_Werewolf", "Werewolf", Category.ENDGAME, 283,
            "Physical",
            "A cursed beast that hunts under moonlight. Savage claws and unnatural speed.",
            new String[]{"Bone Fragment", "Storm Hide", "Void Essence"},
            "Warden Trial", "Werewolf.png");

        reg("Endgame_Saurian_Warrior", "Saurian Warrior", Category.ENDGAME, 220,
            "Physical",
            "The frontline fighters of the Saurian clans. Heavy-hitting and well-armored.",
            new String[]{"Adamantite Bar", "Void Essence"},
            "Zone 4 — Jungles", null);

        reg("Endgame_Saurian_Hunter", "Saurian Hunter", Category.ENDGAME, 180,
            "Physical",
            "Agile Saurian scouts that stalk prey through the wastes. Quick and precise.",
            new String[]{"Adamantite Bar", "Void Essence"},
            "Zone 4 — Jungles", null);

        reg("Endgame_Saurian_Rogue", "Saurian Rogue", Category.ENDGAME, 150,
            "Physical",
            "Cunning Saurian ambushers. Strike from the shadows with poisoned blades.",
            new String[]{"Adamantite Bar", "Void Essence"},
            "Zone 4 — Jungles", null);

        reg("Endgame_Goblin_Duke", "Goblin Duke", Category.ENDGAME, 350,
            "Club",
            "A brutish goblin warlord commanding from a fortified camp. Hits hard with a massive club.",
            new String[]{"Adamantite Bar", "Void Essence", "Mithril Bar"},
            "Warden Trial", "Goblin_Duke.png");

        reg("Scarak_Fighter", "Scarak Fighter", Category.ENDGAME, 81,
            "Physical",
            "Chitinous insectoid warriors that swarm from underground nests. Never found alone.",
            new String[]{"Sturdy Chitin", "Venom Sac"},
            "Zone 3 — Caves", "Scarak_Fighter.png");

        reg("Scarak_Broodmother", "Scarak Broodmother", Category.ENDGAME, 145,
            "Physical",
            "The egg-laying matriarch of a Scarak colony. Protected fiercely by her swarm.",
            new String[]{"Venom Sac", "Sturdy Chitin", "Adamantite Bar"},
            "Zone 3 — Caves", "Scarak_Broodmother.png");

        reg("Endgame_Necromancer_Void", "Necromancer Void", Category.ENDGAME, 500,
            "Magic",
            "A void-touched sorcerer who commands the dead. Summons minions and hurls dark bolts.",
            new String[]{"Bone Fragment", "Void Essence", "Voidheart"},
            "Warden Trial", null);

        reg("Outlander_Brute", "Outlander Brute", Category.ENDGAME, 124,
            "Axe",
            "A hulking outlaw wielding a crude battleaxe. Slow but devastatingly powerful swings.",
            new String[]{"Adamantite Bar", "Shadoweave Fabric"},
            "Warden Trial", "Outlander_Brute.png");

        // === SPECIAL ===
        reg("Onyxium_Encounter", "Onyxium Encounter", Category.SPECIAL, 700,
            "Daggers",
            "A mysterious dual-wielding assassin that appears without warning. Onyxium blades gleam in the dark.",
            new String[]{"Onyxium Bar", "Voidheart", "Storm Hide", "Onyxium Weapon/Armor"},
            "Zone 4 — Random", null);

        reg("Zombie_Aberrant", "Zombie Aberrant", Category.ELITE, 400,
            "119 Physical",
            "A grotesque undead monstrosity found in ancient crypts. Its strength defies its decaying form.",
            new String[]{"Voidheart", "Adamantite Bar"},
            "Zone 4 — Wastes", "Zombie_Aberrant.png");

        reg("Endgame_Fen_Stalker", "Fen Stalker", Category.CREATURE, 200,
            "29 Physical",
            "A swift amphibian predator lurking in murky waters. Its powerful jaws snap shut before prey can react.",
            new String[]{"Raw Wildmeat", "Medium Hide"},
            "Swamp Dungeon", null);

        reg("Endgame_Grooble", "Grooble", Category.CREATURE, 250,
            "10 Physical",
            "A burrowing beast that erupts from the ground. Its stone-covered hide deflects weak blows.",
            new String[]{"Raw Wildmeat"},
            "Swamp Dungeon", null);

        reg("Frost_Feran", "Frost Feran", Category.CREATURE, 150,
            "Sword",
            "A frost-touched Feran warrior lurking in the frozen dungeon. Wields a bone sword with eerie skill.",
            new String[]{"Medium Hide", "Cobalt Bar", "Bone Sword", "Flocon"},
            "Frozen Dungeon", "Frost_Feran.png");
    }

    private static void reg(String id, String name, Category cat, int hp,
                             String dmg, String desc, String[] drops, String loc, String icon) {
        ALL.put(id, new MobInfo(id, name, cat, hp, dmg, desc, drops, loc, icon));
    }

    public static MobInfo get(String npcTypeId) {
        return ALL.get(npcTypeId);
    }

    public static Collection<MobInfo> getAll() {
        return Collections.unmodifiableCollection(ALL.values());
    }

    public static List<MobInfo> getByCategory(Category category) {
        return ALL.values().stream()
                .filter(m -> m.category() == category)
                .toList();
    }

    public static int getTotalCount() {
        return ALL.size();
    }

    private BestiaryRegistry() {}
}
