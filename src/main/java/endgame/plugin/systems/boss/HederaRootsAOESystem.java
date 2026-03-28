package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.boss.GenericBossManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically summons roots in an AOE around Hedera boss.
 * Players caught in the AOE are rooted (immobilized).
 *
 * Features:
 * - 8-10 second cooldown between AOE attacks
 * - 5 block radius around Hedera
 * - Applies Root status effect to players in range
 */
public class HederaRootsAOESystem extends EntityTickingSystem<EntityStore> {

    private static final float AOE_COOLDOWN_MIN = 8.0f; // Minimum 8 seconds between AOE
    private static final float AOE_COOLDOWN_MAX = 10.0f; // Maximum 10 seconds between AOE
    private static final float AOE_RADIUS = 5.0f; // 5 block radius
    private static final float ROOT_DURATION = 3.0f; // 3 seconds immobilization
    private static final String ROOT_EFFECT_ID = "Root";

    // Query for Hedera NPCs with stats
    private static final Query<EntityStore> QUERY = Query.and(
            NPCEntity.getComponentType(),
            EntityStatMap.getComponentType(),
            TransformComponent.getComponentType()
    );

    private final EndgameQoL plugin;
    private volatile GenericBossManager genericBossManager;

    // Track cooldowns per Hedera entity (by Ref)
    private final Map<Ref<EntityStore>, HederaAOEState> hederaStates = new ConcurrentHashMap<>();

    // M2 FIX: Time-based cleanup instead of per-entity tick counter (per-world)
    private final ConcurrentHashMap<Store<EntityStore>, Long> lastCleanupTimes = new ConcurrentHashMap<>();
    private static final long CLEANUP_INTERVAL_MS = 30000; // 30 seconds

    private static class HederaAOEState {
        float cooldownRemaining;
        float nextCooldown;

        HederaAOEState() {
            // Start with random cooldown so attacks are staggered
            this.cooldownRemaining = 2.0f + ThreadLocalRandom.current().nextFloat() * 4.0f;
            this.nextCooldown = generateCooldown();
        }

        static float generateCooldown() {
            return AOE_COOLDOWN_MIN + ThreadLocalRandom.current().nextFloat() * (AOE_COOLDOWN_MAX - AOE_COOLDOWN_MIN);
        }
    }

    public HederaRootsAOESystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    public void setGenericBossManager(GenericBossManager manager) {
        this.genericBossManager = manager;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null; // Run in default group
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        // Periodic cleanup of invalid refs (time-based, per-store to avoid cross-world starvation)
        long now = System.currentTimeMillis();
        Long lastCleanup = lastCleanupTimes.get(store);
        if (lastCleanup == null || now - lastCleanup >= CLEANUP_INTERVAL_MS) {
            lastCleanupTimes.put(store, now);
            cleanupInvalidRefs();
        }

        // Get NPC entity
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) return;

        NPCEntity npcEntity = archetypeChunk.getComponent(index, npcType);
        if (npcEntity == null) return;

        String typeId = npcEntity.getNPCTypeId();
        if (typeId == null) return;

        String lowerType = typeId.toLowerCase();
        if (!lowerType.contains("endgame_hedera")) {
            return;
        }

        // Get Ref for this Hedera entity
        Ref<EntityStore> hederaRef = archetypeChunk.getReferenceTo(index);
        if (hederaRef == null || !hederaRef.isValid()) return;

        // Get or create state for this Hedera
        HederaAOEState state = hederaStates.computeIfAbsent(hederaRef, k -> new HederaAOEState());

        // Update cooldown
        state.cooldownRemaining -= dt;
        if (state.cooldownRemaining > 0) {
            return; // Still on cooldown
        }

        // Reset cooldown for next attack
        state.cooldownRemaining = state.nextCooldown;
        state.nextCooldown = HederaAOEState.generateCooldown();

        // Get Hedera's position
        ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
        if (transformType == null) return;

        TransformComponent transform = archetypeChunk.getComponent(index, transformType);
        if (transform == null) return;

