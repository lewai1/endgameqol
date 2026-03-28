package endgame.plugin.managers;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bounty definitions — static template pool for daily bounty generation.
 * Each template defines type, target, count, difficulty, and description.
 */
public class BountyTemplate {

    public enum BountyType {
        KILL_NPC,
        KILL_ANY_BOSS,
        COMPLETE_TRIAL,
        COMBO_TIER,
        SPEED_KILL_BOSS,
        KILL_UNIQUE_BOSSES,
        REACH_FRENZY_COUNT,
        KILL_ENDGAME_NPCS,
        CRAFT_ITEM,
        DUNGEON_CLEAR,
        DAMAGE_DEALT,
        MINE_ORE,
        EXPLORE_DUNGEON
    }

    public enum BountyDifficulty {
        EASY, MEDIUM, HARD, WEEKLY
    }

    /**
     * Cross-system bonus objective types.
     * Each bounty can have a hidden bonus tied to another system.
     */
    public enum BonusType {
        NONE("", ""),
        COMBO_X3("combo_x3", "while at Combo x3+"),
        COMBO_FRENZY("combo_frenzy", "while maintaining FRENZY"),
        DURING_GAUNTLET("during_gauntlet", "during a Gauntlet run"), // Kept for save compatibility
        AT_FULL_HP("at_full_hp", "at full HP");

        private final String id;
        private final String description;

        BonusType(String id, String description) {
            this.id = id;
            this.description = description;
        }

        public String getId() { return id; }
        public String getDescription() { return description; }

        private static final Map<String, BonusType> BY_ID = Map.of(
                "combo_x3", COMBO_X3, "combo_frenzy", COMBO_FRENZY,
                "during_gauntlet", DURING_GAUNTLET, "at_full_hp", AT_FULL_HP);

        public static BonusType fromId(String id) {
            return BY_ID.getOrDefault(id, NONE);
        }
    }

    private final String id;
    private final BountyType type;
    private final String target;      // NPC type ID, boss type, item ID, or null
    private final int count;          // required count
    private final BountyDifficulty difficulty;
    private final String description;
    private final String rewardDropTable;
    private final int xpReward;       // RPG Leveling XP reward (0 = none)
    private final int reputationReward; // Reputation points awarded on claim

    public BountyTemplate(String id, BountyType type, String target, int count,
                          BountyDifficulty difficulty, String description, String rewardDropTable) {
        this(id, type, target, count, difficulty, description, rewardDropTable, 0, 0);
    }

    public BountyTemplate(String id, BountyType type, String target, int count,
                          BountyDifficulty difficulty, String description, String rewardDropTable,
                          int xpReward, int reputationReward) {
        this.id = id;
        this.type = type;
        this.target = target;
        this.count = count;
        this.difficulty = difficulty;
        this.description = description;
        this.rewardDropTable = rewardDropTable;
        this.xpReward = xpReward;
        this.reputationReward = reputationReward;
    }

    public String getId() { return id; }
    public BountyType getType() { return type; }
    public String getTarget() { return target; }
    public int getCount() { return count; }
    public BountyDifficulty getDifficulty() { return difficulty; }
    public String getDescription() { return description; }
    public String getRewardDropTable() { return rewardDropTable; }
    public int getXpReward() { return xpReward; }
    public int getReputationReward() { return reputationReward; }


