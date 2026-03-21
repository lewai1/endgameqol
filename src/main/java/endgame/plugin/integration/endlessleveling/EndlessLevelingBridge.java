package endgame.plugin.integration.endlessleveling;

import com.airijko.endlessleveling.api.EndlessLevelingAPI;
import com.airijko.endlessleveling.api.PlayerSnapshot;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.utils.BossType;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bridge to Endless Leveling mod API (6.1+).
 * This is the ONLY class that imports com.airijko.endlessleveling.api — complete isolation.
 * If Endless Leveling is not loaded, this class is never instantiated.
 */
public class EndlessLevelingBridge {

    private final EndgameQoL plugin;
    private volatile EndlessLevelingAPI api;

    public EndlessLevelingBridge(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        api = EndlessLevelingAPI.get();
        return api != null;
    }

    public boolean isActive() {
        return api != null;
    }

    public void shutdown() {
        api = null;
    }

    // === XP ===

    /**
     * Award XP to a player and nearby party members for killing a boss.
     * Uses grantSharedXpInRange() for automatic party distribution.
     */
    public void addBossKillXP(UUID playerUuid, BossType bossType) {
        if (api == null || playerUuid == null || bossType == null) return;

        BossConfig bc = plugin.getConfig().get().getBossConfig(bossType);
        int xp = bc.getXpReward();
        if (xp <= 0) return;

        double shareRange = plugin.getConfig().get().getElXpShareRange();
        try {
            api.grantSharedXpInRange(playerUuid, xp, shareRange);
            plugin.getLogger().atFine().log("[EndlessLeveling] %s kill -> %d XP shared (%.0fm range) from %s",
                    bossType.getDisplayName(), xp, shareRange, playerUuid);
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[EndlessLeveling] Failed to award boss XP: %s", e.getMessage());
        }
    }

    /**
     * Award arbitrary XP to a player (bounty, achievement, gauntlet, warden trial, etc.).
     */
    public void addXP(UUID playerUuid, int xp, String sourceName) {
        if (api == null || playerUuid == null || xp <= 0) return;
        try {
            api.grantXp(playerUuid, xp);
            plugin.getLogger().atFine().log("[EndlessLeveling] %s -> %d XP to %s",
                    sourceName, xp, playerUuid);
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[EndlessLeveling] Failed to award XP: %s", e.getMessage());
        }
    }

    // === Player Info ===

    public int getPlayerLevel(UUID playerUuid) {
        if (api == null || playerUuid == null) return 0;
        try {
            return api.getPlayerLevel(playerUuid);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get a full snapshot of the player's EL data (level, XP, race, class, skills).
     */
    @Nullable
    public PlayerSnapshot getPlayerSnapshot(UUID playerUuid) {
        if (api == null || playerUuid == null) return null;
        try {
            return api.getPlayerSnapshot(playerUuid);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the player's prestige level, or 0 if unknown.
     */
    public int getPlayerPrestigeLevel(UUID playerUuid) {
        if (api == null || playerUuid == null) return 0;
        try {
            return api.getPlayerPrestigeLevel(playerUuid);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get the player's race display name, or null if none.
     */
    @Nullable
    public String getPlayerRaceName(UUID playerUuid) {
        if (api == null || playerUuid == null) return null;
        try {
            PlayerSnapshot snap = api.getPlayerSnapshot(playerUuid);
            if (snap == null || snap.raceId() == null) return null;
            var def = api.getRaceDefinition(snap.raceId());
            return def != null ? def.getDisplayName() : snap.raceId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get the player's primary class display name, or null if none.
     */
    @Nullable
    public String getPlayerClassName(UUID playerUuid) {
        if (api == null || playerUuid == null) return null;
        try {
            PlayerSnapshot snap = api.getPlayerSnapshot(playerUuid);
            if (snap == null || snap.primaryClassId() == null) return null;
            var def = api.getClassDefinition(snap.primaryClassId());
            return def != null ? def.getDisplayName() : snap.primaryClassId();
        } catch (Exception e) {
            return null;
        }
    }

    // === Mob Leveling ===

    public void setMobEntityLevel(int entityIndex, int level) {
        if (api == null) return;
        try {
            api.setMobEntityLevelOverride(entityIndex, level);
            plugin.getLogger().atFine().log("[EndlessLeveling] Set mob entity level %d for index %d", level, entityIndex);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[EndlessLeveling] Failed to set mob level: %s", e.getMessage());
        }
    }

    public void clearMobEntityLevel(int entityIndex) {
        if (api == null) return;
        try {
            api.clearMobEntityLevelOverride(entityIndex);
            plugin.getLogger().atFine().log("[EndlessLeveling] Cleared mob level for index %d", entityIndex);
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[EndlessLeveling] Failed to clear mob level: %s", e.getMessage());
        }
    }

    /**
     * Register a level override for all mobs in a dungeon area.
     */
    public void registerDungeonLevelOverride(String id, String worldName,
                                              double cx, double cz, double radius,
                                              int minLevel, int maxLevel) {
        if (api == null) return;
        try {
            api.registerMobAreaLevelOverride(id, worldName, cx, cz, radius, minLevel, maxLevel);
            plugin.getLogger().atFine().log("[EndlessLeveling] Registered area override '%s' in %s (Lv%d-%d, r=%.0f)",
                    id, worldName, minLevel, maxLevel, radius);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[EndlessLeveling] Failed to register area override '%s': %s", id, e.getMessage());
        }
    }

    /**
     * Register a level override for ALL mobs in an entire world/instance (6.1+).
     * Cleaner than area overrides for instanced dungeons.
     */
    public void registerWorldLevelOverride(String id, String worldName, int minLevel, int maxLevel) {
        if (api == null) return;
        try {
            api.registerMobWorldLevelOverride(id, worldName, minLevel, maxLevel);
            plugin.getLogger().atFine().log("[EndlessLeveling] Registered world override '%s' in %s (Lv%d-%d)",
                    id, worldName, minLevel, maxLevel);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[EndlessLeveling] Failed to register world override '%s': %s", id, e.getMessage());
        }
    }

    public void removeDungeonLevelOverride(String id) {
        if (api == null) return;
        try {
            api.removeMobAreaLevelOverride(id);
            plugin.getLogger().atFine().log("[EndlessLeveling] Removed area override '%s'", id);
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[EndlessLeveling] Failed to remove area override: %s", e.getMessage());
        }
    }
}
