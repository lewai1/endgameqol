package endgame.plugin.systems.npc;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Custom interaction type that spawns NPCs near the caster.
 * Works in NPC attack chains (unlike vanilla SpawnNPC which only works for block interactions).
 *
 * Anti-exploit: per-caster cooldown (default 45s) + max alive cap (default 6).
 *
 * Registered in EndgameQoL.setup() as "EndgameSpawnNPC".
 *
 * Single spawn:  { "Type": "EndgameSpawnNPC", "EntityId": "Skeleton_Soldier" }
 * Multi spawn:   { "Type": "EndgameSpawnNPC", "EntityIds": ["Skeleton_Soldier", "Skeleton_Archer"], "SpawnCount": 6, "SpawnRadius": 4 }
 * With limits:   { ..., "MaxAlive": 8, "CooldownSeconds": 30 }
 */
public class EndgameSpawnNPCInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.SpawnNPC");

    // Per-caster state: tracks spawned refs and last summon time.
    // Key = Ref<EntityStore> directly (equals/hashCode work correctly on Ref).
    private static final Map<Ref<EntityStore>, CasterState> casterStates = new ConcurrentHashMap<>();

    private static class CasterState {
        volatile long lastSummonTime = 0;
        final List<Ref<EntityStore>> spawnedRefs = new CopyOnWriteArrayList<>();
    }

    private String entityId = "Sheep";
    private String[] entityIds = null;
    private int spawnCount = 1;
    private double spawnOffsetX = 0;
    private double spawnOffsetY = 0.5;
    private double spawnOffsetZ = 2;
    private double spawnRadius = 3;
    private int maxAlive = 6;
    private double cooldownSeconds = 45.0;

    public static final BuilderCodec<EndgameSpawnNPCInteraction> CODEC = BuilderCodec.builder(
            EndgameSpawnNPCInteraction.class, EndgameSpawnNPCInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("EntityId", Codec.STRING), (i, v) -> { if (v != null) i.entityId = v; }, i -> i.entityId).add()
            .append(new KeyedCodec<>("EntityIds", new ArrayCodec<>(Codec.STRING, String[]::new)), (i, v) -> { if (v != null) i.entityIds = v; }, i -> i.entityIds).add()
            .append(new KeyedCodec<>("SpawnCount", Codec.INTEGER), (i, v) -> { if (v != null) i.spawnCount = v; }, i -> i.spawnCount).add()
            .append(new KeyedCodec<>("SpawnOffsetX", Codec.DOUBLE), (i, v) -> { if (v != null) i.spawnOffsetX = v; }, i -> i.spawnOffsetX).add()
            .append(new KeyedCodec<>("SpawnOffsetY", Codec.DOUBLE), (i, v) -> { if (v != null) i.spawnOffsetY = v; }, i -> i.spawnOffsetY).add()
            .append(new KeyedCodec<>("SpawnOffsetZ", Codec.DOUBLE), (i, v) -> { if (v != null) i.spawnOffsetZ = v; }, i -> i.spawnOffsetZ).add()
            .append(new KeyedCodec<>("SpawnRadius", Codec.DOUBLE), (i, v) -> { if (v != null) i.spawnRadius = v; }, i -> i.spawnRadius).add()
            .append(new KeyedCodec<>("MaxAlive", Codec.INTEGER), (i, v) -> { if (v != null) i.maxAlive = v; }, i -> i.maxAlive).add()
            .append(new KeyedCodec<>("CooldownSeconds", Codec.DOUBLE), (i, v) -> { if (v != null) i.cooldownSeconds = v; }, i -> i.cooldownSeconds).add()
            .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        CasterState state = casterStates.computeIfAbsent(ref, k -> new CasterState());

        // Synchronized: cooldown check + alive-count check + cooldown set (atomic)
        long now = System.currentTimeMillis();
        long cooldownMs = (long) (cooldownSeconds * 1000);
        int canSpawn;
        synchronized (state) {
            if (now - state.lastSummonTime < cooldownMs) {
                LOGGER.atFine().log("[SpawnNPC] Caster %s on cooldown, skipping summon", ref);
                return;
            }

            // Prune dead/invalid refs and count alive
            state.spawnedRefs.removeIf(r -> r == null || !r.isValid());
            int alive = state.spawnedRefs.size();
            if (alive >= maxAlive) {
                LOGGER.atFine().log("[SpawnNPC] Caster %s has %d/%d alive, skipping summon", ref, alive, maxAlive);
                return;
            }

            state.lastSummonTime = now;
            canSpawn = Math.min(spawnCount, maxAlive - alive);
        }
        if (canSpawn <= 0) return;

        Vector3d casterPos = transform.getPosition();
        Vector3f casterRot = transform.getRotation();
        double yaw = casterRot.y;

        context.getCommandBuffer().run(s -> {
            ThreadLocalRandom rng = ThreadLocalRandom.current();

            for (int n = 0; n < canSpawn; n++) {
                // Pick entity ID: from EntityIds array (random) or fall back to EntityId
                String npcId;
                if (entityIds != null && entityIds.length > 0) {
                    npcId = entityIds[rng.nextInt(entityIds.length)];
                } else {
                    npcId = entityId;
                }

                double offsetX, offsetZ;
                if (canSpawn == 1) {
                    offsetX = spawnOffsetX;
                    offsetZ = spawnOffsetZ;
                } else {
                    double angle = -Math.PI / 3 + (Math.PI * 2.0 / 3.0) * n / (canSpawn - 1);
                    double dist = spawnRadius * (0.7 + rng.nextDouble() * 0.6);
                    offsetX = Math.sin(angle) * dist;
                    offsetZ = Math.cos(angle) * dist;
                }

                double sinYaw = Math.sin(yaw);
                double cosYaw = Math.cos(yaw);
                double worldX = casterPos.x + (offsetX * cosYaw - offsetZ * sinYaw);
                double worldY = casterPos.y + spawnOffsetY;
                double worldZ = casterPos.z + (offsetX * sinYaw + offsetZ * cosYaw);

                Vector3d spawnPos = new Vector3d(worldX, worldY, worldZ);
                Vector3f spawnRot = new Vector3f(0, casterRot.y, 0);
                var pair = NPCPlugin.get().spawnNPC(s, npcId, null, spawnPos, spawnRot);
                if (pair != null && pair.first() != null) {
                    state.spawnedRefs.add(pair.first());
                }
            }
        });
    }

    /**
     * Cleanup stale caster entries (call periodically or on shutdown).
     */
    public static void cleanupStaleEntries() {
        casterStates.entrySet().removeIf(e -> {
            // Remove if caster entity is dead/invalid
            if (!e.getKey().isValid()) return true;
            e.getValue().spawnedRefs.removeIf(r -> r == null || !r.isValid());
            return e.getValue().spawnedRefs.isEmpty() &&
                   System.currentTimeMillis() - e.getValue().lastSummonTime > 120_000;
        });
    }
}