    private static final List<BountyTemplate> EASY_POOL = List.of(
        new BountyTemplate("easy_saurian_10", BountyType.KILL_NPC, "Saurian_Warrior", 10,
                BountyDifficulty.EASY, "Slay 10 Saurian Warriors", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_werewolf_5", BountyType.KILL_NPC, "Werewolf", 5,
                BountyDifficulty.EASY, "Slay 5 Werewolves", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_ghoul_15", BountyType.KILL_NPC, "Ghoul", 15,
                BountyDifficulty.EASY, "Slay 15 Ghouls", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_scarak_10", BountyType.KILL_NPC, "Scarak_Fighter", 10,
                BountyDifficulty.EASY, "Slay 10 Scarak Fighters", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_shadow_3", BountyType.KILL_NPC, "Shadow_Knight", 3,
                BountyDifficulty.EASY, "Slay 3 Shadow Knights", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_combo_2", BountyType.COMBO_TIER, null, 2,
                BountyDifficulty.EASY, "Reach Combo x3 Tier", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_trial_1", BountyType.COMPLETE_TRIAL, null, 1,
                BountyDifficulty.EASY, "Complete a Warden Challenge (Tier I+)", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_rat_frost_12", BountyType.KILL_NPC, "Endgame_Rat_Frost", 12,
                BountyDifficulty.EASY, "Slay 12 Frost Rats (Frozen Dungeon)", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_toad_frost_8", BountyType.KILL_NPC, "Endgame_Toad_Frost", 8,
                BountyDifficulty.EASY, "Slay 8 Frost Toads (Frozen Dungeon)", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_spirit_root_6", BountyType.KILL_NPC, "Spirit_Root", 6,
                BountyDifficulty.EASY, "Slay 6 Root Spirits", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_saurian_hunter_8", BountyType.KILL_NPC, "Endgame_Saurian_Hunter", 8,
                BountyDifficulty.EASY, "Slay 8 Saurian Hunters (Zone 4)", "Endgame_Drop_Reward_5"),
        new BountyTemplate("easy_saurian_rogue_6", BountyType.KILL_NPC, "Endgame_Saurian_Rogue", 6,
                BountyDifficulty.EASY, "Slay 6 Saurian Rogues (Zone 4)", "Endgame_Drop_Reward_5", 50, 1),
        new BountyTemplate("easy_craft_mithril_ore_3", BountyType.CRAFT_ITEM, "Ore_Mithril", 3,
                BountyDifficulty.EASY, "Craft 3 Mithril Ore", "Endgame_Drop_Reward_5", 40, 1),
        new BountyTemplate("easy_damage_500", BountyType.DAMAGE_DEALT, null, 500,
                BountyDifficulty.EASY, "Deal 500 damage to bosses", "Endgame_Drop_Reward_5", 60, 1),
        new BountyTemplate("easy_mine_mithril_5", BountyType.MINE_ORE, "OreMithril", 5,
                BountyDifficulty.EASY, "Mine 5 Mithril Ore", "Endgame_Drop_Reward_5", 40, 1),
        new BountyTemplate("easy_mine_adamantite_3", BountyType.MINE_ORE, "OreAdamantite", 3,
                BountyDifficulty.EASY, "Mine 3 Adamantite Ore", "Endgame_Drop_Reward_5", 50, 1),
        new BountyTemplate("easy_explore_dungeon", BountyType.EXPLORE_DUNGEON, null, 1,
                BountyDifficulty.EASY, "Enter a dungeon", "Endgame_Drop_Reward_5", 40, 1),
        new BountyTemplate("easy_craft_adamantite_3", BountyType.CRAFT_ITEM, "Ore_Adamantite", 3,
                BountyDifficulty.EASY, "Craft 3 Adamantite Ore", "Endgame_Drop_Reward_5", 40, 1)
    );

