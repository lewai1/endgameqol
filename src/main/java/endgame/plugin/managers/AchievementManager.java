package endgame.plugin.managers;

import com.hypixel.hytale.logger.HytaleLogger;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.AchievementData.PlayerAchievementState;
import endgame.plugin.config.BestiaryData.PlayerBestiaryState;

import endgame.plugin.config.BestiaryData.NPCEntry;
import endgame.plugin.utils.BestiaryRegistry;
import endgame.plugin.utils.BestiaryRegistry.KillMilestone;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages achievement progress, bestiary tracking, and reward distribution.
 *
 * All state is stored in the player's ECS component (PlayerEndgameComponent),
 * which Hytale auto-persists to universe/players/{UUID}.bson.
 * No manual saves needed.
 *
 * Thread safety: ConcurrentHashMap for in-memory cache lookups.
 */
public class AchievementManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Achievement");

    private final EndgameQoL plugin;

    // In-memory cache of player components (populated on connect, cleared on disconnect)
    private final ConcurrentHashMap<UUID, PlayerEndgameComponent> components = new ConcurrentHashMap<>();

    // Track unique boss kills per player for boss_rush achievement (transient, not persisted)
    private final ConcurrentHashMap<UUID, Set<String>> uniqueBossKills = new ConcurrentHashMap<>();

    // Track unique weapon crafts per player for craft_all_types achievement (transient)
    private final ConcurrentHashMap<UUID, Set<String>> uniqueWeaponCrafts = new ConcurrentHashMap<>();

    // Track unique speed kills per player for speedrun achievements (transient)
    private final ConcurrentHashMap<UUID, Set<String>> uniqueSpeedKills = new ConcurrentHashMap<>();

    // Track unique dungeon types entered per player for explore_both (transient)
    private final ConcurrentHashMap<UUID, Set<String>> uniqueDungeonTypes = new ConcurrentHashMap<>();

    public AchievementManager(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    // === Component Cache ===

    public void onPlayerConnect(UUID playerUuid, PlayerEndgameComponent comp) {
        components.put(playerUuid, comp);
        uniqueBossKills.remove(playerUuid);
        uniqueWeaponCrafts.remove(playerUuid);
        uniqueSpeedKills.remove(playerUuid);
        uniqueDungeonTypes.remove(playerUuid);
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        components.remove(playerUuid);
        uniqueBossKills.remove(playerUuid);
        uniqueWeaponCrafts.remove(playerUuid);
        uniqueSpeedKills.remove(playerUuid);
        uniqueDungeonTypes.remove(playerUuid);
        // No manual save needed — Hytale auto-persists the component
    }

    // === Bestiary + Achievement State Access ===

    public PlayerAchievementState getAchievementState(UUID playerUuid) {
        PlayerEndgameComponent comp = components.get(playerUuid);
        if (comp != null) return comp.getAchievementState();
        // Fallback: try direct lookup from plugin cache
        comp = plugin.getPlayerComponent(playerUuid);
        return comp != null ? comp.getAchievementState() : new PlayerAchievementState();
    }

    public PlayerBestiaryState getBestiaryState(UUID playerUuid) {
        PlayerEndgameComponent comp = components.get(playerUuid);
        if (comp != null) return comp.getBestiaryState();
        comp = plugin.getPlayerComponent(playerUuid);
        return comp != null ? comp.getBestiaryState() : new PlayerBestiaryState();
    }

    // === Event Hooks ===

    /**
     * Called when a player kills any NPC (from ComboKillTracker hook).
     */
    public void onNPCKill(UUID playerUuid, String npcTypeId) {
        if (!components.containsKey(playerUuid)) return;

        // Only track NPCs registered in the endgame bestiary
        if (BestiaryRegistry.get(npcTypeId) == null) return;

        // Bestiary: record kill
        PlayerBestiaryState bestiary = getBestiaryState(playerUuid);
        bestiary.getOrCreate(npcTypeId).recordKill();

        // Achievements: combat kill count
        PlayerAchievementState achievements = getAchievementState(playerUuid);
        int totalKills = bestiary.getTotalKills();
        checkProgress(playerUuid, achievements, "combat_first_blood", totalKills);
        checkProgress(playerUuid, achievements, "combat_slayer_50", totalKills);
        checkProgress(playerUuid, achievements, "combat_slayer_100", totalKills);
        checkProgress(playerUuid, achievements, "combat_exterminator", totalKills);
        checkProgress(playerUuid, achievements, "combat_thousand", totalKills);

        // Discovery achievements
        int discovered = bestiary.getDiscoveredCount();
        checkProgress(playerUuid, achievements, "discovery_5", discovered);
        checkProgress(playerUuid, achievements, "discovery_10", discovered);
        checkProgress(playerUuid, achievements, "discovery_20", discovered);
        checkProgress(playerUuid, achievements, "discovery_all", discovered);
    }

    /**
     * Called when a boss is killed.
     * @param encounterDurationSeconds seconds elapsed since boss spawned
     */
    public void onBossKill(UUID playerUuid, String bossTypeId, long encounterDurationSeconds) {
        if (!components.containsKey(playerUuid)) return;

        PlayerAchievementState achievements = getAchievementState(playerUuid);

        // Track unique boss types for boss_rush
        Set<String> killed = uniqueBossKills.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
        killed.add(bossTypeId.toLowerCase());

        // Individual boss achievements
        String normalizedId = bossTypeId.toLowerCase();
        if (normalizedId.contains("dragon_frost") || normalizedId.contains("ice_dragon")) {
            checkAndAward(playerUuid, achievements, "boss_frost_dragon");
        }
        if (normalizedId.contains("hedera")) {
            checkAndAward(playerUuid, achievements, "boss_hedera");
        }
        if (normalizedId.contains("golem_void")) {
            checkAndAward(playerUuid, achievements, "boss_golem_void");
        }
        if (normalizedId.contains("dragon_fire")) {
            checkAndAward(playerUuid, achievements, "boss_fire_dragon");
        }

        // Boss rush (unique types)
        checkProgress(playerUuid, achievements, "boss_rush", killed.size());

        // Total boss kills
        int totalBossKills = achievements.getProgress("boss_slayer_10") + 1;
        checkProgress(playerUuid, achievements, "boss_slayer_10", totalBossKills);
        checkProgress(playerUuid, achievements, "boss_slayer_25", totalBossKills);

        // Speedrun achievements — check if kill was fast enough
        boolean speedKill = false;
        if (normalizedId.contains("dragon_frost") && encounterDurationSeconds <= 180) {
            checkAndAward(playerUuid, achievements, "speed_frost_180");
            speedKill = true;
        }
        if (normalizedId.contains("hedera") && encounterDurationSeconds <= 240) {
            checkAndAward(playerUuid, achievements, "speed_hedera_240");
            speedKill = true;
        }
        if (normalizedId.contains("golem_void") && encounterDurationSeconds <= 300) {
            checkAndAward(playerUuid, achievements, "speed_golem_300");
            speedKill = true;
        }
        if (normalizedId.contains("dragon_fire") && encounterDurationSeconds <= 120) {
            checkAndAward(playerUuid, achievements, "speed_fire_120");
            speedKill = true;
        }
        if (speedKill) {
            Set<String> speeds = uniqueSpeedKills.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
            speeds.add(normalizedId);
            checkProgress(playerUuid, achievements, "speed_any_3", speeds.size());
            checkProgress(playerUuid, achievements, "speed_all", speeds.size());
        }
    }

    /**
     * Called when a bounty is completed.
     */
    public void onBountyComplete(UUID playerUuid, int totalCompleted) {
        if (!components.containsKey(playerUuid)) return;
        PlayerAchievementState achievements = getAchievementState(playerUuid);
        checkProgress(playerUuid, achievements, "bounty_first", totalCompleted);
        checkProgress(playerUuid, achievements, "bounty_10", totalCompleted);
        checkProgress(playerUuid, achievements, "bounty_50", totalCompleted);
    }

    /**
     * Called when a streak bonus is claimed.
     */
    public void onStreakClaimed(UUID playerUuid) {
        if (!components.containsKey(playerUuid)) return;
        PlayerAchievementState achievements = getAchievementState(playerUuid);
        int streaks = achievements.getProgress("bounty_streak_3") + 1;
        checkProgress(playerUuid, achievements, "bounty_streak_3", streaks);
    }

    /**
     * Called when a player enters a dungeon instance (from DungeonEnterEvent).
     * @param dungeonType "frozen_dungeon" or "swamp_dungeon"
     */
    public void onDungeonEnter(UUID playerUuid, String dungeonType) {
        if (!components.containsKey(playerUuid)) return;
        PlayerAchievementState achievements = getAchievementState(playerUuid);

        // Total dungeon entries
        int totalEntries = achievements.getProgress("explore_dungeon_first") + 1;
        checkProgress(playerUuid, achievements, "explore_dungeon_first", totalEntries);
        checkProgress(playerUuid, achievements, "explore_dungeon_5", totalEntries);
        checkProgress(playerUuid, achievements, "explore_dungeon_15", totalEntries);

        // Specific dungeon types
        if ("frozen_dungeon".equalsIgnoreCase(dungeonType)) {
            checkAndAward(playerUuid, achievements, "explore_frozen");
        } else if ("swamp_dungeon".equalsIgnoreCase(dungeonType)) {
            checkAndAward(playerUuid, achievements, "explore_swamp");
        }

        // Both dungeon types (unique tracking)
        Set<String> types = uniqueDungeonTypes.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
        types.add(dungeonType.toLowerCase());
        checkProgress(playerUuid, achievements, "explore_both", types.size());
    }

    /**
     * Called when a player mines a block (from MiningTracker).
     * @param gatherType the block's gather type (e.g., "OreMithril", "OreAdamantite")
     */
    public void onBlockMined(UUID playerUuid, String gatherType) {
        if (!components.containsKey(playerUuid)) return;
        PlayerAchievementState achievements = getAchievementState(playerUuid);

        boolean isEndgameOre = "OreMithril".equals(gatherType) || "OreAdamantite".equals(gatherType);

        // Total blocks mined (any type)
        int totalBlocks = achievements.getProgress("mine_1000") + 1;
        checkProgress(playerUuid, achievements, "mine_1000", totalBlocks);

        if (isEndgameOre) {
            // Endgame ore count (Mithril + Adamantite combined)
            int oreCount = achievements.getProgress("mine_first") + 1;
            checkProgress(playerUuid, achievements, "mine_first", oreCount);
            checkProgress(playerUuid, achievements, "mine_50", oreCount);
            checkProgress(playerUuid, achievements, "mine_200", oreCount);
        }

        // Specific ore types
        if ("OreMithril".equals(gatherType)) {
            int mithril = achievements.getProgress("mine_mithril_25") + 1;
            checkProgress(playerUuid, achievements, "mine_mithril_25", mithril);
        } else if ("OreAdamantite".equals(gatherType)) {
            int adamantite = achievements.getProgress("mine_adamantite_25") + 1;
            checkProgress(playerUuid, achievements, "mine_adamantite_25", adamantite);
        }
    }

    /**
     * Called when a player crafts an endgame item.
     */
    public void onCraft(UUID playerUuid, String itemId) {
        if (!components.containsKey(playerUuid)) return;
        PlayerAchievementState achievements = getAchievementState(playerUuid);

        // Track unique weapon types for craft_all_types
        if (isEndgameWeapon(itemId)) {
            Set<String> crafted = uniqueWeaponCrafts.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet());
            crafted.add(itemId.toLowerCase());

            int weaponCount = achievements.getProgress("craft_5_weapons") + 1;
            checkProgress(playerUuid, achievements, "craft_first_weapon", weaponCount);
            checkProgress(playerUuid, achievements, "craft_5_weapons", weaponCount);
            checkProgress(playerUuid, achievements, "craft_10_weapons", weaponCount);
            checkProgress(playerUuid, achievements, "craft_all_types", crafted.size());
        }
    }

    // === Claim Methods ===

    /**
     * Claims an achievement reward. Returns the drop table ID, or null if invalid.
     */
    public String claimAchievement(UUID playerUuid, String achievementId) {
        PlayerAchievementState state = getAchievementState(playerUuid);
        if (!state.isCompleted(achievementId) || state.isClaimed(achievementId)) return null;

        AchievementTemplate template = AchievementTemplate.findById(achievementId);
        if (template == null || template.getRewardDropTable() == null) return null;

        state.markClaimed(achievementId);

        // Award XP on achievement claim
        int achXp = plugin.getConfig().get().getElAchievementXp();
        try {
            if (plugin.isRPGLevelingActive()) {
                plugin.getRpgLevelingBridge().addXP(playerUuid, achXp, "ACHIEVEMENT");
            }
            if (plugin.isEndlessLevelingActive()) {
                plugin.getEndlessLevelingBridge().addXP(playerUuid, achXp, "ACHIEVEMENT");
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[Achievement] Failed to award XP: %s", e.getMessage());
        }

        LOGGER.atFine().log("[Achievement] %s claimed reward for '%s'", playerUuid, achievementId);
        return template.getRewardDropTable();
    }

    /**
     * Claims a bestiary kill milestone for a specific NPC. Returns drop table ID, or null if invalid.
     */
    public String claimBestiaryMilestone(UUID playerUuid, String npcTypeId, int threshold) {
        PlayerBestiaryState bestiary = getBestiaryState(playerUuid);
        NPCEntry entry = bestiary.getEntries().get(npcTypeId);
        if (entry == null || entry.getKillCount() < threshold) return null;
        if (entry.getClaimedMilestone() >= threshold) return null;

        BestiaryRegistry.MobInfo mobInfo = BestiaryRegistry.get(npcTypeId);
        if (mobInfo == null) return null;

        List<KillMilestone> milestones = BestiaryRegistry.getKillMilestones(mobInfo.category());
        String dropTable = null;
        for (KillMilestone m : milestones) {
            if (m.threshold() == threshold) {
                dropTable = m.dropTable();
                break;
            }
        }
        if (dropTable == null) return null;

        entry.setClaimedMilestone(threshold);
        LOGGER.atFine().log("[Bestiary] %s claimed milestone %d for %s", playerUuid, threshold, npcTypeId);
        return dropTable;
    }

    /**
     * Claims a bestiary discovery milestone. Returns drop table ID, or null if invalid.
     */
    public String claimDiscoveryMilestone(UUID playerUuid, int threshold) {
        PlayerBestiaryState bestiary = getBestiaryState(playerUuid);
        int discovered = bestiary.getDiscoveredCount();

        int effectiveThreshold = threshold;
        if (threshold == -1) effectiveThreshold = BestiaryRegistry.getTotalCount();
        if (discovered < effectiveThreshold) return null;
        if (bestiary.getClaimedDiscoveryMilestone() >= effectiveThreshold) return null;

        // Find matching drop table
        String dropTable = null;
        for (int i = 0; i < BestiaryRegistry.DISCOVERY_MILESTONES.length; i++) {
            int mt = BestiaryRegistry.DISCOVERY_MILESTONES[i];
            int effective = mt == -1 ? BestiaryRegistry.getTotalCount() : mt;
            if (effective == effectiveThreshold) {
                dropTable = BestiaryRegistry.DISCOVERY_DROP_TABLES[i];
                break;
            }
        }
        if (dropTable == null) return null;

        bestiary.setClaimedDiscoveryMilestone(effectiveThreshold);
        LOGGER.atFine().log("[Bestiary] %s claimed discovery milestone %d", playerUuid, effectiveThreshold);
        return dropTable;
    }

    // === Internal ===

    private void checkProgress(UUID playerUuid, PlayerAchievementState state, String achievementId, int currentValue) {
        if (state.isCompleted(achievementId)) return;

        AchievementTemplate template = AchievementTemplate.findById(achievementId);
        if (template == null) return;

        state.setProgress(achievementId, currentValue);

        if (currentValue >= template.getTarget()) {
            state.markCompleted(achievementId);
            LOGGER.atFine().log("[Achievement] %s completed '%s'", playerUuid, achievementId);
            plugin.getGameEventBus().publish(new endgame.plugin.events.domain.GameEvent.AchievementUnlockEvent(
                    playerUuid, achievementId, template.getName()));

            // Award XP if RPG Leveling active
            if (template.getXpReward() > 0 && plugin.isRPGLevelingActive()) {
                try {
                    plugin.getRpgLevelingBridge().addXP(playerUuid, template.getXpReward(), "ACHIEVEMENT");
                } catch (Exception e) {
                    LOGGER.atFine().log("[Achievement] Failed to award XP: %s", e.getMessage());
                }
            }
        }
    }

    private void checkAndAward(UUID playerUuid, PlayerAchievementState state, String achievementId) {
        if (state.isCompleted(achievementId)) return;

        AchievementTemplate template = AchievementTemplate.findById(achievementId);
        if (template == null) return;

        state.setProgress(achievementId, 1);
        state.markCompleted(achievementId);
        LOGGER.atFine().log("[Achievement] %s completed '%s'", playerUuid, achievementId);
        plugin.getGameEventBus().publish(new endgame.plugin.events.domain.GameEvent.AchievementUnlockEvent(
                playerUuid, achievementId, template.getName()));

        if (template.getXpReward() > 0 && plugin.isRPGLevelingActive()) {
            try {
                plugin.getRpgLevelingBridge().addXP(playerUuid, template.getXpReward(), "ACHIEVEMENT");
            } catch (Exception e) {
                LOGGER.atFine().log("[Achievement] Failed to award XP: %s", e.getMessage());
            }
        }
    }

    private boolean isEndgameWeapon(String itemId) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase();
        return lower.contains("endgame_") && (
                lower.contains("sword") || lower.contains("dagger") ||
                lower.contains("staff") || lower.contains("longsword") ||
                lower.contains("mace") || lower.contains("bow") ||
                lower.contains("axe") || lower.contains("spear"));
    }

    // === Cleanup ===

    public void forceClear() {
        components.clear();
        uniqueBossKills.clear();
        uniqueWeaponCrafts.clear();
        uniqueSpeedKills.clear();
        uniqueDungeonTypes.clear();
    }
}
