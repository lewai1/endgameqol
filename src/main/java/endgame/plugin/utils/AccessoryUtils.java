package endgame.plugin.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.AccessoryPouchData;
import endgame.plugin.config.AccessoryPouchStorage;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Utility methods for checking accessory pouch contents.
 * Verifies that the player has the Trinket Pouch item in their inventory
 * before reading persisted AccessoryPouchStorage.
 */
public final class AccessoryUtils {

    private static final String POUCH_ITEM_ID = "Endgame_Accessory_Pouch";

    private AccessoryUtils() {}

    /**
     * Check if a player has a specific accessory equipped in their pouch.
     * Returns false if the player doesn't have the pouch item in their inventory.
     */
    public static boolean hasAccessory(@Nonnull EndgameQoL plugin, @Nonnull UUID uuid, @Nonnull String itemId) {
        try {
            if (!playerHasPouchInInventory(uuid)) return false;

            AccessoryPouchData pouch = getPouch(plugin, uuid);
            for (int i = 0; i < AccessoryPouchData.MAX_SLOTS; i++) {
                if (pouch.isSlotOccupied(i) && itemId.equals(pouch.getItemId(i))) {
                    return true;
                }
            }
        } catch (Exception e) {
            EndgameQoL.getInstance().getLogger().atFine().log("[Accessory] Check failed: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Store-aware overload for use inside ECS systems (DamageEventSystem, TickingSystem, etc.).
     * Uses the provided store and ref directly — avoids iterating Universe.getPlayers() and
     * calling ref.getStore() which can fail during certain ECS tick states.
     */
    public static boolean hasAccessory(@Nonnull EndgameQoL plugin, @Nonnull UUID uuid,
                                        @Nonnull String itemId,
                                        @Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        try {
            InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
            InventoryComponent.Backpack backpack = store.getComponent(playerRef, InventoryComponent.Backpack.getComponentType());

            boolean hasPouchInHotbar = hotbar != null && containsItem(hotbar.getInventory(), POUCH_ITEM_ID);
            boolean hasPouchInBackpack = backpack != null && containsItem(backpack.getInventory(), POUCH_ITEM_ID);

            if (!hasPouchInHotbar && !hasPouchInBackpack) {
                return false;
            }

            AccessoryPouchData pouch = getPouch(plugin, uuid);
            for (int i = 0; i < AccessoryPouchData.MAX_SLOTS; i++) {
                if (pouch.isSlotOccupied(i) && itemId.equals(pouch.getItemId(i))) {
                    return true;
                }
            }
        } catch (Exception e) {
            EndgameQoL.getInstance().getLogger().atFine().log("[Accessory] Check failed: %s", e.getMessage());
        }
        return false;
    }

    /**
     * Get all equipped accessory item IDs for a player.
     * Returns empty list if the player doesn't have the pouch item in their inventory.
     */
    @Nonnull
    public static List<String> getEquippedAccessories(@Nonnull EndgameQoL plugin, @Nonnull UUID uuid) {
        List<String> result = new ArrayList<>();
        try {
            if (!playerHasPouchInInventory(uuid)) return result;

            AccessoryPouchData pouch = getPouch(plugin, uuid);
            for (int i = 0; i < AccessoryPouchData.MAX_SLOTS; i++) {
                if (pouch.isSlotOccupied(i)) {
                    result.add(pouch.getItemId(i));
                }
            }
        } catch (Exception e) {
            EndgameQoL.getInstance().getLogger().atFine().log("[Accessory] Check failed: %s", e.getMessage());
        }
        return result;
    }

    /**
     * Get pouch data from ECS component, falling back to legacy storage.
     */
    private static AccessoryPouchData getPouch(@Nonnull EndgameQoL plugin, @Nonnull UUID uuid) {
        endgame.plugin.components.PlayerEndgameComponent comp = plugin.getPlayerComponent(uuid);
        if (comp != null) {
            return comp.getAccessoryPouchData();
        }
        // Fallback to legacy storage during migration
        AccessoryPouchStorage storage = plugin.getAccessoryPouchConfig().get();
        return storage.getAccessoryPouch(uuid.toString());
    }

    /**
     * Check if the player has the Trinket Pouch item in their hotbar or backpack.
     */
    private static boolean playerHasPouchInInventory(@Nonnull UUID uuid) {
        for (PlayerRef pr : Universe.get().getPlayers()) {
            if (pr == null) continue;
            UUID prUuid = EntityUtils.getUuid(pr);
            if (!uuid.equals(prUuid)) continue;

            Ref<EntityStore> ref = pr.getReference();
            if (ref == null || !ref.isValid()) return false;

            Store<EntityStore> store = ref.getStore();

            InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
            InventoryComponent.Backpack backpack = store.getComponent(ref, InventoryComponent.Backpack.getComponentType());

            boolean hasPouchInHotbar = hotbar != null && containsItem(hotbar.getInventory(), POUCH_ITEM_ID);
            boolean hasPouchInBackpack = backpack != null && containsItem(backpack.getInventory(), POUCH_ITEM_ID);

            return hasPouchInHotbar || hasPouchInBackpack;
        }
        return false;
    }

    private static boolean containsItem(ItemContainer container, String itemId) {
        if (container == null) return false;
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getItem() == null) continue;
            if (itemId.equals(stack.getItem().getId())) return true;
        }
        return false;
    }
}
