package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Automatically despawns Prisma Clone NPCs after their lifetime expires (default 5s).
 * Uses EntityTickingSystem pattern from FrostDragonSkyBoltSystem.
 */
public class PrismaMirageCleanupSystem extends EntityTickingSystem<EntityStore> {

    private static final String CLONE_NPC_TYPE_ID = "Prisma_Clone";
    private static final long CHECK_INTERVAL_MS = 200;

    private static final Query<EntityStore> QUERY = Query.and(
            TransformComponent.getComponentType(),
            NPCEntity.getComponentType());

    private final EndgameQoL plugin;
    // Key: Ref<EntityStore> directly — equals()/hashCode() are stable, toString() is NOT
    private final ConcurrentHashMap<Ref<EntityStore>, Long> firstSeenTimes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Ref<EntityStore>, Long> lastCheckTimes = new ConcurrentHashMap<>();

    public PrismaMirageCleanupSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String typeId = npc.getNPCTypeId();
        if (!CLONE_NPC_TYPE_ID.equals(typeId)) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        long now = System.currentTimeMillis();

        // Rate limit checks
        Long lastCheck = lastCheckTimes.get(ref);
        if (lastCheck != null && (now - lastCheck) < CHECK_INTERVAL_MS) return;
        lastCheckTimes.put(ref, now);

        // Track first-seen time
        Long firstSeen = firstSeenTimes.putIfAbsent(ref, now);
        if (firstSeen == null) {
            firstSeen = now;
        }

        // Check lifetime
        long lifetime = plugin.getConfig().get().getPrismaMirageLifetimeMs();
        if ((now - firstSeen) >= lifetime) {
            // Remove clone directly (avoids death pipeline — no drops/XP for mirages)
            try {
                commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            } catch (Exception e) {
                plugin.getLogger().atWarning().log(
                        "[PrismaMirageCleanup] Failed to despawn clone: %s", e.getMessage());
            }
            firstSeenTimes.remove(ref);
            lastCheckTimes.remove(ref);

            plugin.getLogger().atFine().log("[PrismaMirageCleanup] Clone despawned after %dms", lifetime);
        }
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    public void forceClear() {
        firstSeenTimes.clear();
        lastCheckTimes.clear();
    }
}
