package endgame.plugin.integration.rpgleveling;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.utils.BossType;
import endgame.plugin.utils.EntityUtils;
import org.zuxaw.plugin.RPGLevelingPlugin;
import org.zuxaw.plugin.api.RPGLevelingAPI;
import org.zuxaw.plugin.api.XPSource;
import org.zuxaw.plugin.components.MobLevelData;

import java.util.UUID;

/**
 * Bridge to RPG Leveling mod API.
 * This is the ONLY class that imports org.zuxaw.plugin.api — complete isolation.
 * If RPG Leveling is not loaded, this class is never instantiated.
 */
public class RPGLevelingBridge {

    private final EndgameQoL plugin;
    private volatile RPGLevelingAPI api;
    private volatile XPSource bossKillSource;

    public RPGLevelingBridge(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the bridge. Returns true if RPG Leveling API is available.
     */
    public boolean init() {
        if (!RPGLevelingAPI.isAvailable()) return false;
        api = RPGLevelingAPI.get();
        if (api == null) return false;
        bossKillSource = XPSource.create("BOSS_KILL");
        return true;
    }

    /**
     * Award XP to a player for killing a boss.
     * Reads xpReward from BossConfig (data-driven).
     */
    public void addBossKillXP(UUID playerUuid, BossType bossType) {
        if (api == null || playerUuid == null || bossType == null) return;

        BossConfig bc = plugin.getConfig().get().getBossConfig(bossType);
        int xp = bc.getXpReward();
        if (xp <= 0) return;

        boolean success = api.addXP(playerUuid, xp, bossKillSource);

        plugin.getLogger().atFine().log("[RPGLeveling] %s kill → %d XP to %s (success=%b)",
                bossType.getDisplayName(), xp, playerUuid, success);
    }

    /**
     * Award arbitrary XP to a player with a named source (bounty, achievement, etc.).
     */
    public void addXP(UUID playerUuid, int xp, String sourceName) {
        if (api == null || playerUuid == null || xp <= 0) return;
        try {
            XPSource source = XPSource.create(sourceName);
            boolean success = api.addXP(playerUuid, xp, source);
            plugin.getLogger().atFine().log("[RPGLeveling] %s → %d XP to %s (success=%b)",
                    sourceName, xp, playerUuid, success);
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[RPGLeveling] Failed to award XP: %s", e.getMessage());
        }
    }

    /**
     * Force a specific mob level on an NPC entity for Warden Challenge tier scaling.
     * Pre-sets the spawn level so RPG Leveling uses it instead of zone-based calculation,
     * and immediately updates the cached level on the MobLevelData component.
     */
    public void setMobLevel(Store<EntityStore> store, Ref<EntityStore> npcRef, int level) {
        try {
            UUID entityUuid = EntityUtils.getUuid(store, npcRef);
            if (entityUuid == null) return;

            // Pre-set spawn level so RPG Leveling picks it up during level calculation
            RPGLevelingPlugin.get().putSpawnLevelForEntity(entityUuid, level);

            // Also set cached level immediately on the component if it exists
            var mobLevelDataType = RPGLevelingPlugin.get().getMobLevelDataType();
            if (mobLevelDataType != null) {
                MobLevelData data = store.getComponent(npcRef, mobLevelDataType);
                if (data != null) {
                    data.setCachedLevel(level);
                }
            }

            plugin.getLogger().atFine().log("[RPGLeveling] Set mob level %d for entity %s", level, entityUuid);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[RPGLeveling] Failed to set mob level: %s", e.getMessage());
        }
    }

    /**
     * Clear the forced spawn level for an entity (cleanup on death).
     * Prevents memory leak in RPGLeveling's spawnLevelByEntityUuid map.
     */
    public void clearMobLevel(Store<EntityStore> store, Ref<EntityStore> npcRef) {
        try {
            UUID entityUuid = EntityUtils.getUuid(store, npcRef);
            if (entityUuid == null) return;

            RPGLevelingPlugin.get().removeSpawnLevelForEntity(entityUuid);

            plugin.getLogger().atFine().log("[RPGLeveling] Cleared mob level for entity %s", entityUuid);
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[RPGLeveling] Failed to clear mob level: %s", e.getMessage());
        }
    }

    /**
     * Whether the bridge successfully connected to RPG Leveling API.
     */
    public boolean isActive() {
        return api != null;
    }

    /**
     * Shutdown the bridge. Called on plugin shutdown.
     */
    public void shutdown() {
        api = null;
        bossKillSource = null;
    }
}
