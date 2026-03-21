package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Periodically switches boss targets between nearby players.
 * Weighted random: NEAREST (40%), AGGRO (highest recent damage, 40%), RANDOM (20%).
 * Evaluated every 8-10 seconds per boss.
 */
public class BossTargetSwitchSystem extends EntityTickingSystem<EntityStore> {

    private static final double MAX_RANGE = 80.0;
    private static final double MAX_RANGE_SQ = MAX_RANGE * MAX_RANGE;

    // Per-boss cooldown tracking
    private final Map<Ref<EntityStore>, Long> nextSwitchTime = new ConcurrentHashMap<>();
    private volatile long lastCleanupTime = 0;
    private static final long CLEANUP_INTERVAL_MS = 60000;

    private final EndgameQoL plugin;
    private final GenericBossDamageSystem damageSystem;

    public BossTargetSwitchSystem(EndgameQoL plugin, GenericBossManager genericBossManager,
                                  GenericBossDamageSystem damageSystem) {
        this.plugin = plugin;
        this.damageSystem = damageSystem;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) return Query.any();
        return Query.and(npcType, TransformComponent.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (!plugin.getConfig().get().isBossTargetSwitchEnabled()) return;

        Ref<EntityStore> bossRef = archetypeChunk.getReferenceTo(index);
        if (bossRef == null || !bossRef.isValid()) return;

        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String npcTypeId = npc.getNPCTypeId();
        BossType bossType = BossType.fromTypeId(npcTypeId);
        if (bossType == null || !bossType.isBoss()) return;

        long now = System.currentTimeMillis();

        // Periodic cleanup of dead boss refs to prevent memory leak
        // Only check refs from THIS store to avoid cross-store thread assertions
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            nextSwitchTime.keySet().removeIf(ref -> ref.getStore() == store && !ref.isValid());
        }
        int intervalMs = plugin.getConfig().get().getBossTargetSwitchIntervalMs();

        Long nextTime = nextSwitchTime.get(bossRef);
        if (nextTime != null && now < nextTime) return;

        // Jitter: interval + random 0-2s
        nextSwitchTime.put(bossRef, now + intervalMs + ThreadLocalRandom.current().nextLong(2000));

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;
        Vector3d bossPos = transform.getPosition();
        if (bossPos == null) return;

        // Find all players within range
        List<PlayerRef> inRange = new ArrayList<>();
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef == null) continue;
            Ref<EntityStore> pRefEntity = pRef.getReference();
            if (pRefEntity == null || !pRefEntity.isValid()) continue;

            try {
                TransformComponent pTransform = pRefEntity.getStore().getComponent(
                        pRefEntity, TransformComponent.getComponentType());
                if (pTransform == null) continue;
                Vector3d pPos = pTransform.getPosition();
                if (pPos == null) continue;

                double dx = pPos.x - bossPos.x;
                double dy = pPos.y - bossPos.y;
                double dz = pPos.z - bossPos.z;
                if (dx * dx + dy * dy + dz * dz <= MAX_RANGE_SQ) {
                    inRange.add(pRef);
                }
            } catch (Exception e) {
                plugin.getLogger().atFine().log("[BossTargetSwitch] Cross-world player access: %s", e.getMessage());
            }
        }

        if (inRange.size() <= 1) return;

        // Weighted selection: NEAREST 40%, AGGRO 40%, RANDOM 20%
        int roll = ThreadLocalRandom.current().nextInt(100);
        PlayerRef chosen;
        if (roll < 40) {
            chosen = findNearest(inRange, bossPos);
        } else if (roll < 80) {
            chosen = findTopDamageDealer(inRange, bossRef);
        } else {
            chosen = inRange.get(ThreadLocalRandom.current().nextInt(inRange.size()));
        }

        if (chosen == null) return;

        Ref<EntityStore> targetRef = chosen.getReference();
        if (targetRef == null || !targetRef.isValid()) return;

        // Must set target on the boss's world thread
        World bossWorld = store.getExternalData().getWorld();
        if (bossWorld == null) return;
        bossWorld.execute(() -> {
            try {
                if (!bossRef.isValid()) return;
                NPCEntity npcEntity = bossRef.getStore().getComponent(bossRef, NPCEntity.getComponentType());
                if (npcEntity == null || npcEntity.getRole() == null) return;
                npcEntity.getRole().setMarkedTarget("target", targetRef);
                plugin.getLogger().atFine().log(
                        "[BossTargetSwitch] %s switched target (strategy=%s)",
                        bossType.getDisplayName(), roll < 40 ? "nearest" : roll < 80 ? "aggro" : "random");
            } catch (Exception e) {
                plugin.getLogger().atFine().log("[BossTargetSwitch] Failed: %s", e.getMessage());
            }
        });
    }

    private PlayerRef findNearest(List<PlayerRef> players, Vector3d bossPos) {
        PlayerRef nearest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (PlayerRef pRef : players) {
            try {
                Ref<EntityStore> ref = pRef.getReference();
                if (ref == null || !ref.isValid()) continue;
                TransformComponent t = ref.getStore().getComponent(ref, TransformComponent.getComponentType());
                if (t == null) continue;
                Vector3d pos = t.getPosition();
                if (pos == null) continue;
                double dx = pos.x - bossPos.x;
                double dy = pos.y - bossPos.y;
                double dz = pos.z - bossPos.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    nearest = pRef;
                }
            } catch (Exception e) {
                plugin.getLogger().atFine().log("[BossTargetSwitch] Error finding nearest: %s", e.getMessage());
            }
        }
        return nearest;
    }

    private PlayerRef findTopDamageDealer(List<PlayerRef> players, Ref<EntityStore> bossRef) {
        PlayerRef top = null;
        double topDamage = 0;
        Map<UUID, Double> damageMap = damageSystem.getRecentDamage(bossRef);
        if (damageMap == null || damageMap.isEmpty()) {
            // Fall back to random if no damage data
            return players.get(ThreadLocalRandom.current().nextInt(players.size()));
        }
        for (PlayerRef pRef : players) {
            UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(pRef);
            if (uuid == null) continue;
            Double dmg = damageMap.get(uuid);
            if (dmg != null && dmg > topDamage) {
                topDamage = dmg;
                top = pRef;
            }
        }
        return top != null ? top : players.get(ThreadLocalRandom.current().nextInt(players.size()));
    }

    public void forceClear() {
        nextSwitchTime.clear();
    }
}
