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

public class BlockProtectionSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final String FROZEN_DUNGEON_INSTANCE = "frozen_dungeon";
    private static final String SWAMP_DUNGEON_INSTANCE = "swamp_dungeon";

    private static final Set<String> FROZEN_DUNGEON_BREAKABLE = Set.of("Ore_Mithril_Snow");
    private static final Set<String> SWAMP_DUNGEON_BREAKABLE = Set.of("Swamp_Gem");
    private static final Set<String> SWAMP_DUNGEON_INTERACTABLE = Set.of(
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

        if (event.getBlockType() == null) return;
        String blockId = event.getBlockType().getId();

        if (worldName.contains(FROZEN_DUNGEON_INSTANCE)) {
            if (!FROZEN_DUNGEON_BREAKABLE.contains(blockId)) {
                event.setCancelled(true);
            }
        } else if (worldName.contains(SWAMP_DUNGEON_INSTANCE)) {
            if (!SWAMP_DUNGEON_BREAKABLE.contains(blockId)) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    static boolean isProtectedWorld(Store<EntityStore> store) {
        String name = store.getExternalData().getWorld().getName().toLowerCase();
        return name.contains(FROZEN_DUNGEON_INSTANCE) || name.contains(SWAMP_DUNGEON_INSTANCE);
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
            if (event.getBlockType() == null) return;
            String blockId = event.getBlockType().getId();

            if (worldName.contains(FROZEN_DUNGEON_INSTANCE)) {
                if (!FROZEN_DUNGEON_BREAKABLE.contains(blockId)) {
                    event.setCancelled(true);
                    event.setDamage(0);
                }
            } else if (worldName.contains(SWAMP_DUNGEON_INSTANCE)) {
                if (!SWAMP_DUNGEON_BREAKABLE.contains(blockId)
                        && !SWAMP_DUNGEON_INTERACTABLE.contains(blockId)) {
                    event.setCancelled(true);
                    event.setDamage(0);
                }
            }
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
