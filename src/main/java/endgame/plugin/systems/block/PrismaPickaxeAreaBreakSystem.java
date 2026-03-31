package endgame.plugin.systems.block;

import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Prisma Pickaxe 3x3 area mining system.
 * When a block is broken with the Prisma Pickaxe in AreaBreak state,
 * surrounding blocks in a 3x3 area are also broken if they match
 * the pickaxe's gather types (rocks, ores, soils, soft blocks).
 * The 3x3 plane orients to the face being mined based on player look direction.
 *
 * Uses a two-pass algorithm: scan all valid blocks first (read-only),
 * then break them all and give drops. This prevents world.setBlock() side effects
 * (block physics, neighbor updates) from aborting the loop mid-way.
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

    /** Block position + cached drop info, collected in the scan pass. */
    private record PendingBreak(int x, int y, int z, String dropItemId, int dropQuantity, String dropListId) {}

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent event) {

        if (event.isCancelled()) return;
        if (PROCESSING.get()) return;

        // Check AreaBreak mode via player UUID set first (most reliable check).
        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        java.util.UUID playerUUID = endgame.plugin.utils.EntityUtils.getUuid(store, playerRef);
        if (playerUUID == null || !AREA_BREAK_PLAYERS.contains(playerUUID)) return;

        // Check held item is Prisma Pickaxe
        ItemStack heldItem = event.getItemInHand();
        if (heldItem == null) return;
        String itemId = heldItem.getItemId();
        if (itemId == null || !itemId.contains("Pickaxe_Prisma")) return;

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
        }

        PROCESSING.set(Boolean.TRUE);
        try {
            // === PASS 1: Scan all valid blocks (read-only, no world mutation) ===
            List<PendingBreak> toBreak = new ArrayList<>(8);

            for (int d1 = -1; d1 <= 1; d1++) {
                for (int d2 = -1; d2 <= 1; d2++) {
                    if (d1 == 0 && d2 == 0) continue;

                    int bx = cx, by = cy, bz = cz;
                    switch (depthAxis) {
                        case 0 -> { by = cy + d1; bz = cz + d2; }
                        case 1 -> { bx = cx + d1; bz = cz + d2; }
                        case 2 -> { bx = cx + d1; by = cy + d2; }
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

                    if (!claimBridge.isBreakAllowed(playerUUID, worldName, bx, bz)) {
                        LOGGER.atFine().log("[PrismaPickaxe] Skipped %d,%d,%d (claim protected)", bx, by, bz);
                        continue;
                    }

                    toBreak.add(new PendingBreak(bx, by, bz,
                            breaking.getItemId(), Math.max(breaking.getQuantity(), 1), breaking.getDropListId()));
                }
            }

            if (toBreak.isEmpty()) return;

            // === PASS 2: Break blocks and give drops ===
            int extraBroken = 0;
            for (PendingBreak block : toBreak) {
                if (!world.isAlive()) break;

                try {
                    world.setBlock(block.x, block.y, block.z, "Empty");
                } catch (Exception e) {
                    LOGGER.atFine().log("Failed to break block at %d,%d,%d: %s", block.x, block.y, block.z, e.getMessage());
                    continue;
                }
                extraBroken++;
                giveDrops(store, block);
            }

            LOGGER.atFine().log("[PrismaPickaxe] 3x3 complete: %d extra blocks broken at %d,%d,%d", extraBroken, cx, cy, cz);

            // Reduce durability for extra blocks broken
            if (extraBroken > 0) {
                player = store.getComponent(playerRef, Player.getComponentType());
                if (player != null) {
                    updateDurability(player, heldItem, extraBroken);
                }
            }
        } finally {
            PROCESSING.set(Boolean.FALSE);
        }
    }

    /**
     * Drop items at the block's world position, exactly like vanilla block breaking.
     * Items spawn as physical entities that players pick up naturally.
     */
    private void giveDrops(Store<EntityStore> store, PendingBreak block) {
        Vector3d dropPos = new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
        try {
            List<ItemStack> drops;
            if (block.dropItemId != null && !block.dropItemId.isEmpty()) {
                drops = List.of(new ItemStack(block.dropItemId, block.dropQuantity));
            } else if (block.dropListId != null && !block.dropListId.isEmpty()) {
                drops = ItemModule.get().getRandomItemDrops(block.dropListId);
            } else {
                return;
            }
            Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(store, drops, dropPos, Vector3f.ZERO);
            store.addEntities(holders, AddReason.SPAWN);
        } catch (Exception e) {
            LOGGER.atFine().log("Failed to drop items at %d,%d,%d: %s", block.x, block.y, block.z, e.getMessage());
        }
    }

    private void updateDurability(Player player, ItemStack heldItem, int extraBroken) {
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

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