        Vector3d hederaPos = transform.getPosition();
        if (hederaPos == null) return;

        // Get world for player iteration
        World world = store.getExternalData().getWorld();

        // Scale AOE radius and root duration by phase (from GenericBossManager)
        float effectiveRadius = AOE_RADIUS;
        float effectiveDuration = ROOT_DURATION;
        if (genericBossManager != null) {
            int phase = genericBossManager.getCurrentPhase(hederaRef);
            if (phase >= 2) {
                effectiveRadius = phase == 2 ? 7.0f : 9.0f;  // P1:5, P2:7, P3:9
                effectiveDuration = phase == 2 ? 4.0f : 5.0f; // P1:3, P2:4, P3:5
            }
        }

        // Apply root to all players within AOE radius
        int rootedCount = applyRootToNearbyPlayers(world, hederaPos, commandBuffer, effectiveRadius, effectiveDuration);

        if (rootedCount > 0) {
            plugin.getLogger().atFine().log(
                "[HederaRootsAOESystem] Hedera summoned roots at (%.1f, %.1f, %.1f) - rooted %d players (radius=%.1f, dur=%.1f)",
                hederaPos.x, hederaPos.y, hederaPos.z, rootedCount, effectiveRadius, effectiveDuration);
        } else {
            plugin.getLogger().atFine().log(
                "[HederaRootsAOESystem] Hedera summoned roots at (%.1f, %.1f, %.1f) - no players in range",
                hederaPos.x, hederaPos.y, hederaPos.z);
        }
    }

    /**
     * Apply Root effect to all players within radius of the given position.
     * @return Number of players rooted
     */
    private int applyRootToNearbyPlayers(World world, Vector3d center, CommandBuffer<EntityStore> commandBuffer,
                                          float radius, float duration) {
        int rootedCount = 0;

        EntityEffect rootEffect = EntityEffect.getAssetMap().getAsset(ROOT_EFFECT_ID);
        if (rootEffect == null) {
            plugin.getLogger().atWarning().log("[HederaRootsAOESystem] Root effect asset not found!");
            return 0;
        }

        ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
        ComponentType<EntityStore, EffectControllerComponent> effectType = EffectControllerComponent.getComponentType();

        if (transformType == null || effectType == null) {
            return 0;
        }

        // Iterate through all players
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) continue;

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;

            Store<EntityStore> playerStore = ref.getStore();
            World playerWorld = playerStore.getExternalData().getWorld();
            if (!playerWorld.equals(world)) continue;

            // Get player position
            TransformComponent playerTransform = playerStore.getComponent(ref, transformType);
            if (playerTransform == null) continue;

            Vector3d playerPos = playerTransform.getPosition();
            if (playerPos == null) continue;

            // Check distance (2D horizontal distance for ground-based AOE)
            double dx = playerPos.x - center.x;
            double dz = playerPos.z - center.z;
            double distanceSq = dx * dx + dz * dz;

            if (distanceSq > radius * radius) {
                continue; // Player too far
            }

            // Apply root effect
            EffectControllerComponent effectController = playerStore.getComponent(ref, effectType);
            if (effectController == null) continue;

            boolean applied = effectController.addEffect(
                    ref,
                    rootEffect,
                    duration,
                    OverlapBehavior.OVERWRITE,
                    playerStore
            );

            if (applied) {
                rootedCount++;
                plugin.getLogger().atFine().log(
                    "[HederaRootsAOESystem] Rooted player at distance %.1f", Math.sqrt(distanceSq));
            }
        }

        return rootedCount;
    }

    /**
     * Clean up tracking for Hedera entities that no longer exist.
     * Called periodically or on plugin shutdown.
     */
    public void cleanup() {
        hederaStates.clear();
    }

    /**
     * Remove invalid refs from tracking map.
     * Called periodically to prevent memory leaks.
     */
    public void cleanupInvalidRefs() {
        hederaStates.entrySet().removeIf(entry -> {
            try { return !entry.getKey().isValid(); } catch (Exception e) { return true; }
        });
    }
}
