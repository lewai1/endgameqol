package endgame.plugin.managers;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Static pool of achievement definitions.
 * Each achievement has a category, target count, and reward.
 */
public class AchievementTemplate {

    public enum Category {
        COMBAT, BOSS, BOUNTY, DISCOVERY, CRAFTING, EXPLORATION, SPEEDRUN, MINING
    }

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final int target;
    private final int xpReward;
    private final String rewardDropTable; // null = no item reward

    public AchievementTemplate(String id, String name, String description,
                               Category category, int target, int xpReward, String rewardDropTable) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.target = target;
        this.xpReward = xpReward;
        this.rewardDropTable = rewardDropTable;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public int getTarget() { return target; }
    public int getXpReward() { return xpReward; }
    public String getRewardDropTable() { return rewardDropTable; }


    private static final List<AchievementTemplate> ALL_ACHIEVEMENTS = List.of(
        // --- COMBAT ---
        new AchievementTemplate("combat_first_blood", "First Blood",
                "Kill your first endgame NPC", Category.COMBAT, 1, 25, null),
        new AchievementTemplate("combat_slayer_50", "Slayer",
                "Kill 50 endgame NPCs", Category.COMBAT, 50, 100, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("combat_slayer_100", "Veteran Slayer",
                "Kill 100 endgame NPCs", Category.COMBAT, 100, 200, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("combat_exterminator", "Exterminator",
                "Kill 500 endgame NPCs", Category.COMBAT, 500, 500, "Endgame_Drop_Reward_20"),
        new AchievementTemplate("combat_thousand", "Thousand Kills",
                "Kill 1000 endgame NPCs", Category.COMBAT, 1000, 1000, "Endgame_Drop_Bounty_Weekly"),

        // --- BOSS ---
        new AchievementTemplate("boss_frost_dragon", "Dragon Slayer",
                "Defeat the Frost Dragon", Category.BOSS, 1, 200, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("boss_hedera", "Hedera's Bane",
                "Defeat Hedera", Category.BOSS, 1, 200, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("boss_golem_void", "Void Crusher",
                "Defeat the Void Golem", Category.BOSS, 1, 300, "Endgame_Drop_Reward_20"),
        new AchievementTemplate("boss_fire_dragon", "Flame Tamer",
                "Defeat the Fire Dragon", Category.BOSS, 1, 150, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("boss_rush", "Boss Rush",
                "Defeat all 4 boss types", Category.BOSS, 4, 500, "Endgame_Drop_Bounty_Weekly"),
        new AchievementTemplate("boss_slayer_10", "Boss Hunter",
                "Defeat 10 bosses total", Category.BOSS, 10, 400, "Endgame_Drop_Reward_20"),
        new AchievementTemplate("boss_slayer_25", "Boss Executioner",
                "Defeat 25 bosses total", Category.BOSS, 25, 800, "Endgame_Drop_Bounty_Weekly"),

        // --- BOUNTY ---
        new AchievementTemplate("bounty_first", "Bounty Hunter",
                "Complete your first bounty", Category.BOUNTY, 1, 50, null),
        new AchievementTemplate("bounty_10", "Professional Hunter",
                "Complete 10 bounties", Category.BOUNTY, 10, 150, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("bounty_50", "Bounty Master",
                "Complete 50 bounties", Category.BOUNTY, 50, 400, "Endgame_Drop_Reward_20"),
        new AchievementTemplate("bounty_streak_3", "Streak Starter",
                "Claim 3 daily streak bonuses", Category.BOUNTY, 3, 100, "Endgame_Drop_Reward_5"),

        // --- DISCOVERY ---
        new AchievementTemplate("discovery_5", "Curious Explorer",
                "Discover 5 NPC types", Category.DISCOVERY, 5, 50, null),
        new AchievementTemplate("discovery_10", "Explorer",
                "Discover 10 NPC types", Category.DISCOVERY, 10, 100, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("discovery_20", "Seasoned Explorer",
                "Discover 20 NPC types", Category.DISCOVERY, 20, 200, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("discovery_all", "Naturalist",
                "Discover all endgame NPC types", Category.DISCOVERY, 30, 500, "Endgame_Drop_Bounty_Weekly"),

        // --- CRAFTING ---
        new AchievementTemplate("craft_first_weapon", "Apprentice Smith",
                "Craft your first endgame weapon", Category.CRAFTING, 1, 50, null),
        new AchievementTemplate("craft_5_weapons", "Blacksmith",
                "Craft 5 endgame weapons", Category.CRAFTING, 5, 150, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("craft_10_weapons", "Master Crafter",
                "Craft 10 endgame weapons", Category.CRAFTING, 10, 300, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("craft_all_types", "Arsenal Builder",
                "Craft one of each endgame weapon type", Category.CRAFTING, 8, 500, "Endgame_Drop_Reward_20"),

        // --- EXPLORATION ---
        new AchievementTemplate("explore_dungeon_first", "Dungeon Diver",
                "Enter your first dungeon", Category.EXPLORATION, 1, 50, null),
        new AchievementTemplate("explore_dungeon_5", "Dungeon Crawler",
                "Enter 5 dungeon instances", Category.EXPLORATION, 5, 150, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("explore_dungeon_15", "Dungeon Veteran",
                "Enter 15 dungeon instances", Category.EXPLORATION, 15, 300, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("explore_frozen", "Frostbitten",
                "Enter the Frozen Dungeon", Category.EXPLORATION, 1, 100, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("explore_swamp", "Swamp Walker",
                "Enter the Swamp Dungeon", Category.EXPLORATION, 1, 100, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("explore_both", "World Traveler",
                "Enter both dungeon types", Category.EXPLORATION, 2, 250, "Endgame_Drop_Reward_15"),

        // --- SPEEDRUN ---
        new AchievementTemplate("speed_frost_180", "Blizzard Blitz",
                "Defeat Frost Dragon in under 3 minutes", Category.SPEEDRUN, 1, 300, "Endgame_Drop_Reward_15"),
        new AchievementTemplate("speed_hedera_240", "Root Ripper",
                "Defeat Hedera in under 4 minutes", Category.SPEEDRUN, 1, 300, "Endgame_Drop_Reward_15"),
        new AchievementTemplate("speed_golem_300", "Void Velocity",
                "Defeat Void Golem in under 5 minutes", Category.SPEEDRUN, 1, 400, "Endgame_Drop_Reward_20"),
        new AchievementTemplate("speed_fire_120", "Inferno Rush",
                "Defeat Fire Dragon in under 2 minutes", Category.SPEEDRUN, 1, 250, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("speed_any_3", "Speed Demon",
                "Speed kill any 3 bosses", Category.SPEEDRUN, 3, 500, "Endgame_Drop_Reward_20"),
        new AchievementTemplate("speed_all", "Perfectionist",
                "Speed kill all 4 boss types", Category.SPEEDRUN, 4, 800, "Endgame_Drop_Bounty_Weekly"),

        // --- MINING ---
        new AchievementTemplate("mine_first", "Prospector",
                "Mine your first Mithril or Adamantite Ore", Category.MINING, 1, 25, null),
        new AchievementTemplate("mine_50", "Ore Hoarder",
                "Mine 50 Mithril or Adamantite Ore", Category.MINING, 50, 150, "Endgame_Drop_Reward_5"),
        new AchievementTemplate("mine_200", "Deep Miner",
                "Mine 200 Mithril or Adamantite Ore", Category.MINING, 200, 400, "Endgame_Drop_Reward_15"),
        new AchievementTemplate("mine_mithril_25", "Mithril Seeker",
                "Mine 25 Mithril Ore", Category.MINING, 25, 200, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("mine_adamantite_25", "Adamantite Hunter",
                "Mine 25 Adamantite Ore", Category.MINING, 25, 250, "Endgame_Drop_Reward_10"),
        new AchievementTemplate("mine_1000", "Earthshaker",
                "Mine 1000 blocks total", Category.MINING, 1000, 500, "Endgame_Drop_Reward_20")
    );

    private static final Map<String, AchievementTemplate> BY_ID = ALL_ACHIEVEMENTS.stream()
            .collect(Collectors.toUnmodifiableMap(AchievementTemplate::getId, Function.identity()));

    public static List<AchievementTemplate> getAll() {
        return ALL_ACHIEVEMENTS;
    }

    public static AchievementTemplate findById(String id) {
        return BY_ID.get(id);
    }

    public static List<AchievementTemplate> getByCategory(Category category) {
        return ALL_ACHIEVEMENTS.stream()
                .filter(t -> t.category == category)
                .toList();
    }
}
