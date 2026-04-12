package endgame.wavearena;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import javax.annotation.Nonnull;

/**
 * ECS death system that tracks NPC deaths for the WaveArenaEngine.
 * Only processes entities tracked by the engine (ignores all other NPCs).
 */
public class WaveArenaDeathSystem extends DeathSystems.OnDeathSystem {

    private final WaveArenaEngine engine;

    public WaveArenaDeathSystem(@Nonnull WaveArenaEngine engine) {
        this.engine = engine;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store,
                                  @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (engine.isTrackedNpc(ref)) {
            engine.onNpcDeath(ref);
        }
    }
}
