package endgame.plugin.managers;

import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.BossConfig;
import endgame.plugin.utils.BossType;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BossHealthManager - Simple and reliable boss health system.
 *
 * Single source of truth for boss health modifications.
 * Applied immediately at spawn via MobManager.onEntityAdd().
 * Death is handled by vanilla game mechanics (no custom death code).
 */
public class BossHealthManager {

    public static final String HEALTH_MODIFIER_KEY = "EndgameQoL.BossHealth";

    private final EndgameQoL plugin;

    // Track bosses by UUID for cleanup and refresh
    private final ConcurrentHashMap<UUID, BossHealthState> trackedBosses = new ConcurrentHashMap<>();

    // Cleanup interval
    private static final long CLEANUP_INTERVAL_MS = 60_000L;
    private static final float MINIMUM_BOSS_HEALTH = 100f;
    private volatile long lastCleanupTime = 0;

    private static class BossHealthState {
        final String typeId;
        final long createdTime;

        BossHealthState(String typeId) {
            this.typeId = typeId;
            this.createdTime = System.currentTimeMillis();
        }
    }

    public BossHealthManager(EndgameQoL plugin) {
        this.plugin = plugin;
        plugin.getLogger().atFine().log("[BossHealthManager] Initialized");
    }

    /**
     * Apply boss health at spawn time.
     * Called from MobManager.EntitySetupSystem.onEntityAdd().
     */
    public void applyBossHealth(NPCEntity npc, EntityStatMap statMap, @Nullable World world, @Nullable UUID uuid) {
        if (npc == null || statMap == null) {
            return;
        }

        String typeId = npc.getNPCTypeId();
        if (typeId == null) {
            return;
        }

        BossType bossType = BossType.fromTypeId(typeId.toLowerCase());
        if (bossType == null) {
            return;
        }

        // Calculate target health from config (data-driven)
        float targetHealth = calculateFromConfig(bossType, world);
        if (targetHealth <= 0) {
            plugin.getLogger().atWarning().log("[BossHealthManager] Invalid target health for %s: %.0f, using fallback", typeId, targetHealth);
            targetHealth = 1000f;
        }

        // H6 FIX: Claim the UUID slot BEFORE applying modifier to prevent race condition.
        if (uuid != null) {
            BossHealthState existing = trackedBosses.putIfAbsent(uuid, new BossHealthState(typeId));
            if (existing != null) {
                plugin.getLogger().atFine().log("[BossHealthManager] Skipping - already applied for UUID %s", uuid);
                return;
            }
        }

        int healthStat = DefaultEntityStatTypes.getHealth();

        // Remove existing modifier first
        statMap.removeModifier(healthStat, HEALTH_MODIFIER_KEY);

        // Use the known JSON base health (stable, predictable)
        float baseMax = bossType.getDefaultHealth();
        if (baseMax <= 0) {
            baseMax = MINIMUM_BOSS_HEALTH;
        }

        // Calculate additive modifier: targetHealth = baseMax + additive
        float additive = targetHealth - baseMax;

        // Ensure final health is at least minimum
        if (baseMax + additive < MINIMUM_BOSS_HEALTH) {
            additive = MINIMUM_BOSS_HEALTH - baseMax;
            plugin.getLogger().atWarning().log("[BossHealthManager] Adjusted additive to ensure minimum health: %.0f", MINIMUM_BOSS_HEALTH);
        }

        // Apply MAX modifier (ADDITIVE)
        StaticModifier mod = new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, additive);
        statMap.putModifier(healthStat, HEALTH_MODIFIER_KEY, mod);

        // Maximize health (current = max) for fresh spawn
        statMap.maximizeStatValue(healthStat);

        // Log spawn
        plugin.getLogger().atFine().log("[BossHealthManager] %s spawned with %.0f HP (jsonBase: %.0f, additive: %.0f)",
                bossType.getDisplayName(), targetHealth, baseMax, additive);

        com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue healthValue = statMap.get(healthStat);
        float runtimeMax = (healthValue != null) ? healthValue.getMax() : -1f;
        plugin.getLogger().atFine().log("[BossHealthManager] Diagnostic: runtimeMax=%.0f, jsonBase=%.0f, target=%.0f, additive=%.0f",
                runtimeMax, baseMax, targetHealth, additive);

