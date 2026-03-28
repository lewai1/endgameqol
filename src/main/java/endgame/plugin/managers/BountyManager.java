package endgame.plugin.managers;

import com.hypixel.hytale.logger.HytaleLogger;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.BountyData.ActiveBounty;
import endgame.plugin.config.BountyData.PlayerBountyState;
import endgame.plugin.config.EndgameConfig;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages daily bounty generation, progress tracking, and rewards.
 *
 * Bounties are deterministic per player per day (seeded by dayOfYear + UUID hash).
 * 3 bounties per player: 1 easy, 1 medium, 1 hard.
 *
 * Thread safety: ConcurrentHashMap for in-memory cache, debounced saves.
 */
public class BountyManager {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Bounty");

    private final EndgameQoL plugin;

    // In-memory cache of player components (populated on connect, cleared on disconnect)
    private final ConcurrentHashMap<UUID, PlayerEndgameComponent> components = new ConcurrentHashMap<>();

    public BountyManager(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    // === Component Cache ===

    public void onPlayerConnect(UUID playerUuid, PlayerEndgameComponent comp) {
        components.put(playerUuid, comp);
    }

    /**
     * Get or generate bounties for a player. Refreshes if expired.
     */
    public PlayerBountyState getPlayerBounties(UUID playerUuid) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return null;

        PlayerEndgameComponent comp = components.get(playerUuid);
        if (comp == null) {
            comp = plugin.getPlayerComponent(playerUuid);
        }
        if (comp == null) return null;
        PlayerBountyState state = comp.getBountyState();

        // Check if daily refresh needed
        long now = System.currentTimeMillis();
        long refreshMs = config.getBountyRefreshHours() * 3600000L;
        if (now - state.getLastRefreshTimestamp() >= refreshMs) {
            generateBounties(playerUuid, state);
            // No explicit save — Hytale auto-persists the ECS component
        }

        // B1: Check if weekly refresh needed (7-day cycle)
        if (config.isBountyWeeklyEnabled()) {
            long weeklyRefreshMs = 7L * 24 * 3600000L;
            if (now - state.getLastWeeklyRefreshTimestamp() >= weeklyRefreshMs) {
                generateWeeklyBounty(playerUuid, state);
                // No explicit save — Hytale auto-persists the ECS component
            }
        }

        return state;
    }

