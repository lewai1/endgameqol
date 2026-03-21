package endgame.plugin.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Lightweight ECS system that tracks successful crafts for bounty + achievement hooks.
 * Listens on CraftRecipeEvent.Post (after the craft succeeds).
 */
public class CraftTracker extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {

    private final EndgameQoL plugin;

    public CraftTracker(EndgameQoL plugin) {
        super(CraftRecipeEvent.Post.class);
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        ComponentType<EntityStore, Player> playerType = Player.getComponentType();
        if (playerType == null) return Query.any();
        return Query.and(playerType);
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull CraftRecipeEvent.Post event) {
        // Get the OUTPUT item ID (not the recipe ID)
        String itemId = null;
        try {
            var recipe = event.getCraftedRecipe();
            if (recipe != null) {
                var output = recipe.getPrimaryOutput();
                if (output != null) {
                    itemId = output.getItemId();
                }
            }
        } catch (Exception e) {
            return;
        }
        if (itemId == null) return;

        // Resolve player UUID
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        UUID playerUuid = null;
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef != null && ref.equals(pRef.getReference())) {
                playerUuid = pRef.getUuid();
                break;
            }
        }
        if (playerUuid == null) return;

        // Bounty CRAFT_ITEM hook
        var bountyManager = plugin.getBountyManager();
        if (bountyManager != null) {
            bountyManager.onCraft(playerUuid, itemId);
        }

        // Achievement crafting hook
        var achievementManager = plugin.getAchievementManager();
        if (achievementManager != null) {
            achievementManager.onCraft(playerUuid, itemId);
        }

        plugin.getLogger().atFine().log("[CraftTracker] %s crafted %s", playerUuid, itemId);
    }
}
