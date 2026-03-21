package endgame.plugin.managers;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.boss.GolemVoidBossManager;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MobManager - Handles boss detection and health application at spawn.
 *
 * Uses two systems for robust detection:
 * 1. EntitySetupSystem (HolderSystem) - Catches entity spawns and applies health
 * 2. StatRefreshSystem (RefChangeSystem) - Catches EntityStatMap changes
 *
 * Health is applied IMMEDIATELY at spawn via BossHealthManager.
 * Death is handled by vanilla game mechanics (no custom death code needed).
 *
 * Based on HardcoreMode implementation pattern.
 */
public class MobManager {

    // Track logged entities to avoid duplicate detection logs
    private final Set<String> loggedEntities = ConcurrentHashMap.newKeySet();
    private volatile long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 60000;

    private final EndgameQoL plugin;
    private GolemVoidBossManager golemVoidBossManager;

    public MobManager(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    public void setGolemVoidBossManager(GolemVoidBossManager system) {
        this.golemVoidBossManager = system;
    }

    public GolemVoidBossManager getGolemVoidBossManager() {
        return golemVoidBossManager;
    }

    /**
     * Log boss detection event.
     */
    public void onBossDetected(String npcTypeId) {
        if (npcTypeId == null) return;

        // Cleanup old entries periodically
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            loggedEntities.clear();
            lastCleanupTime = now;
        }

        String lowerType = npcTypeId.toLowerCase();
        String mobName = getMobName(lowerType);

        if (mobName == null) {
            return; // Not a boss we care about
        }

        // Only log once per unique type (to avoid spam)
        String logKey = npcTypeId;
        boolean isFirstLog = loggedEntities.add(logKey);

        if (isFirstLog) {
            String difficultyName = plugin.getConfig().get().getDifficulty().getDisplayName();
            plugin.getLogger().atFine().log("[MobManager] >>> %s DETECTED (Difficulty: %s) <<<",
                    mobName, difficultyName);
        }
    }

    /**
     * Get display name for a boss type.
     * @return mob name or null if not a recognized boss
     */
    @Nullable
    private String getMobName(String lowerType) {
        BossType type = BossType.fromTypeId(lowerType);
        return type != null ? type.getDisplayName() : null;
    }

    /**
     * Check if a given NPC type ID is a boss we manage.
     */
    public boolean isBoss(String npcTypeId) {
        if (npcTypeId == null) return false;
        return getMobName(npcTypeId.toLowerCase()) != null;
    }

    /**
     * Clear all tracking data. Called on cleanup.
     */
    public void cleanup() {
        loggedEntities.clear();
    }

    // =========================================================================
    // EntitySetupSystem - Catches entity spawns via HolderSystem
    // Applies boss health IMMEDIATELY at spawn via BossHealthManager
    // =========================================================================

    public static class EntitySetupSystem extends HolderSystem<EntityStore> {
        private final EndgameQoL plugin;
        private final MobManager manager;