    /**
     * Called when a player kills any NPC (from ComboKillTracker hook).
     */
    public void onNPCKill(UUID playerUuid, String npcTypeId) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;


        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.KILL_NPC) {
                if (npcTypeId.toLowerCase().contains(template.getTarget().toLowerCase())) {
                    bounty.incrementProgress(1);

                }
            }
        }

        // B1: Weekly — KILL_ENDGAME_NPCS
        ActiveBounty weekly = state.getWeeklyBounty();
        if (weekly != null && !weekly.isCompleted() && !weekly.isClaimed()) {
            BountyTemplate wt = findTemplate(weekly.getTemplateId());
            if (wt != null && wt.getType() == BountyTemplate.BountyType.KILL_ENDGAME_NPCS) {
                weekly.incrementProgress(1);

            }
        }

        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Called when a boss dies (from GenericBossDeathSystem / GolemVoidDeathSystem hook).
     * @param encounterDurationSeconds seconds elapsed since boss spawned
     */
    public void onBossKill(UUID playerUuid, String bossType, long encounterDurationSeconds) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;


        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.KILL_ANY_BOSS) {
                bounty.incrementProgress(1);

            } else if (template.getType() == BountyTemplate.BountyType.SPEED_KILL_BOSS) {
                // Speed kill: target must match boss type, kill must be within time limit
                if (template.getTarget() != null
                        && bossType.toLowerCase().contains(template.getTarget().toLowerCase())
                        && encounterDurationSeconds <= template.getCount()) {
                    bounty.incrementProgress(1);

                    LOGGER.atFine().log("[Bounty] SPEED_KILL_BOSS completed: %s killed in %ds (limit: %ds)",
                            bossType, encounterDurationSeconds, template.getCount());
                }
            }

            // B3: Check bonus objective — "during_gauntlet"
            if (!bounty.isBonusCompleted() && !bounty.getBonusType().isEmpty()) {
                if (isBonusConditionMet(playerUuid, bounty.getBonusType())) {
                    bounty.setBonusCompleted(true);

                }
            }
        }

        // B1: Weekly — KILL_ANY_BOSS and KILL_UNIQUE_BOSSES
        ActiveBounty weekly = state.getWeeklyBounty();
        if (weekly != null && !weekly.isCompleted() && !weekly.isClaimed()) {
            BountyTemplate wt = findTemplate(weekly.getTemplateId());
            if (wt != null) {
                if (wt.getType() == BountyTemplate.BountyType.KILL_ANY_BOSS) {
                    weekly.incrementProgress(1);

                } else if (wt.getType() == BountyTemplate.BountyType.KILL_UNIQUE_BOSSES) {
                    // Track distinct boss types via metadata (comma-separated)
                    String killed = weekly.getMetadata();
                    if (!killed.contains(bossType)) {
                        weekly.setMetadata(killed.isEmpty() ? bossType : killed + "," + bossType);
                        weekly.incrementProgress(1);
    
                    }
                }
            }
        }

        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Called when a Warden Trial is completed.
     */
    public void onTrialComplete(UUID playerUuid, int tier) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;


        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.COMPLETE_TRIAL) {
                if (tier >= template.getCount()) {
                    bounty.incrementProgress(1);

                }
            }
        }

        // B1: Weekly — COMPLETE_TRIAL (tier III+ = tier >= 3)
        ActiveBounty weekly = state.getWeeklyBounty();
        if (weekly != null && !weekly.isCompleted() && !weekly.isClaimed()) {
            BountyTemplate wt = findTemplate(weekly.getTemplateId());
            if (wt != null && wt.getType() == BountyTemplate.BountyType.COMPLETE_TRIAL) {
                if (tier >= 3) {
                    weekly.incrementProgress(1);

                }
            }
        }

        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Phase 2: Called when a player crafts an item.
     */
    public void onCraft(UUID playerUuid, String itemId) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;


        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.CRAFT_ITEM) {
                if (template.getTarget() != null && itemId.toLowerCase().contains(template.getTarget().toLowerCase())) {
                    bounty.incrementProgress(1);

                }
            }
        }
        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Phase 2: Called when a dungeon instance is cleared.
     */
    public void onDungeonClear(UUID playerUuid) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;


        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.DUNGEON_CLEAR) {
                bounty.incrementProgress(1);

            }
        }
        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Phase 2: Called when a player deals damage to a boss.
     * Amount is accumulated (not per-hit completion).
     */
    public void onBossDamageDealt(UUID playerUuid, float amount) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;


        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.DAMAGE_DEALT) {
                bounty.incrementProgress(Math.round(amount));

            }
        }
        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Phase 3: Called when a player mines a block (from MiningTracker).
     * @param gatherType the block's gather type (e.g., "OreMithril", "OreAdamantite")
     */
    public void onBlockMined(UUID playerUuid, String gatherType) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;

        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.MINE_ORE) {
                if (template.getTarget() == null || gatherType.equalsIgnoreCase(template.getTarget())) {
                    bounty.incrementProgress(1);
                }
            }
        }
        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Phase 3: Called when a player enters a dungeon instance (from DungeonEnterEvent).
     * @param dungeonType "frozen_dungeon" or "swamp_dungeon"
     */
    public void onDungeonEnter(UUID playerUuid, String dungeonType) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;

        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.EXPLORE_DUNGEON) {
                if (template.getTarget() == null || dungeonType.equalsIgnoreCase(template.getTarget())) {
                    bounty.incrementProgress(1);
                }
            }
        }
        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Called when player reaches a combo tier.
     */
    public void onComboTier(UUID playerUuid, int tier) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyEnabled()) return;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;


        for (ActiveBounty bounty : state.getBounties()) {
            if (bounty.isClaimed() || bounty.isCompleted()) continue;

            BountyTemplate template = findTemplate(bounty.getTemplateId());
            if (template == null) continue;

            if (template.getType() == BountyTemplate.BountyType.COMBO_TIER) {
                if (tier >= template.getCount()) {
                    bounty.incrementProgress(1);

                }
            }

            // B3: Check bonus — "combo_x3" or "combo_frenzy"
            if (!bounty.isBonusCompleted() && !bounty.getBonusType().isEmpty()) {
                if (isBonusConditionMet(playerUuid, bounty.getBonusType())) {
                    bounty.setBonusCompleted(true);

                }
            }
        }

        // B1: Weekly — REACH_FRENZY_COUNT (tier 4 = FRENZY)
        if (tier >= 4) {
            ActiveBounty weekly = state.getWeeklyBounty();
            if (weekly != null && !weekly.isCompleted() && !weekly.isClaimed()) {
                BountyTemplate wt = findTemplate(weekly.getTemplateId());
                if (wt != null && wt.getType() == BountyTemplate.BountyType.REACH_FRENZY_COUNT) {
                    weekly.incrementProgress(1);

                }
            }
        }

        // No explicit save — Hytale auto-persists the ECS component
    }

    /**
     * Claim a completed bounty by index (0-2 for daily, 3 for weekly). Returns the reward drop table, or null.
     */
    public String claimBounty(UUID playerUuid, int index) {
        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return null;

        // B1: Index 3 = weekly bounty
        if (index == 3) {
            return claimWeeklyBounty(playerUuid, state);
        }

        List<ActiveBounty> bounties = state.getBounties();
        if (index < 0 || index >= bounties.size()) return null;

        ActiveBounty bounty = bounties.get(index);
        if (!bounty.isCompleted() || bounty.isClaimed()) return null;

        bounty.setClaimed(true);

        // B2: Increment reputation
        state.incrementTotalCompleted();

        BountyTemplate template = findTemplate(bounty.getTemplateId());
        String dropTable = template != null ? template.getRewardDropTable() : "Endgame_Drop_Reward_5";

        // Phase 2: Award reputation points from template
        if (template != null && template.getReputationReward() > 0) {
            state.addReputation(template.getReputationReward());
        }

        // Phase 2: Award XP via leveling integrations
        if (template != null && template.getXpReward() > 0) {
            if (plugin.isRPGLevelingActive()) {
                try {
                    plugin.getRpgLevelingBridge().addXP(playerUuid, template.getXpReward(), "BOUNTY_COMPLETE");
                } catch (Exception e) {
                    LOGGER.atFine().log("[Bounty] Failed to award RPG XP for %s: %s", playerUuid, e.getMessage());
                }
            }
            if (plugin.isEndlessLevelingActive()) {
                try {
                    plugin.getEndlessLevelingBridge().addXP(playerUuid, template.getXpReward(), "BOUNTY_COMPLETE");
                } catch (Exception e) {
                    LOGGER.atFine().log("[Bounty] Failed to award EL XP for %s: %s", playerUuid, e.getMessage());
                }
            }
        }

        // B3: Bonus objective — if completed, upgrade the drop table
        if (bounty.isBonusCompleted() && template != null) {
            dropTable = getBonusDropTable(template.getDifficulty(), dropTable);
        }

        // No explicit save — Hytale auto-persists the ECS component
        return dropTable;
    }

    /**
     * B1: Claim weekly bounty. Returns the reward drop table, or null.
     */
    private String claimWeeklyBounty(UUID playerUuid, PlayerBountyState state) {
        ActiveBounty weekly = state.getWeeklyBounty();
        if (weekly == null || !weekly.isCompleted() || weekly.isClaimed()) return null;

        weekly.setClaimed(true);
        state.incrementTotalCompleted();

        // No explicit save — Hytale auto-persists the ECS component
        return "Endgame_Drop_Bounty_Weekly";
    }

    /**
     * B3: Get upgraded drop table for bonus completion (+50% reward = next tier drop table).
     */
    private String getBonusDropTable(BountyTemplate.BountyDifficulty difficulty, String baseDrop) {
        return switch (difficulty) {
            case EASY -> "Endgame_Drop_Reward_10";      // Easy bonus → Medium reward
            case MEDIUM -> "Endgame_Drop_Reward_20";     // Medium bonus → Hard reward
            case HARD -> "Endgame_Drop_Bounty_Weekly";     // Hard bonus → Weekly reward
            default -> baseDrop;
        };
    }

    /**
     * Claim streak bonus (all 3 bounties completed). Returns true if claimed.
     */
    public boolean claimStreak(UUID playerUuid) {
        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBountyStreakEnabled()) return false;

        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return false;

        if (!state.allCompleted() || state.isStreakClaimed()) return false;

        state.setStreakClaimed(true);
        // No explicit save — Hytale auto-persists the ECS component
        return true;
    }

    /**
     * Get time remaining until next refresh in milliseconds.
     */
    public long getTimeUntilRefresh(UUID playerUuid) {
        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return 0;

        EndgameConfig config = plugin.getConfig().get();
        long refreshMs = config.getBountyRefreshHours() * 3600000L;
        long elapsed = System.currentTimeMillis() - state.getLastRefreshTimestamp();
        return Math.max(0, refreshMs - elapsed);
    }

    /**
     * Force refresh bounties for a specific player (admin command).
     */
    public void forceRefreshPlayer(UUID playerUuid) {
        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return;
        generateBounties(playerUuid, state);
        generateWeeklyBounty(playerUuid, state);
        LOGGER.atFine().log("[Bounty] Admin forced refresh for %s", playerUuid);
    }

    /**
     * Force refresh bounties for all cached players (admin command).
     * Returns the number of players refreshed.
     */
    public int forceRefreshAll() {
        int count = 0;
        for (Map.Entry<UUID, PlayerEndgameComponent> entry : components.entrySet()) {
            PlayerBountyState state = entry.getValue().getBountyState();
            generateBounties(entry.getKey(), state);
            generateWeeklyBounty(entry.getKey(), state);
            count++;
        }
        LOGGER.atFine().log("[Bounty] Admin forced refresh for all %d cached players", count);
        return count;
    }

    /**
     * Get the number of players with cached bounty state.
     */
    public int getCachedPlayerCount() {
        return components.size();
    }

    /**
     * Remove a player from the in-memory cache on disconnect.
     * No manual save needed — Hytale auto-persists the ECS component.
     */
    public void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        components.remove(playerUuid);
    }

    // === Internal ===

    private void generateBounties(UUID playerUuid, PlayerBountyState state) {
        long now = System.currentTimeMillis();
        state.setLastRefreshTimestamp(now);
        state.getBounties().clear();
        state.setStreakClaimed(false);

        // Deterministic seed: day + player UUID hash
        int dayOfYear = LocalDate.now(ZoneOffset.UTC).getDayOfYear();
        long seed = dayOfYear * 31L + playerUuid.hashCode();
        Random rng = new Random(seed);

        // Pick 1 from each difficulty pool
        BountyTemplate easy = pickRandom(BountyTemplate.getEasyPool(), rng);
        BountyTemplate medium = pickRandom(BountyTemplate.getMediumPool(), rng);
        BountyTemplate hard = pickRandom(BountyTemplate.getHardPool(), rng);

        ActiveBounty easyBounty = new ActiveBounty(easy.getId(), bountyTarget(easy));
        ActiveBounty medBounty = new ActiveBounty(medium.getId(), bountyTarget(medium));
        ActiveBounty hardBounty = new ActiveBounty(hard.getId(), bountyTarget(hard));

        // B3: Assign cross-system bonus objectives
        assignBonusObjective(easyBounty, easy, rng);
        assignBonusObjective(medBounty, medium, rng);
        assignBonusObjective(hardBounty, hard, rng);

        state.getBounties().add(easyBounty);
        state.getBounties().add(medBounty);
        state.getBounties().add(hardBounty);

        LOGGER.atFine().log("[Bounty] Generated bounties for %s: %s, %s, %s",
                playerUuid, easy.getId(), medium.getId(), hard.getId());
    }

    /**
     * B1: Generate a weekly bounty for the player.
     */
    private void generateWeeklyBounty(UUID playerUuid, PlayerBountyState state) {
        long now = System.currentTimeMillis();
        state.setLastWeeklyRefreshTimestamp(now);

        // Deterministic seed: ISO week + player UUID hash
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int weekOfYear = today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        long seed = weekOfYear * 53L + playerUuid.hashCode();
        Random rng = new Random(seed);

        BountyTemplate weekly = pickRandom(BountyTemplate.getWeeklyPool(), rng);
        ActiveBounty weeklyBounty = new ActiveBounty(weekly.getId(), weekly.getCount());
        state.setWeeklyBounty(weeklyBounty);

        LOGGER.atFine().log("[Bounty] Generated weekly bounty for %s: %s", playerUuid, weekly.getId());
    }

    /**
     * B3: Assign a cross-system bonus objective to a bounty based on its type.
     * Kill bounties → "while at Combo x3+"
     * Boss bounties → "while maintaining FRENZY"
     * Trial bounties → "at full HP"
     */
    private void assignBonusObjective(ActiveBounty bounty, BountyTemplate template, Random rng) {
        BountyTemplate.BonusType bonus = switch (template.getType()) {
            case KILL_NPC -> BountyTemplate.BonusType.COMBO_X3;
            case KILL_ANY_BOSS, SPEED_KILL_BOSS -> BountyTemplate.BonusType.COMBO_FRENZY;
            case COMBO_TIER -> BountyTemplate.BonusType.AT_FULL_HP;
            case COMPLETE_TRIAL -> BountyTemplate.BonusType.COMBO_X3;
            case MINE_ORE -> BountyTemplate.BonusType.COMBO_X3;
            case EXPLORE_DUNGEON -> BountyTemplate.BonusType.AT_FULL_HP;
            default -> BountyTemplate.BonusType.NONE;
        };
        bounty.setBonusType(bonus.getId());
    }

    /**
     * B3: Check if a bonus condition is currently met for a player.
     */
    private boolean isBonusConditionMet(UUID playerUuid, String bonusTypeId) {
        BountyTemplate.BonusType bonus = BountyTemplate.BonusType.fromId(bonusTypeId);
        return switch (bonus) {
            case COMBO_X3 -> {
                ComboMeterManager combo = plugin.getComboMeterManager();
                yield combo != null && combo.getComboTier(playerUuid) >= 2;
            }
            case COMBO_FRENZY -> {
                ComboMeterManager combo = plugin.getComboMeterManager();
                yield combo != null && combo.getComboTier(playerUuid) >= 4;
            }
            case DURING_GAUNTLET -> false; // Gauntlet disabled
            case AT_FULL_HP -> isPlayerAtFullHP(playerUuid);
            default -> false;
        };
    }

    private boolean isPlayerAtFullHP(UUID playerUuid) {
        try {
            for (PlayerRef pr : Universe.get().getPlayers()) {
                if (pr == null) continue;
                Ref<EntityStore> ref = pr.getReference();
                if (ref == null || !ref.isValid()) continue;
                if (!playerUuid.equals(endgame.plugin.utils.EntityUtils.getUuid(pr))) continue;

                ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
                if (statType == null) return false;
                EntityStatMap statMap = ref.getStore().getComponent(ref, statType);
                if (statMap == null) return false;

                EntityStatValue health = statMap.get(DefaultEntityStatTypes.getHealth());
                return health != null && health.get() >= health.getMax();
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * B2: Get the player's reputation rank string.
     */
    public String getReputationRank(UUID playerUuid) {
        PlayerBountyState state = getPlayerBounties(playerUuid);
        return state != null ? state.getReputationRank() : "Novice";
    }

    /**
     * B2: Get the player's total bounties completed.
     */
    public int getTotalBountiesCompleted(UUID playerUuid) {
        PlayerBountyState state = getPlayerBounties(playerUuid);
        return state != null ? state.getTotalBountiesCompleted() : 0;
    }

    /**
     * B1: Get time remaining until weekly refresh in milliseconds.
     */
    public long getWeeklyTimeUntilRefresh(UUID playerUuid) {
        PlayerBountyState state = getPlayerBounties(playerUuid);
        if (state == null) return 0;

        long weeklyRefreshMs = 7L * 24 * 3600000L;
        long elapsed = System.currentTimeMillis() - state.getLastWeeklyRefreshTimestamp();
        return Math.max(0, weeklyRefreshMs - elapsed);
    }

    /**
     * For SPEED_KILL_BOSS, target is 1 (binary: kill within time limit).
     * For all other types, target equals the template count.
     */
    private int bountyTarget(BountyTemplate template) {
        if (template.getType() == BountyTemplate.BountyType.SPEED_KILL_BOSS) {
            return 1;
        }
        return template.getCount();
    }

    private BountyTemplate pickRandom(List<BountyTemplate> pool, Random rng) {
        return pool.get(rng.nextInt(pool.size()));
    }

    private BountyTemplate findTemplate(String id) {
        return BountyTemplate.getById(id);
    }

    // No debouncedSave needed — Hytale auto-persists the ECS component
}