        // Periodic cleanup
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            cleanupOldEntries();
        }
    }

    /**
     * Calculate target health from config based on boss type (data-driven).
     */
    private float calculateFromConfig(BossType bossType, @Nullable World world) {
        float targetHealth = (float) plugin.getConfig().get().getEffectiveBossHealth(bossType);

        // Apply player scaling
        if (world != null) {
            BossConfig bc = plugin.getConfig().get().getBossConfig(bossType);
            int scalingPercent = bc.getPlayerScaling();
            if (scalingPercent > 0) {
                int playerCount = countPlayersInWorld(world);
                if (playerCount > 1) {
                    float multiplier = 1.0f + (playerCount - 1) * (scalingPercent / 100.0f);
                    targetHealth = targetHealth * multiplier;

                    plugin.getLogger().atFine().log("[BossHealthManager] Player scaling: %d players -> %.1fx (%.0f HP)",
                            playerCount, multiplier, targetHealth);
                }
            }

            // Apply Endless Leveling level-based scaling (optional)
            if (plugin.isEndlessLevelingActive()) {
                float elMultiplier = calculateELLevelScaling(world);
                if (elMultiplier > 1.0f) {
                    targetHealth = targetHealth * elMultiplier;
                    plugin.getLogger().atFine().log("[BossHealthManager] EL level scaling: %.2fx (%.0f HP)",
                            elMultiplier, targetHealth);
                }
            }
        }

        return targetHealth;
    }

    /**
     * Count players in a specific world.
     */
    private int countPlayersInWorld(@Nullable World world) {
        if (world == null) {
            return 1;
        }

        try {
            int count = 0;
            for (com.hypixel.hytale.server.core.universe.PlayerRef player :
                    com.hypixel.hytale.server.core.universe.Universe.get().getPlayers()) {
                if (player == null) continue;

                com.hypixel.hytale.component.Ref<EntityStore> playerRef = player.getReference();
                if (playerRef == null || !playerRef.isValid()) continue;

                World playerWorld = playerRef.getStore().getExternalData().getWorld();
                if (playerWorld != null && playerWorld.equals(world)) {
                    count++;
                }
            }
            return Math.max(1, count);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[BossHealthManager] Error counting players: %s", e.getMessage());
            return 1;
        }
    }

    /**
     * Calculate boss HP scaling based on average Endless Leveling player level in the world.
     * Every 10 levels above 50 adds 5% HP. E.g., avg level 100 → 1.25x, level 150 → 1.50x.
     * Returns 1.0 if EL is not active or average level is below threshold.
     */
    private float calculateELLevelScaling(@javax.annotation.Nonnull World world) {
        try {
            var bridge = plugin.getEndlessLevelingBridge();
            if (bridge == null) return 1.0f;

            int totalLevel = 0;
            int playerCount = 0;
            for (com.hypixel.hytale.server.core.universe.PlayerRef player :
                    com.hypixel.hytale.server.core.universe.Universe.get().getPlayers()) {
                if (player == null) continue;
                com.hypixel.hytale.component.Ref<EntityStore> pRef = player.getReference();
                if (pRef == null || !pRef.isValid()) continue;
                World pw = pRef.getStore().getExternalData().getWorld();
                if (pw != null && pw.equals(world)) {
                    java.util.UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(player);
                    if (uuid != null) {
                        int level = bridge.getPlayerLevel(uuid);
                        if (level > 0) {
                            totalLevel += level;
                            playerCount++;
                        }
                    }
                }
            }

            if (playerCount == 0) return 1.0f;
            int avgLevel = totalLevel / playerCount;

            // Scaling: +5% per 10 levels above 50
            if (avgLevel <= 50) return 1.0f;
            float bonus = ((avgLevel - 50) / 10) * 0.05f;
            return 1.0f + bonus;
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[BossHealthManager] EL level scaling error: %s", e.getMessage());
            return 1.0f;
        }
    }

    /**
     * Refresh all boss stats - called when config changes via Apply button.
     */
    public void refreshAllBossStats() {
        int count = trackedBosses.size();
        trackedBosses.clear();

        plugin.getLogger().atFine().log("[BossHealthManager] Refresh triggered - cleared %d tracked bosses (new spawns will use updated config)",
                count);
    }

    /**
     * Clean up old entries (tracked by time since no Ref to validate).
     */
    private void cleanupOldEntries() {
        long now = System.currentTimeMillis();
        long maxAge = 10 * 60 * 1000;
        Iterator<Map.Entry<UUID, BossHealthState>> it = trackedBosses.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<UUID, BossHealthState> entry = it.next();
            if (now - entry.getValue().createdTime > maxAge) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            plugin.getLogger().atFine().log("[BossHealthManager] Cleaned up %d old boss entries", removed);
        }
    }

    /**
     * Remove a boss from tracking when it dies or despawns.
     */
    public void untrackBoss(UUID uuid) {
        if (uuid == null) return;
        BossHealthState removed = trackedBosses.remove(uuid);
        if (removed != null) {
            plugin.getLogger().atFine().log("[BossHealthManager] Untracked boss UUID %s (type: %s)", uuid, removed.typeId);
        }
    }

    /**
     * Cleanup all tracking data. Called on plugin shutdown.
     */
    public void cleanup() {
        trackedBosses.clear();
        plugin.getLogger().atFine().log("[BossHealthManager] Cleanup complete");
    }

    public int getTrackedBossCount() {
        return trackedBosses.size();
    }
}
