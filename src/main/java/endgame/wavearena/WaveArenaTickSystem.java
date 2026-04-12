package endgame.wavearena;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS tick system that drives the WaveArenaEngine.
 * Rate-limited to 500ms per store to avoid over-ticking.
 */
public class WaveArenaTickSystem extends EntityTickingSystem<EntityStore> {

    private final WaveArenaEngine engine;
    private final ConcurrentHashMap<Store<EntityStore>, Long> lastTickTimes = new ConcurrentHashMap<>();
    private static final long TICK_INTERVAL_MS = 500;

    public WaveArenaTickSystem(@Nonnull WaveArenaEngine engine) {
        this.engine = engine;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> buffer) {
        long now = System.currentTimeMillis();
        Long lastTick = lastTickTimes.get(store);
        if (lastTick != null && (now - lastTick) < TICK_INTERVAL_MS) return;
        lastTickTimes.put(store, now);

        engine.tick(store);
    }
}
