package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Damages NPCs along the Prisma Daggers blink teleport path (Shadow Trail).
 * Trail data is written by DaggerVanishSystem.executeBlinkForward() and consumed here.
 * Each trail lasts a single processing window (200ms) then expires.
 */
public class BlinkTrailDamageSystem extends EntityTickingSystem<EntityStore> {

    private static final long TRAIL_EXPIRY_MS = 200;
    private static final double TRAIL_RADIUS = 1.5;
    private static volatile com.hypixel.hytale.server.core.modules.entity.damage.DamageCause cachedPhysicalCause;

    private final EndgameQoL plugin;

    // Pending trails: playerUUID → trail data. Written by DaggerVanishSystem, consumed here.
    private final ConcurrentHashMap<UUID, TrailData> pendingTrails = new ConcurrentHashMap<>();
    // Track which NPCs have already been damaged by which trail to avoid double-hits
    private final ConcurrentHashMap<UUID, Set<Ref<EntityStore>>> damagedNpcs = new ConcurrentHashMap<>();

    public record TrailData(Vector3d origin, Vector3d destination, Ref<EntityStore> playerRef, long createdAt) {}

    public BlinkTrailDamageSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    public void addTrail(UUID playerUuid, TrailData trail) {
        pendingTrails.put(playerUuid, trail);
        damagedNpcs.put(playerUuid, ConcurrentHashMap.newKeySet());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        if (pendingTrails.isEmpty()) return;

        EndgameConfig config = plugin.getConfig().get();
        if (!config.isDaggerTrailEnabled()) return;

        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) return;

        Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(index);
        if (npcRef == null || !npcRef.isValid()) return;

        Vector3d npcPos = transform.getPosition();
        long now = System.currentTimeMillis();

        Iterator<Map.Entry<UUID, TrailData>> it = pendingTrails.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, TrailData> entry = it.next();
            TrailData trail = entry.getValue();

            // Expire old trails
            if (now - trail.createdAt > TRAIL_EXPIRY_MS) {
                it.remove();
                damagedNpcs.remove(entry.getKey());
                continue;
            }

            Set<Ref<EntityStore>> damaged = damagedNpcs.get(entry.getKey());
            if (damaged != null && damaged.contains(npcRef)) continue;

            // Distance from NPC to the trail line segment
            double dist = pointToSegmentDistance(npcPos, trail.origin, trail.destination);
            if (dist <= TRAIL_RADIUS) {
                float trailDamage = config.getDaggerTrailDamage();
                Damage damage = new Damage(
                        new Damage.EntitySource(trail.playerRef),
                        getPhysicalDamageCause(),
                        trailDamage);
                DamageSystems.executeDamage(npcRef, commandBuffer, damage);

                // Mark NPC as damaged for this trail
                if (damaged != null) {
                    damaged.add(npcRef);
                }

                plugin.getLogger().atFine().log(
                        "[BlinkTrail] NPC hit for %.0f damage (dist=%.2f)", trailDamage, dist);
            }
        }
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType(), TransformComponent.getComponentType(),
                EntityStatMap.getComponentType());
    }

    /**
     * Distance from point P to line segment AB.
     */
    private static double pointToSegmentDistance(Vector3d p, Vector3d a, Vector3d b) {
        double abx = b.getX() - a.getX();
        double aby = b.getY() - a.getY();
        double abz = b.getZ() - a.getZ();

        double apx = p.getX() - a.getX();
        double apy = p.getY() - a.getY();
        double apz = p.getZ() - a.getZ();

        double abLenSq = abx * abx + aby * aby + abz * abz;
        if (abLenSq < 0.001) {
            // Degenerate segment — just distance to point A
            return Math.sqrt(apx * apx + apy * apy + apz * apz);
        }

        double t = (apx * abx + apy * aby + apz * abz) / abLenSq;
        t = Math.max(0.0, Math.min(1.0, t));

        double closestX = a.getX() + t * abx;
        double closestY = a.getY() + t * aby;
        double closestZ = a.getZ() + t * abz;

        double dx = p.getX() - closestX;
        double dy = p.getY() - closestY;
        double dz = p.getZ() - closestZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        pendingTrails.remove(playerUuid);
        damagedNpcs.remove(playerUuid);
    }

    public void forceClear() {
        pendingTrails.clear();
        damagedNpcs.clear();
    }

    private static com.hypixel.hytale.server.core.modules.entity.damage.DamageCause getPhysicalDamageCause() {
        var cause = cachedPhysicalCause;
        if (cause == null) {
            cause = com.hypixel.hytale.server.core.modules.entity.damage.DamageCause.getAssetMap().getAsset("Physical");
            cachedPhysicalCause = cause;
        }
        return cause;
    }
}
