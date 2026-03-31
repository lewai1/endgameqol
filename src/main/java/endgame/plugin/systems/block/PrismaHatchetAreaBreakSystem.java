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
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
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
 * Prisma Hatchet 3x3 area mining system.
 * When a block is broken with the Prisma Hatchet, surrounding blocks in a 3x3 area
 * (same Y-plane) are also broken if they match the hatchet's primary gather types.
 *
 * Uses a two-pass algorithm: scan all valid blocks first (read-only),
 * then break them all and give drops. This prevents world.setBlock() side effects
 * (tree collapse, block physics) from aborting the loop mid-way.
 */
public class PrismaHatchetAreaBreakSystem extends EntityEventSystem<EntityStore, com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.PrismaHatchet");
    private static final String PRISMA_HATCHET_ID = "Tool_Hatchet_Prisma";
    private static final Set<String> ALLOWED_GATHER_TYPES = Set.of("Woods", "SoftBlocks");
    private static final double DURABILITY_LOSS_PER_BLOCK = 0.1;

    /** Re-entrancy guard to prevent recursive triggers from secondary breaks. */
    private static final ThreadLocal<Boolean> PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public PrismaHatchetAreaBreakSystem() {
        super(com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent.class);
    }

    /** Block position + cached drop info, collected in the scan pass. */
    private record PendingBreak(int x, int y, int z, String dropItemId) {}

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent event) {

        if (event.isCancelled()) return;
        if (PROCESSING.get()) return;

        // Check held item is Prisma Hatchet in AreaBreak state
        ItemStack heldItem = event.getItemInHand();
        if (heldItem == null) return;
        String itemId = heldItem.getItemId();
        if (itemId == null || !itemId.contains("Hatchet_Prisma")) return;
        // Only do 3x3 in AreaBreak state (variant ID differs from base)
        if (PRISMA_HATCHET_ID.equals(itemId)) return;

        Vector3i center = event.getTargetBlock();
        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        World world = store.getExternalData().getWorld();
        if (world == null || !world.isAlive()) return;
        String worldName = world.getName();

        Ref<EntityStore> playerRef = archetypeChunk.getReferenceTo(index);
        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return;

        java.util.UUID playerUUID = endgame.plugin.utils.EntityUtils.getUuid(store, playerRef);
        var claimBridge = endgame.plugin.integration.ClaimProtectionBridge.get();

        PROCESSING.set(Boolean.TRUE);
        try {
            // === PASS 1: Scan all valid blocks (read-only, no world mutation) ===
            List<PendingBreak> toBreak = new ArrayList<>(8);

            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;

                    int bx = cx + dx;
                    int bz = cz + dz;

                    BlockType bt;
                    try {
                        bt = world.getBlockType(bx, cy, bz);
                    } catch (Exception e) {
                        continue;
                    }
                    if (bt == null || "Empty".equals(bt.getId())) continue;

                    BlockGathering gathering = bt.getGathering();
                    if (gathering == null) continue;

                    BlockBreakingDropType breaking = gathering.getBreaking();
                    if (breaking == null) continue;

                    String gatherType = breaking.getGatherType();
                    if (gatherType == null || !ALLOWED_GATHER_TYPES.contains(gatherType)) continue;

                    if (!claimBridge.isBreakAllowed(playerUUID, worldName, bx, bz)) {
                        LOGGER.atFine().log("[PrismaHatchet] Skipped %d,%d,%d (claim protected)", bx, cy, bz);
                        continue;
                    }

                    toBreak.add(new PendingBreak(bx, cy, bz, breaking.getItemId()));
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

                // Drop items at block position like vanilla block breaking
                if (block.dropItemId != null && !block.dropItemId.isEmpty()) {
                    try {
                        Vector3d dropPos = new Vector3d(block.x + 0.5, block.y, block.z + 0.5);
                        List<ItemStack> drops = List.of(new ItemStack(block.dropItemId, 1));
                        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(store, drops, dropPos, Vector3f.ZERO);
                        store.addEntities(holders, AddReason.SPAWN);
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to drop %s at %d,%d,%d: %s", block.dropItemId, block.x, block.y, block.z, e.getMessage());
                    }
                }
            }

            // Reduce durability for extra blocks broken
            if (extraBroken > 0) {
                player = store.getComponent(playerRef, Player.getComponentType());
                if (player != null) {
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
                                    && currentSlotItem.getItemId().contains("Hatchet_Prisma")) {
                                container.setItemStackForSlot((short) activeSlot, currentSlotItem.withDurability(newDurability));
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.atFine().log("Failed to update durability: %s", e.getMessage());
                    }
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
