package endgame.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ContainerWindow;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterActionType;
import com.hypixel.hytale.server.core.inventory.container.filter.SlotFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.AccessoryPouchData;
import endgame.plugin.config.AccessoryPouchStorage;

import java.util.UUID;

/**
 * Accessory Pouch UI — uses the native container system (like a chest) to display
 * a 6-slot persistent inventory for endgame accessories.
 *
 * Flow:
 *   1. Load saved items from AccessoryPouchData into a SimpleItemContainer
 *   2. Open with PageManager.setPageWithWindows (Page.Bench)
 *   3. On close, save container contents back to AccessoryPouchData
 *
 * Slot filter:
 *   - Only items with category containing "Endgame.Accessories" are accepted
 *   - No duplicate item IDs allowed in the container
 */
public class AccessoryPouchUI {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.AccessoryPouch");

    /**
     * Open the Accessory Pouch as a native container UI.
     */
    public static void openPouch(EndgameQoL plugin, Player player, PlayerRef playerRef,
                                 Ref<EntityStore> ref, Store<EntityStore> store) {
        UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(store, ref);
        if (uuid == null) return;
        String playerUuid = uuid.toString();

        // Get pouch data from ECS component
        endgame.plugin.components.PlayerEndgameComponent comp = plugin.getPlayerComponent(uuid);
        AccessoryPouchData pouch;
        if (comp != null) {
            pouch = comp.getAccessoryPouchData();
        } else {
            // Fallback to legacy storage during migration
            AccessoryPouchStorage storage = plugin.getAccessoryPouchConfig().get();
            pouch = storage.getAccessoryPouch(playerUuid);
        }

        // Create a 6-slot container and populate from saved data
        SimpleItemContainer container = new SimpleItemContainer((short) AccessoryPouchData.MAX_SLOTS);
        for (int i = 0; i < AccessoryPouchData.MAX_SLOTS; i++) {
            if (pouch.isSlotOccupied(i)) {
                try {
                    String itemId = pouch.getItemId(i);
                    int count = pouch.getCount(i);
                    double savedDurability = pouch.getDurability(i);
                    ItemStack stack;
                    if (savedDurability >= 0) {
                        Item item = Item.getAssetMap().getAsset(itemId);
                        double maxDur = item != null ? item.getMaxDurability() : savedDurability;
                        stack = new ItemStack(itemId, count, savedDurability, maxDur, null);
                    } else {
                        stack = new ItemStack(itemId, count);
                    }
                    container.setItemStackForSlot((short) i, stack);
                } catch (Exception e) {
                    LOGGER.atWarning().log("[AccessoryPouch] Failed to load slot %d (%s x%d): %s",
                            i, pouch.getItemId(i), pouch.getCount(i), e.getMessage());
                }
            }
        }

        // Slot filter: only Endgame.Accessories category, no duplicates
        SlotFilter accessoryFilter = (actionType, cont, slot, itemStack) -> {
            if (itemStack == null) return true;

            // Check category — must be an endgame accessory
            Item item = itemStack.getItem();
            if (item == null) return false;
            var categories = item.getCategories();
            boolean isAccessory = false;
            if (categories != null) {
                for (String cat : categories) {
                    if (cat != null && cat.contains("Endgame.Accessories")) {
                        isAccessory = true;
                        break;
                    }
                }
            }
            if (!isAccessory) return false;

            // Block duplicate item IDs
            String newItemId = itemStack.getItemId();
            for (short s = 0; s < AccessoryPouchData.MAX_SLOTS; s++) {
                if (s == slot) continue;
                ItemStack existing = cont.getItemStack(s);
                if (existing != null && !ItemStack.isEmpty(existing)
                        && newItemId.equals(existing.getItemId())) {
                    return false;
                }
            }
            return true;
        };
        for (short i = 0; i < AccessoryPouchData.MAX_SLOTS; i++) {
            container.setSlotFilter(FilterActionType.ADD, i, accessoryFilter);
        }

        // Wrap in a window
        ContainerWindow window = new ContainerWindow(container);

        // Capture pouch ref at open time — survives even if disconnect uncaches the component
        final AccessoryPouchData capturedPouch = pouch;

        // Register close event to save contents back
        window.registerCloseEvent(event -> {
            saveContainerToPouch(playerUuid, container, capturedPouch);
        });

        // Open the container UI using Page.Bench (only page type that supports container windows)
        boolean success = player.getPageManager().setPageWithWindows(
                ref, store, Page.Bench, true, window);

        if (!success) {
            LOGGER.atWarning().log("[AccessoryPouch] Failed to open container for %s", playerUuid);
        } else {
            LOGGER.atFine().log("[AccessoryPouch] Opened container for %s (%d/%d slots)",
                    playerUuid, pouch.getOccupiedCount(), AccessoryPouchData.MAX_SLOTS);
        }
    }

    /**
     * Save the container contents back to AccessoryPouchData.
     * Called on container close — this is the moment accessories become active/inactive.
     * Uses the pouch reference captured at open time — safe even if player disconnects
     * while the container is open (component may already be uncached).
     * No explicit file save needed — Hytale auto-persists the ECS component.
     */
    static void saveContainerToPouch(String playerUuid, SimpleItemContainer container,
                                     AccessoryPouchData pouch) {
        try {
            int occupied = 0;
            for (int i = 0; i < AccessoryPouchData.MAX_SLOTS; i++) {
                ItemStack stack = container.getItemStack((short) i);
                if (stack != null && !ItemStack.isEmpty(stack) && stack.getItem() != null) {
                    pouch.setItem(i, stack.getItem().getId(), stack.getQuantity(), stack.getDurability());
                    occupied++;
                } else {
                    pouch.clearSlot(i);
                }
            }

            LOGGER.atFine().log("[AccessoryPouch] Saved pouch for %s (%d/%d slots)",
                    playerUuid, occupied, AccessoryPouchData.MAX_SLOTS);
        } catch (Exception e) {
            LOGGER.atWarning().log("[AccessoryPouch] Failed to save pouch for %s: %s",
                    playerUuid, e.getMessage());
        }
    }
}