        public EntitySetupSystem(EndgameQoL plugin, MobManager manager) {
            this.plugin = plugin;
            this.manager = manager;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Override
        public void onEntityAdd(@Nonnull Holder<EntityStore> holder, @Nonnull AddReason reason,
                @Nonnull Store<EntityStore> store) {
            ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
            if (npcType == null) return;

            NPCEntity npc = holder.getComponent(npcType);
            if (npc == null) return;

            String typeId = npc.getNPCTypeId();
            if (typeId == null) return;

            // Check if this is a boss we care about
            if (!manager.isBoss(typeId)) {
                return;
            }

            plugin.getLogger().atFine().log("[EntitySetupSystem] Boss spawned: %s", typeId);

            // Instance-locked bosses: despawn if spawned outside their required instance
            World world = store.getExternalData().getWorld();
            String typeIdLower = typeId.toLowerCase();
            if (typeIdLower.contains("endgame_dragon_frost")
                    && !EndgameQoL.isFrozenDungeonInstance(world)) {
                npc.setDespawning(true);
                plugin.getLogger().atWarning().log(
                        "[EntitySetupSystem] Endgame_Dragon_Frost spawned outside Frozen Dungeon — despawning");
                return;
            }
            if (typeIdLower.contains("endgame_hedera")
                    && !EndgameQoL.isSwampDungeonInstance(world)) {
                npc.setDespawning(true);
                plugin.getLogger().atWarning().log(
                        "[EntitySetupSystem] Endgame_Hedera spawned outside Swamp Dungeon — despawning");
                return;
            }
            if (typeIdLower.contains("endgame_golem_void")
                    && !EndgameQoL.isGolemVoidInstance(world)) {
                npc.setDespawning(true);
                plugin.getLogger().atWarning().log(
                        "[EntitySetupSystem] Endgame_Golem_Void spawned outside Golem Void instance — despawning");
                return;
            }

            // Register EL world-level override for this dungeon instance (dynamic world names)
            if (plugin.isEndlessLevelingActive()) {
                var elBridge = plugin.getEndlessLevelingBridge();
                String worldName = world.getName();
                if (EndgameQoL.isFrozenDungeonInstance(world)) {
                    elBridge.registerWorldLevelOverride("eg_" + worldName, worldName, 80, 110);
                } else if (EndgameQoL.isSwampDungeonInstance(world)) {
                    elBridge.registerWorldLevelOverride("eg_" + worldName, worldName, 100, 135);
                } else if (EndgameQoL.isGolemVoidInstance(world)) {
                    elBridge.registerWorldLevelOverride("eg_" + worldName, worldName, 110, 155);
                }
            }

            // Log detection event
            manager.onBossDetected(typeId);

            // Apply boss health IMMEDIATELY at spawn via BossHealthManager
            ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
            if (statType != null) {
                EntityStatMap statMap = holder.getComponent(statType);
                if (statMap != null) {
                    BossHealthManager healthManager = plugin.getBossHealthManager();
                    if (healthManager != null) {
                        com.hypixel.hytale.server.core.entity.UUIDComponent npcUuidComp = holder.getComponent(
                                com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
                        java.util.UUID npcUuid = npcUuidComp != null ? npcUuidComp.getUuid() : null;
                        healthManager.applyBossHealth(npc, statMap, world, npcUuid);
                    }
                }
            }

            // Handle Endgame_Golem_Void specific logic
            if (typeId.toLowerCase().contains("endgame_golem_void")) {
                plugin.getLogger().atFine().log("[EntitySetupSystem] Endgame_Golem_Void spawned! Boss bar will show on first damage.");
            }
        }

        @Override
        public void onEntityRemoved(@Nonnull Holder<EntityStore> holder, @Nonnull RemoveReason reason,
                @Nonnull Store<EntityStore> store) {
            ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
            if (npcType == null) return;

            NPCEntity npc = holder.getComponent(npcType);
            if (npc == null) return;

            String typeId = npc.getNPCTypeId();
            if (typeId == null || !manager.isBoss(typeId)) return;

            // Untrack boss so respawned entities with same UUID can get health applied
            com.hypixel.hytale.server.core.entity.UUIDComponent uuidComp = holder.getComponent(
                    com.hypixel.hytale.server.core.entity.UUIDComponent.getComponentType());
            java.util.UUID uuid = uuidComp != null ? uuidComp.getUuid() : null;
            if (uuid != null) {
                BossHealthManager healthManager = plugin.getBossHealthManager();
                if (healthManager != null) {
                    healthManager.untrackBoss(uuid);
                }
            }
        }
    }

    // =========================================================================
    // StatRefreshSystem - Catches EntityStatMap changes via RefChangeSystem
    // Applies health if not already applied (backup for edge cases)
    // =========================================================================

    public static class StatRefreshSystem extends RefChangeSystem<EntityStore, EntityStatMap> {
        private final EndgameQoL plugin;
        private final MobManager manager;

        public StatRefreshSystem(EndgameQoL plugin, MobManager manager) {
            this.plugin = plugin;
            this.manager = manager;
        }

        @Nonnull
        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }

        @Nonnull
        @Override
        public ComponentType<EntityStore, EntityStatMap> componentType() {
            ComponentType<EntityStore, EntityStatMap> type = EntityStatMap.getComponentType();
            if (type == null)
                throw new IllegalStateException("EntityStatMap component type not found");
            return type;
        }

        @Override
        public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull EntityStatMap component,
                @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            applyHealthIfBoss(ref, component, store);
        }

        @Override
        public void onComponentSet(@Nonnull Ref<EntityStore> ref, @Nullable EntityStatMap previous,
                @Nonnull EntityStatMap current, @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            // Don't re-apply on component set, only on add
        }

        @Override
        public void onComponentRemoved(@Nonnull Ref<EntityStore> ref, @Nonnull EntityStatMap component,
                @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
            // No cleanup needed - death handled by vanilla game
        }

        private void applyHealthIfBoss(Ref<EntityStore> ref, EntityStatMap statMap, Store<EntityStore> store) {
            if (store == null || ref == null || !ref.isValid()) return;

            ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
            if (npcType == null) return;

            NPCEntity npc = store.getComponent(ref, npcType);
            if (npc == null) return;

            String typeId = npc.getNPCTypeId();
            if (typeId == null || !manager.isBoss(typeId)) return;

            plugin.getLogger().atFine().log("[StatRefreshSystem] Stats added for boss: %s", typeId);

            // Apply health via BossHealthManager (will be skipped if already applied)
            World world = store.getExternalData().getWorld();
            BossHealthManager healthManager = plugin.getBossHealthManager();
            if (healthManager != null) {
                healthManager.applyBossHealth(npc, statMap, world,
                        endgame.plugin.utils.EntityUtils.getUuid(store, ref));
            }
        }
    }
}