    private static final List<BountyTemplate> MEDIUM_POOL = List.of(
        new BountyTemplate("med_rex_2", BountyType.KILL_NPC, "Alpha_Rex", 2,
                BountyDifficulty.MEDIUM, "Slay 2 Alpha Rex", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_duke_3", BountyType.KILL_NPC, "Goblin_Duke", 3,
                BountyDifficulty.MEDIUM, "Slay 3 Goblin Dukes", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_boss_1", BountyType.KILL_ANY_BOSS, null, 1,
                BountyDifficulty.MEDIUM, "Defeat any Boss", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_trial_2", BountyType.COMPLETE_TRIAL, null, 2,
                BountyDifficulty.MEDIUM, "Complete Warden Challenge (Tier II+)", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_trial_3", BountyType.COMPLETE_TRIAL, null, 3,
                BountyDifficulty.MEDIUM, "Complete Warden Challenge (Tier III+)", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_combo_3", BountyType.COMBO_TIER, null, 3,
                BountyDifficulty.MEDIUM, "Reach Combo x4 Tier", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_necro_3", BountyType.KILL_NPC, "Necromancer_Void", 3,
                BountyDifficulty.MEDIUM, "Slay 3 Void Necromancers", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_yeti_2", BountyType.KILL_NPC, "Endgame_Yeti", 2,
                BountyDifficulty.MEDIUM, "Slay 2 Yetis (Frozen Dungeon)", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_croc_4", BountyType.KILL_NPC, "Endgame_Swamp_Crocodile", 4,
                BountyDifficulty.MEDIUM, "Slay 4 Swamp Crocodiles (Swamp Dungeon)", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_outlander_3", BountyType.KILL_NPC, "Outlander_Brute", 3,
                BountyDifficulty.MEDIUM, "Slay 3 Outlander Brutes", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_frost_feran_4", BountyType.KILL_NPC, "Frost_Feran", 4,
                BountyDifficulty.MEDIUM, "Slay 4 Frost Feran (Frozen Dungeon)", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_spirit_frost_5", BountyType.KILL_NPC, "Spirit_Frost", 5,
                BountyDifficulty.MEDIUM, "Slay 5 Frost Spirits (Frozen Dungeon)", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_golem_frost_2", BountyType.KILL_NPC, "Endgame_Golem_Crystal_Frost", 2,
                BountyDifficulty.MEDIUM, "Slay 2 Frost Golems (Frozen Dungeon)", "Endgame_Drop_Reward_10"),
        new BountyTemplate("med_craft_mithril_sword", BountyType.CRAFT_ITEM, "Weapon_Sword_Mithril", 1,
                BountyDifficulty.MEDIUM, "Craft a Mithril Sword", "Endgame_Drop_Reward_10", 120, 2),
        new BountyTemplate("med_dungeon_clear_1", BountyType.DUNGEON_CLEAR, null, 1,
                BountyDifficulty.MEDIUM, "Clear a dungeon instance", "Endgame_Drop_Reward_10", 150, 2),
        new BountyTemplate("med_damage_2000", BountyType.DAMAGE_DEALT, null, 2000,
                BountyDifficulty.MEDIUM, "Deal 2000 damage to bosses", "Endgame_Drop_Reward_10", 120, 2),
        new BountyTemplate("med_mine_adamantite_10", BountyType.MINE_ORE, "OreAdamantite", 10,
                BountyDifficulty.MEDIUM, "Mine 10 Adamantite Ore", "Endgame_Drop_Reward_10", 100, 2),
        new BountyTemplate("med_mine_mithril_15", BountyType.MINE_ORE, "OreMithril", 15,
                BountyDifficulty.MEDIUM, "Mine 15 Mithril Ore", "Endgame_Drop_Reward_10", 100, 2),
        new BountyTemplate("med_explore_frozen", BountyType.EXPLORE_DUNGEON, "frozen_dungeon", 1,
                BountyDifficulty.MEDIUM, "Enter the Frozen Dungeon", "Endgame_Drop_Reward_10", 120, 2),
        new BountyTemplate("med_craft_onyxium_sword", BountyType.CRAFT_ITEM, "Weapon_Sword_Onyxium", 1,
                BountyDifficulty.MEDIUM, "Craft an Onyxium Sword", "Endgame_Drop_Reward_10", 120, 2)
    );

