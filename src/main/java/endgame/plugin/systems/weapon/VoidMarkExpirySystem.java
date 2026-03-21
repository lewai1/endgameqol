package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.VoidMarkComponent;
import endgame.plugin.utils.VoidMarkTracker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Removes expired VoidMarkComponents from entities and cleans up VoidMarkTracker.
 * Rate-limited to 500ms per entity check.
 */
public class VoidMarkExpirySystem extends EntityTickingSystem<EntityStore> {

    private static final long CHECK_INTERVAL_MS = 500;
    private static final long CLEANUP_INTERVAL_MS = 30000;

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, VoidMarkComponent> voidMarkType;
    private final VoidMarkTracker tracker;
    private final ConcurrentHashMap<Ref<EntityStore>, Long> lastCheckTimes = new ConcurrentHashMap<>();
    private volatile long lastCleanupTime = 0;

    public VoidMarkExpirySystem(EndgameQoL plugin,
                                 ComponentType<EntityStore, VoidMarkComponent> voidMarkType,
                                 VoidMarkTracker tracker) {
        this.plugin = plugin;
        this.voidMarkType = voidMarkType;
        this.tracker = tracker;
    }

    @Override
    public void tick(
            float dt,
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        VoidMarkComponent mark = archetypeChunk.getComponent(index, voidMarkType);
        if (mark == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        long now = System.currentTimeMillis();

        // Periodic cleanup of invalid Ref keys (prevents slow memory leak on long-running servers)
        if (now - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            lastCleanupTime = now;
            lastCheckTimes.keySet().removeIf(r -> !r.isValid());
        }

        // Rate limit
        Long lastCheck = lastCheckTimes.get(ref);
        if (lastCheck != null && (now - lastCheck) < CHECK_INTERVAL_MS) return;
        lastCheckTimes.put(ref, now);

        if (mark.isExpired()) {
            commandBuffer.removeComponent(ref, voidMarkType);
            tracker.removeMark(ref);
            lastCheckTimes.remove(ref);

            plugin.getLogger().atFine().log("[VoidMarkExpiry] Mark expired, removed from entity");
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
        return Query.and(voidMarkType);
    }

    public void forceClear() {
        lastCheckTimes.clear();
        tracker.cleanup();
    }
}
