package endgame.plugin.systems.trial;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;

/**
 * Ticks the WardenTrialManager each frame to drive the trial state machine.
 * Uses an EntityTickingSystem with Player query + rate limiting (500ms).
 *
 * Only one player's tick actually drives the manager (via time guard).
 * Pattern matches DangerZoneTickSystem.
 */
public class WardenTrialTickSystem extends EntityTickingSystem<EntityStore> {

    private final EndgameQoL plugin;
    private final WardenTrialManager manager;

    private final java.util.concurrent.ConcurrentHashMap<Store<EntityStore>, Long> lastTickTimes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long TICK_INTERVAL_MS = 500;

    private static final Query<EntityStore> QUERY = Query.and(
            TransformComponent.getComponentType(),
            EntityStatMap.getComponentType(),
            Player.getComponentType());

    public WardenTrialTickSystem(EndgameQoL plugin, WardenTrialManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        long now = System.currentTimeMillis();
        Long lastTick = lastTickTimes.get(store);
        if (lastTick != null && now - lastTick < TICK_INTERVAL_MS) return;
        lastTickTimes.put(store, now);

        try {
            manager.tick(store);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[WardenChallenge] Tick error: %s", e.getMessage());
        }
    }

    public void forceClear() {
        manager.forceClear();
    }
}
