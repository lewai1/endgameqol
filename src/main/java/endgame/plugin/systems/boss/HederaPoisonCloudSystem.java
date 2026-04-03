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
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PoisonComponent;
import endgame.plugin.managers.boss.GenericBossManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns lingering poison clouds around Hedera during Phase 2+.
 * Clouds apply poison to players within radius for their duration.
 * Uses vanilla "Impact_Poison" particle ID for visuals (Java-spawned particles
 * require vanilla IDs — custom plugin IDs only work in interaction JSON).
 */
public class HederaPoisonCloudSystem extends EntityTickingSystem<EntityStore> {

    private static final float CLOUD_COOLDOWN_MIN = 18.0f;
    private static final float CLOUD_COOLDOWN_MAX = 25.0f;
    private static final float CLOUD_DURATION = 6.0f;
    private static final float CLOUD_RADIUS = 5.0f;
    private static final float CLOUD_TICK_INTERVAL = 1.0f;
    private static final float CLOUD_POISON_DAMAGE = 6.0f;
    private static final int CLOUD_POISON_TICKS = 3;
    private static final String PARTICLE_ID = "Impact_Poison";

    private static final Query<EntityStore> QUERY = Query.and(
            NPCEntity.getComponentType(),
            EntityStatMap.getComponentType(),
            TransformComponent.getComponentType()
    );

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, PoisonComponent> poisonType;
    private volatile GenericBossManager genericBossManager;

    private final Map<Ref<EntityStore>, HederaCloudState> hederaStates = new ConcurrentHashMap<>();

    private static class PoisonCloud {
        final Vector3d position;
        float remainingDuration;
        float tickTimer;

        PoisonCloud(Vector3d pos) {
            this.position = new Vector3d(pos.x, pos.y, pos.z);
            this.remainingDuration = CLOUD_DURATION;
            this.tickTimer = 0;
        }
    }

    private static class HederaCloudState {
        float cooldownRemaining;
        final List<PoisonCloud> activeClouds = new ArrayList<>();

        HederaCloudState() {
            this.cooldownRemaining = 5.0f + ThreadLocalRandom.current().nextFloat() * 5.0f;
        }
    }

    public HederaPoisonCloudSystem(EndgameQoL plugin,
                                    ComponentType<EntityStore, PoisonComponent> poisonType) {
        this.plugin = plugin;
        this.poisonType = poisonType;
    }

    public void setGenericBossManager(GenericBossManager manager) {
        this.genericBossManager = manager;
    }

    @Nullable @Override
    public SystemGroup<EntityStore> getGroup() { return null; }

    @Nonnull @Override
    public Query<EntityStore> getQuery() { return QUERY; }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String typeId = npc.getNPCTypeId();
        if (typeId == null || !typeId.toLowerCase().contains("endgame_hedera")) return;

        Ref<EntityStore> hederaRef = chunk.getReferenceTo(index);
        if (hederaRef == null || !hederaRef.isValid()) return;

        // Phase 2+ only
        if (genericBossManager != null && genericBossManager.getCurrentPhase(hederaRef) < 2) return;

        TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null) return;

        World world = store.getExternalData().getWorld();
        HederaCloudState state = hederaStates.computeIfAbsent(hederaRef, k -> new HederaCloudState());

        // Collect player refs for particles
        List<Ref<EntityStore>> viewers = collectViewers(world);

        // Tick active clouds
        state.activeClouds.removeIf(cloud -> {
            cloud.remainingDuration -= dt;
            if (cloud.remainingDuration <= 0) return true;

            cloud.tickTimer -= dt;
            if (cloud.tickTimer <= 0) {
                cloud.tickTimer = CLOUD_TICK_INTERVAL;
                applyPoisonToNearbyPlayers(world, cloud.position, commandBuffer);
                spawnCloudParticle(cloud.position, viewers, store);
            }
            return false;
        });

        // Spawn new cloud on cooldown
        state.cooldownRemaining -= dt;
        if (state.cooldownRemaining <= 0) {
            state.cooldownRemaining = CLOUD_COOLDOWN_MIN +
                    ThreadLocalRandom.current().nextFloat() * (CLOUD_COOLDOWN_MAX - CLOUD_COOLDOWN_MIN);

            Vector3d pos = transform.getPosition();
            state.activeClouds.add(new PoisonCloud(pos));
            spawnCloudParticle(pos, viewers, store);

            plugin.getLogger().atFine().log(
                    "[HederaPoisonCloud] Spawned cloud at (%.1f, %.1f, %.1f)", pos.x, pos.y, pos.z);
        }
    }

    private void spawnCloudParticle(Vector3d pos, List<Ref<EntityStore>> viewers, Store<EntityStore> store) {
        if (viewers.isEmpty()) return;
        try {
            Store<EntityStore> particleStore = viewers.getFirst().getStore();
            ParticleUtil.spawnParticleEffect(PARTICLE_ID, pos, viewers, particleStore);
        } catch (Exception e) {
            // Silently ignore particle spawn failures
        }
    }

    private List<Ref<EntityStore>> collectViewers(World world) {
        List<Ref<EntityStore>> viewers = new ArrayList<>();
        for (PlayerRef pr : Universe.get().getPlayers()) {
            if (pr == null) continue;
            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) continue;
            if (ref.getStore().getExternalData().getWorld().equals(world)) {
                viewers.add(ref);
            }
        }
        return viewers;
    }

    private void applyPoisonToNearbyPlayers(World world, Vector3d center,
                                             CommandBuffer<EntityStore> commandBuffer) {
        for (PlayerRef playerRef : Universe.get().getPlayers()) {
            if (playerRef == null) continue;
            Ref<EntityStore> ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) continue;

            Store<EntityStore> playerStore = ref.getStore();
            if (!playerStore.getExternalData().getWorld().equals(world)) continue;

            TransformComponent playerTransform = playerStore.getComponent(ref, TransformComponent.getComponentType());
            if (playerTransform == null) continue;

            Vector3d playerPos = playerTransform.getPosition();
            double dx = playerPos.x - center.x;
            double dz = playerPos.z - center.z;
            if (dx * dx + dz * dz > CLOUD_RADIUS * CLOUD_RADIUS) continue;

            commandBuffer.run(s -> {
                PoisonComponent existing = s.getComponent(ref, poisonType);
                if (existing != null) {
                    existing.refresh(CLOUD_POISON_DAMAGE, 1.0f, CLOUD_POISON_TICKS,
                            PoisonComponent.PoisonSource.BOSS);
                } else {
                    s.addComponent(ref, poisonType,
                            new PoisonComponent(CLOUD_POISON_DAMAGE, 1.0f, CLOUD_POISON_TICKS,
                                    PoisonComponent.PoisonSource.BOSS));
                }
            });
        }
    }

    public void cleanup() {
        hederaStates.clear();
    }
}
