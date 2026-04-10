package endgame.plugin.systems;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Protects blocks in all Endgame dungeon instances.
 * Matches any world name containing "endgame_" (instance names are lowercase).
 * Specific dungeons can have breakable/interactable block exceptions.
 */
public class BlockProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    // Per-dungeon breakable exceptions
    private static final Set<String> FROZEN_BREAKABLE = Set.of("Ore_Mithril_Snow");
    private static final Set<String> SWAMP_BREAKABLE = Set.of("Swamp_Gem");
    private static final Set<String> SWAMP_INTERACTABLE = Set.of(
            "Swamp_Dungeon_Door_Locked", "Swamp_Dungeon_Door_Unlocked");

    private final EndgameQoL plugin;

    public BlockProtectionSystem(EndgameQoL plugin) {
        super(BreakBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event) {

        if (!plugin.getConfig().get().isEnableDungeonBlockProtection()) return;

        String worldName = store.getExternalData().getWorld().getName().toLowerCase();
        if (!isEndgameInstance(worldName)) return;

        if (event.getBlockType() == null) return;
        String blockId = event.getBlockType().getId();

        // Check per-dungeon exceptions
        if (worldName.contains("frozen_dungeon") && FROZEN_BREAKABLE.contains(blockId)) return;
        if (worldName.contains("swamp_dungeon") && SWAMP_BREAKABLE.contains(blockId)) return;

        // Block all breaking in endgame instances
        event.setCancelled(true);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    /**
     * Check if a world is an Endgame instance (any instance containing "endgame_").
     * Covers: Frozen Dungeon, Swamp Dungeon, Void Realm, Eldergrove, Oakwood, Canopy, and future instances.
     */
    static boolean isEndgameInstance(String worldNameLower) {
        return worldNameLower.contains("endgame_");
    }

    static boolean isProtectedWorld(Store<EntityStore> store) {
        String name = store.getExternalData().getWorld().getName().toLowerCase();
        return isEndgameInstance(name);
    }

    public static class DamageProtection extends EntityEventSystem<EntityStore, DamageBlockEvent> {

        private final EndgameQoL plugin;

        public DamageProtection(EndgameQoL plugin) {
            super(DamageBlockEvent.class);
            this.plugin = plugin;
        }

        @Override
        public void handle(int index,
                @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull DamageBlockEvent event) {

            if (!plugin.getConfig().get().isEnableDungeonBlockProtection()) return;

            String worldName = store.getExternalData().getWorld().getName().toLowerCase();
            if (!isEndgameInstance(worldName)) return;

            if (event.getBlockType() == null) return;
            String blockId = event.getBlockType().getId();

            // Per-dungeon exceptions
            if (worldName.contains("frozen_dungeon") && FROZEN_BREAKABLE.contains(blockId)) return;
            if (worldName.contains("swamp_dungeon")
                    && (SWAMP_BREAKABLE.contains(blockId) || SWAMP_INTERACTABLE.contains(blockId))) return;

            event.setCancelled(true);
            event.setDamage(0);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }

    public static class PlaceProtection extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

        private final EndgameQoL plugin;

        public PlaceProtection(EndgameQoL plugin) {
            super(PlaceBlockEvent.class);
            this.plugin = plugin;
        }

        @Override
        public void handle(int index,
                @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                @Nonnull Store<EntityStore> store,
                @Nonnull CommandBuffer<EntityStore> commandBuffer,
                @Nonnull PlaceBlockEvent event) {

            if (plugin.getConfig().get().isEnableDungeonBlockProtection() && isProtectedWorld(store)) {
                event.setCancelled(true);
            }
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Archetype.empty();
        }
    }
}
