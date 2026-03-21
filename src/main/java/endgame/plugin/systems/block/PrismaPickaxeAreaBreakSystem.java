package endgame.plugin.systems.block;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

/**
 * Prisma Pickaxe 3x3 area mining system.
 * When a block is broken with the Prisma Pickaxe in AreaBreak state,
 * surrounding blocks in a 3x3 area are also broken if they match
 * the pickaxe's gather types (rocks, ores, soils, soft blocks).
 * The 3x3 plane orients to the face being mined based on player look direction.
 */
public class PrismaPickaxeAreaBreakSystem extends EntityEventSystem<EntityStore, com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.PrismaPickaxe");
    private static final Set<String> ALLOWED_GATHER_TYPES = Set.of(
            "Rocks", "VolcanicRocks", "Soils", "SoftBlocks",
            "OreCopper", "OreIron", "OreSilver", "OreGold",
            "OreThorium", "OreCobalt", "OreAdamantite", "OreMithril");
    private static final double DURABILITY_LOSS_PER_BLOCK = 0.1;

    /** Re-entrancy guard to prevent recursive triggers from secondary breaks. */
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Players currently in AreaBreak mode. Set from EndgameStanceChangeInteraction, checked here. */
    private static final Set<java.util.UUID> AREA_BREAK_PLAYERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void setAreaBreakMode(java.util.UUID uuid, boolean enabled) {
        if (enabled) AREA_BREAK_PLAYERS.add(uuid);
        else AREA_BREAK_PLAYERS.remove(uuid);
    }

    public static void clearPlayer(java.util.UUID uuid) { AREA_BREAK_PLAYERS.remove(uuid); }

    public PrismaPickaxeAreaBreakSystem() {
        super(com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent.class);
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent event) {

        if (event.isCancelled()) return;
        if (PROCESSING.get()) return;

        // Check held item is Prisma Pickaxe
        ItemStack heldItem = event.getItemInHand();
        if (heldItem == null) return;
        String itemId = heldItem.getItemId();
        if (itemId == null || !itemId.contains("Pickaxe_Prisma")) return;

        // Check AreaBreak mode via player UUID set (set by EndgameStanceChangeInteraction).
        // Don't use isVariant()/itemId — the engine can return stale item state in events.
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        java.util.UUID playerUUID = endgame.plugin.utils.EntityUtils.getUuid(store, playerRef);
        if (playerUUID == null || !AREA_BREAK_PLAYERS.contains(playerUUID)) return;

        LOGGER.atFine().log("[PrismaPickaxe] 3x3 triggered, variant=%s", itemId);

        Vector3i center = event.getTargetBlock();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) return;
        String worldName = world.getName();

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;

        var claimBridge = endgame.plugin.integration.ClaimProtectionBridge.get();

        // Determine which face was mined using player eye position relative to block center.
        // The dominant axis (largest delta) is the "depth" axis — the 3x3 plane spans the other two.
        TransformComponent transform = store.getComponent(playerRef, TransformComponent.getComponentType());
        // depth axis: 0=X, 1=Y, 2=Z
        int depthAxis = 1; // default: Y (horizontal plane)
        if (transform != null) {
            var pos = transform.getPosition();
            double pdx = Math.abs(pos.getX() - (cx + 0.5));
            double pdy = Math.abs(pos.getY() - (cy + 0.5));
            double pdz = Math.abs(pos.getZ() - (cz + 0.5));
            if (pdx >= pdy && pdx >= pdz) {
                depthAxis = 0; // player is offset on X → face is YZ plane
            } else if (pdz >= pdy) {
                depthAxis = 2; // player is offset on Z → face is XY plane
            }
            // else depthAxis stays 1 → player is above/below → face is XZ plane
        }

        PROCESSING.set(Boolean.TRUE);
        try {
            int extraBroken = 0;

            for (int d1 = -1; d1 <= 1; d1++) {
                for (int d2 = -1; d2 <= 1; d2++) {
                    if (d1 == 0 && d2 == 0) continue; // center already broken by engine

                    // Re-validate player after each break
                    if (store.getComponent(playerRef, Player.getComponentType()) == null) {
                        LOGGER.atFine().log("Player removed during 3x3 break, aborting");
                        return;
                    }
                    if (!world.isAlive()) return;

                    // Spread on the 2 axes perpendicular to the depth axis
                    int bx = cx, by = cy, bz = cz;
                    switch (depthAxis) {
                        case 0 -> { by = cy + d1; bz = cz + d2; } // face is YZ
                        case 1 -> { bx = cx + d1; bz = cz + d2; } // face is XZ
                        case 2 -> { bx = cx + d1; by = cy + d2; } // face is XY
                    }

                    BlockType bt;
                    try {
                        bt = world.getBlockType(bx, by, bz);
                    } catch (Exception e) {
                        continue;
                    }
                    if (bt == null || "Empty".equals(bt.getId())) continue;

                    BlockGathering gathering = bt.getGathering();
                    if (gathering == null) continue;

                    BlockBreakingDropType breaking = gathering.getBreaking();
                    if (breaking == null) continue;

                    String gatherType = breaking.getGatherType();
                    if (gatherType == null || !ALLOWED_GATHER_TYPES.contains(gatherType)) {
                        LOGGER.atFine().log("[PrismaPickaxe] Skipped %s at %d,%d,%d (gatherType=%s)",
                                bt.getId(), bx, by, bz, gatherType);
                        continue;
                    }

                    // Check claim protection (SimpleClaims soft-dep) before breaking
                    if (!claimBridge.isBreakAllowed(playerUUID, worldName, bx, bz)) {
                        LOGGER.atFine().log("[PrismaPickaxe] Skipped %d,%d,%d (claim protected)", bx, by, bz);
                        continue;
                    }

                    // Cache drop info BEFORE breaking the block
                    String dropItemId = breaking.getItemId();
                    int dropQuantity = Math.max(breaking.getQuantity(), 1);
                    String dropListId = breaking.getDropListId();

                    // Break the block
                    try {
                        world.setBlock(bx, by, bz, "Empty");
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to break block at %d,%d,%d: %s", bx, by, bz, e.getMessage());
                        continue;
                    }
                    extraBroken++;

                    // Re-validate player after setBlock
                    player = store.getComponent(playerRef, Player.getComponentType());
                    if (player == null) {
                        LOGGER.atFine().log("Player removed after block break at %d,%d,%d, aborting", bx, by, bz);
                        return;
                    }

                    // Give drops to player
                    if (dropItemId != null && !dropItemId.isEmpty()) {
                        // Direct item drop (stone, cobble, etc.)
                        try {
                            ItemStack dropStack = new ItemStack(dropItemId, dropQuantity);
                            ItemStackTransaction transaction = player.giveItem(dropStack, playerRef, store);
                            ItemStack remainder = transaction.getRemainder();
                            if (remainder != null && !remainder.isEmpty()) {
                                ItemUtils.dropItem(playerRef, remainder, store);
                            }
                        } catch (Exception e) {
                            LOGGER.atFine().log("Failed to give drop %s: %s", dropItemId, e.getMessage());
                        }
                    } else if (dropListId != null && !dropListId.isEmpty()) {
                        // DropList-based drops (ores drop ore + cobble via drop list)
                        try {
                            List<ItemStack> dropStacks = ItemModule.get().getRandomItemDrops(dropListId);
                            for (ItemStack dropStack : dropStacks) {
                                ItemStackTransaction transaction = player.giveItem(
                                        dropStack, playerRef, store);
                                ItemStack remainder = transaction.getRemainder();
                                if (remainder != null && !remainder.isEmpty()) {
                                    ItemUtils.dropItem(playerRef, remainder, store);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.atFine().log("Failed to resolve drop list %s: %s", dropListId, e.getMessage());
                        }
                    }
                }
            }

            LOGGER.atFine().log("[PrismaPickaxe] 3x3 complete: %d extra blocks broken at %d,%d,%d", extraBroken, cx, cy, cz);

            // Reduce durability for extra blocks broken
            if (extraBroken > 0) {
                player = store.getComponent(playerRef, Player.getComponentType());
                if (player == null) return;

                double durabilityLoss = extraBroken * DURABILITY_LOSS_PER_BLOCK;
                double newDurability = heldItem.getDurability() - durabilityLoss;
                if (newDurability < 0) newDurability = 0;

                try {
                    var inv = player.getInventory();
                    ItemContainer container;
                    byte activeSlot;
                    if (inv.usingToolsItem()) {
                        container = inv.getTools();
                        activeSlot = inv.getActiveSlot(-8);
                    } else {
                        container = inv.getHotbar();
                        activeSlot = inv.getActiveSlot(-1);
                    }
                    if (activeSlot >= 0) {
                        ItemStack currentSlotItem = container.getItemStack((short) activeSlot);
                        if (currentSlotItem != null && currentSlotItem.getItemId() != null
                                && currentSlotItem.getItemId().contains("Pickaxe_Prisma")) {
                            container.setItemStackForSlot((short) activeSlot, currentSlotItem.withDurability(newDurability));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to update durability: %s", e.getMessage());
                }
            }
        } finally {
            PROCESSING.set(Boolean.FALSE);
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