    private static final List<BountyTemplate> HARD_POOL = List.of(
        new BountyTemplate("hard_boss_2", BountyType.KILL_ANY_BOSS, null, 2,
                BountyDifficulty.HARD, "Defeat 2 Bosses", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_trial_4", BountyType.COMPLETE_TRIAL, null, 4,
                BountyDifficulty.HARD, "Complete Warden Challenge (Tier IV)", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_combo_4", BountyType.COMBO_TIER, null, 4,
                BountyDifficulty.HARD, "Reach FRENZY Combo Tier", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_speed_hedera", BountyType.SPEED_KILL_BOSS, "Endgame_Hedera", 240,
                BountyDifficulty.HARD, "Defeat Hedera in under 4 minutes", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_speed_dragon", BountyType.SPEED_KILL_BOSS, "Endgame_Dragon_Frost", 180,
                BountyDifficulty.HARD, "Defeat Frost Dragon in under 3 minutes", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_spectre_5", BountyType.KILL_NPC, "Spectre_Void", 5,
                BountyDifficulty.HARD, "Slay 5 Void Spectres", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_aberrant_2", BountyType.KILL_NPC, "Zombie_Aberrant", 2,
                BountyDifficulty.HARD, "Slay 2 Zombie Aberrants", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_broodmother_1", BountyType.KILL_NPC, "Scarak_Broodmother", 1,
                BountyDifficulty.HARD, "Slay a Scarak Broodmother", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_speed_golem", BountyType.SPEED_KILL_BOSS, "Endgame_Golem_Void", 300,
                BountyDifficulty.HARD, "Defeat Void Golem in under 5 minutes", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_boss_3", BountyType.KILL_ANY_BOSS, null, 3,
                BountyDifficulty.HARD, "Defeat 3 Bosses", "Endgame_Drop_Reward_20"),
        new BountyTemplate("hard_craft_prisma_sword", BountyType.CRAFT_ITEM, "Endgame_Sword_Prisma", 1,
                BountyDifficulty.HARD, "Craft a Prisma Sword", "Endgame_Drop_Reward_20", 250, 3),
        new BountyTemplate("hard_dungeon_clear_3", BountyType.DUNGEON_CLEAR, null, 3,
                BountyDifficulty.HARD, "Clear 3 dungeon instances", "Endgame_Drop_Reward_20", 300, 3),
        new BountyTemplate("hard_damage_5000", BountyType.DAMAGE_DEALT, null, 5000,
                BountyDifficulty.HARD, "Deal 5000 damage to bosses", "Endgame_Drop_Reward_20", 250, 3),
        new BountyTemplate("hard_mine_adamantite_25", BountyType.MINE_ORE, "OreAdamantite", 25,
                BountyDifficulty.HARD, "Mine 25 Adamantite Ore", "Endgame_Drop_Reward_20", 200, 3),
        new BountyTemplate("hard_explore_both", BountyType.EXPLORE_DUNGEON, null, 2,
                BountyDifficulty.HARD, "Enter both dungeon types", "Endgame_Drop_Reward_20", 250, 3),
        new BountyTemplate("hard_craft_hedera", BountyType.CRAFT_ITEM, "Weapon_Daggers_Hedera", 1,
                BountyDifficulty.HARD, "Craft Hedera Daggers", "Endgame_Drop_Reward_20", 250, 3)
    );

    private static final List<BountyTemplate> WEEKLY_POOL = List.of(
        new BountyTemplate("weekly_warlord", BountyType.KILL_UNIQUE_BOSSES, null, 5,
                BountyDifficulty.WEEKLY, "Defeat 5 unique boss types", "Endgame_Drop_Bounty_Weekly"),
        new BountyTemplate("weekly_combo_master", BountyType.REACH_FRENZY_COUNT, null, 5,
                BountyDifficulty.WEEKLY, "Reach FRENZY 5 times", "Endgame_Drop_Bounty_Weekly"),
        new BountyTemplate("weekly_dungeon_crawler", BountyType.COMPLETE_TRIAL, null, 3,
                BountyDifficulty.WEEKLY, "Complete 3 Warden Challenges (Tier III+)", "Endgame_Drop_Bounty_Weekly"),
        new BountyTemplate("weekly_exterminator", BountyType.KILL_ENDGAME_NPCS, null, 100,
                BountyDifficulty.WEEKLY, "Kill 100 endgame NPCs", "Endgame_Drop_Bounty_Weekly"),
        new BountyTemplate("weekly_boss_slayer", BountyType.KILL_ANY_BOSS, null, 5,
                BountyDifficulty.WEEKLY, "Defeat 5 Bosses", "Endgame_Drop_Bounty_Weekly"),
        new BountyTemplate("weekly_craft_5_weapons", BountyType.CRAFT_ITEM, null, 5,
                BountyDifficulty.WEEKLY, "Craft 5 endgame weapons", "Endgame_Drop_Bounty_Weekly")
    );

    private static final Map<String, BountyTemplate> TEMPLATE_BY_ID =
            Stream.of(EASY_POOL, MEDIUM_POOL, HARD_POOL, WEEKLY_POOL)
                    .flatMap(List::stream)
                    .collect(Collectors.toUnmodifiableMap(BountyTemplate::getId, t -> t));

    public static List<BountyTemplate> getEasyPool() { return EASY_POOL; }
    public static List<BountyTemplate> getMediumPool() { return MEDIUM_POOL; }
    public static List<BountyTemplate> getHardPool() { return HARD_POOL; }
    public static List<BountyTemplate> getWeeklyPool() { return WEEKLY_POOL; }

    public static BountyTemplate getById(String id) {
        return TEMPLATE_BY_ID.get(id);
    }
}
