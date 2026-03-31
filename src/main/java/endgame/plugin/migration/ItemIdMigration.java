package endgame.plugin.migration;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Migrates renamed item IDs in player inventories on every connect.
 * Scans all 6 inventory containers (hotbar, backpack, tools, armor, storage, utility).
 *
 * Usage in plugin setup():
 *   ItemIdMigration.register("Big_Rex_Cave_Leather", "Alpha_Rex_Leather");
 *
 * Then call ItemIdMigration.migratePlayer() from PlayerReadyEvent.
 * Thread-safe: called on world thread per-player, no shared mutable state.
 * Performance: ~150 slots x O(1) lookup = sub-millisecond per player.
 */
public final class ItemIdMigration {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.ItemMigration");

    /** Map of old item ID → new item ID. */
    private static final Map<String, String> MIGRATIONS = new ConcurrentHashMap<>();

    private ItemIdMigration() {}

    /**
     * Register an item ID rename. Call during plugin setup().
     * @param oldId the old item ID (will be converted FROM)
     * @param newId the new item ID (will be converted TO)
     */
    public static void register(String oldId, String newId) {
        MIGRATIONS.put(oldId, newId);
        LOGGER.atInfo().log("[ItemMigration] Registered: %s -> %s", oldId, newId);
    }

    /**
     * Migrate a player's inventory on connect. Runs every connect — safe to call repeatedly.
     * Must be called on the world thread (from PlayerReadyEvent).
     */
    public static void migratePlayer(PlayerRef playerRef, Store<EntityStore> store) {
        if (MIGRATIONS.isEmpty()) return;

        java.util.UUID uuid = playerRef.getUuid();
        if (uuid == null) return;
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        AtomicInteger count = new AtomicInteger(0);

        // Migrate all inventory containers (hotbar, backpack, tools, armor, storage, utility)
        try {
            var entityModule = com.hypixel.hytale.server.core.modules.entity.EntityModule.get();
            migrateComponentContainer(store, ref, entityModule.getHotbarInventoryComponentType(), count);
            migrateComponentContainer(store, ref, entityModule.getBackpackInventoryComponentType(), count);
            migrateComponentContainer(store, ref, entityModule.getToolInventoryComponentType(), count);
            migrateComponentContainer(store, ref, entityModule.getArmorInventoryComponentType(), count);
            migrateComponentContainer(store, ref, entityModule.getStorageInventoryComponentType(), count);
            migrateComponentContainer(store, ref, entityModule.getUtilityInventoryComponentType(), count);
        } catch (Exception e) {
            LOGGER.atWarning().log("[ItemMigration] Error migrating player %s: %s", uuid, e.getMessage());
        }

        if (count.get() > 0) {
            LOGGER.atInfo().log("[ItemMigration] Migrated %d item(s) for player %s", count.get(), uuid);
        }
    }

    /**
     * Migrate a chest's ItemContainer. Call when a chest is opened or loaded.
     */
    public static void migrateChestContainer(ItemContainer container) {
        if (MIGRATIONS.isEmpty() || container == null) return;
        AtomicInteger count = new AtomicInteger(0);
        migrateContainer(container, count);
        if (count.get() > 0) {
            LOGGER.atFine().log("[ItemMigration] Migrated %d item(s) in chest", count.get());
        }
    }

    /**
     * Scan all loaded chunks in a world and migrate items in all chests/containers.
     * Iterates ChunkStore → BlockComponentChunk → ItemContainerBlock → ItemContainer.
     * Only migrates chunks currently loaded — unloaded chunks are migrated when loaded.
     * Must be called on the world thread.
     */
    public static int migrateWorldChests(com.hypixel.hytale.server.core.universe.world.World world) {
        if (MIGRATIONS.isEmpty()) return 0;
        AtomicInteger total = new AtomicInteger(0);

        try {
            var chunkStore = world.getChunkStore();
            var store = chunkStore.getStore();
            var blockCompChunkType = com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk.getComponentType();
            var containerBlockType = com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock.getComponentType();

            for (long chunkIndex : chunkStore.getChunkIndexes()) {
                com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore> chunkRef =
                        chunkStore.getChunkReference(chunkIndex);
                if (chunkRef == null || !chunkRef.isValid()) continue;

                var blockCompChunk = store.getComponent(chunkRef, blockCompChunkType);
                if (blockCompChunk == null) continue;

                for (var entry : blockCompChunk.getEntityReferences().int2ReferenceEntrySet()) {
                    var blockRef = entry.getValue();
                    if (blockRef == null || !blockRef.isValid()) continue;

                    try {
                        var containerBlock = store.getComponent(blockRef, containerBlockType);
                        if (containerBlock != null) {
                            migrateContainer(containerBlock.getItemContainer(), total);
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[ItemMigration] Error migrating world chests: %s", e.getMessage());
        }

        if (total.get() > 0) {
            LOGGER.atInfo().log("[ItemMigration] Migrated %d item(s) in world chests", total.get());
        }
        return total.get();
    }

    private static <T extends com.hypixel.hytale.component.Component<EntityStore>> void migrateComponentContainer(
            Store<EntityStore> store, Ref<EntityStore> ref,
            com.hypixel.hytale.component.ComponentType<EntityStore, T> componentType,
            AtomicInteger count) {
        try {
            var comp = store.getComponent(ref, componentType);
            if (comp instanceof InventoryComponent inv) {
                migrateContainer(inv.getInventory(), count);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Scan a container and replace any old item IDs with new ones.
     */
    private static void migrateContainer(ItemContainer container, AtomicInteger count) {
        if (container == null) return;

        for (short i = 0; i < container.getCapacity(); i++) {
            ItemStack stack = container.getItemStack(i);
            if (stack == null || stack.isEmpty()) continue;

            String itemId = stack.getItemId();
            if (itemId == null) continue;

            String newId = MIGRATIONS.get(itemId);
            if (newId != null) {
                container.setItemStackForSlot(i, new ItemStack(newId, stack.getQuantity(), stack.getMetadata()));
                count.incrementAndGet();
            }
        }
    }

    /**
     * Auto-migrate chests when chunks are loaded from disk.
     * Register in plugin setup():
     *   getEventRegistry().registerGlobal(ChunkPreLoadProcessEvent.class, ItemIdMigration::onChunkLoaded);
     */
    public static void onChunkLoaded(com.hypixel.hytale.server.core.universe.world.events.ChunkPreLoadProcessEvent event) {
        if (MIGRATIONS.isEmpty()) return;
        if (event.isNewlyGenerated()) return;

        try {
            var holder = event.getHolder();
            var blockCompChunk = holder.getComponent(
                    com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk.getComponentType());
            if (blockCompChunk == null) return;

            var containerBlockType = com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock.getComponentType();
            AtomicInteger count = new AtomicInteger(0);

            for (var entry : blockCompChunk.getEntityHolders().int2ObjectEntrySet()) {
                var blockHolder = entry.getValue();
                if (blockHolder == null) continue;
                var containerBlock = blockHolder.getComponent(containerBlockType);
                if (containerBlock != null) {
                    migrateContainer(containerBlock.getItemContainer(), count);
                }
            }

            if (count.get() > 0) {
                LOGGER.atFine().log("[ItemMigration] Migrated %d item(s) in chunk on load", count.get());
            }
        } catch (Exception e) {
            LOGGER.atFine().log("[ItemMigration] Chunk load migration error: %s", e.getMessage());
        }
    }

    /** Clear all registrations (call on plugin shutdown). */
    public static void clear() {
        MIGRATIONS.clear();
    }
}
