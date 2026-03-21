package endgame.plugin.ui.snake;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Opens the snake minigame when a player interacts with an Arcade_Machine block.
 */
public class ArcadeMachineUseSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    public ArcadeMachineUseSystem() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull UseBlockEvent.Pre event) {
        if (!"Arcade_Machine".equals(event.getBlockType().getId())) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef playerRef = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null || playerRef == null) return;

        player.getPageManager().openCustomPage(ref, store, new SnakeGamePage(playerRef));
        event.setCancelled(true);
    }
}
