package endgame.plugin.utils;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.BlockAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.builtin.adventure.farming.states.TilledSoilBlock;

import javax.annotation.Nullable;

/**
 * Utility methods for Frostwalker ice block placement, fluid manipulation,
 * and Pocket Garden farming detection.
 */
public final class AccessoryBlockUtils {

    private AccessoryBlockUtils() {}

    @Nullable
    public static Fluid getFluid(World world, int x, int y, int z) {
        ChunkStore chunkStore = world.getChunkStore();
        int chunkX = ChunkUtil.chunkCoordinate(x);
        int chunkY = ChunkUtil.chunkCoordinate(y);
        int chunkZ = ChunkUtil.chunkCoordinate(z);
        Ref<ChunkStore> section = chunkStore.getChunkSectionReference(chunkX, chunkY, chunkZ);
        if (section == null || !section.isValid()) return null;

        Store<ChunkStore> sectionStore = section.getStore();
        FluidSection fluidSection = sectionStore.getComponent(section, FluidSection.getComponentType());
        if (fluidSection == null) return null;

        int index = ChunkUtil.indexBlock(x, y, z);
        return fluidSection.getFluid(index);
    }

    public static boolean setFluid(World world, int x, int y, int z, String fluidId, int level) {
        ChunkStore chunkStore = world.getChunkStore();
        int chunkX = ChunkUtil.chunkCoordinate(x);
        int chunkY = ChunkUtil.chunkCoordinate(y);
        int chunkZ = ChunkUtil.chunkCoordinate(z);
        Ref<ChunkStore> section = chunkStore.getChunkSectionReference(chunkX, chunkY, chunkZ);
        if (section == null || !section.isValid()) return false;

        Store<ChunkStore> sectionStore = section.getStore();
        FluidSection fluidSection = sectionStore.getComponent(section, FluidSection.getComponentType());
        if (fluidSection == null) return false;

        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        if (fluid == null) return false;

        if (level > fluid.getMaxFluidLevel()) {
            level = fluid.getMaxFluidLevel();
        }

        int index = ChunkUtil.indexBlock(x, y, z);
        fluidSection.setFluid(index, fluid, (byte) level);

        ChunkSection chunkSection = sectionStore.getComponent(section, ChunkSection.getComponentType());
        if (chunkSection != null) {
            Ref<ChunkStore> columnRef = chunkSection.getChunkColumnReference();
            WorldChunk worldChunk = sectionStore.getComponent(columnRef, WorldChunk.getComponentType());
            if (worldChunk != null) {
                worldChunk.markNeedsSaving();
                worldChunk.setTicking(x, y, z, true);
                return true;
            }
        }
        return false;
    }

    public static void placeTickingBlock(World world, String blockTypeKey, int x, int y, int z) {
        int localX = x & 31;
        int localZ = z & 31;
        BlockAccessor accessor = world.getChunk(ChunkUtil.indexChunkFromBlock(x, z));
        if (accessor != null) {
            accessor.setBlock(localX, y, localZ, blockTypeKey);
            accessor.setTicking(localX, y, localZ, true);
        }
    }

    /**
     * Check if there are any TilledSoilBlock (planted) blocks within the given radius
     * at the same Y level or Y-1 (farm plots are usually at foot level or below).
     */
    public static boolean hasFarmingBlockNearby(World world, int px, int py, int pz, int radius) {
        ChunkStore chunkStore = world.getChunkStore();
        for (int dy = -1; dy <= 0; dy++) {
            int y = py + dy;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    TilledSoilBlock soil = getTilledSoilBlock(chunkStore, x, y, z);
                    if (soil != null && soil.isPlanted()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Auto-fertilize all TilledSoilBlock blocks within the given radius at Y and Y-1 levels.
     * Returns the number of blocks fertilized.
     */
    public static int fertilizeNearby(World world, int px, int py, int pz, int radius) {
        ChunkStore chunkStore = world.getChunkStore();
        int count = 0;
        for (int dy = -1; dy <= 0; dy++) {
            int y = py + dy;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int x = px + dx;
                    int z = pz + dz;
                    TilledSoilBlock soil = getTilledSoilBlock(chunkStore, x, y, z);
                    if (soil != null && !soil.isFertilized()) {
                        soil.setFertilized(true);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    @Nullable
    private static TilledSoilBlock getTilledSoilBlock(ChunkStore chunkStore, int x, int y, int z) {
        try {
            int chunkX = ChunkUtil.chunkCoordinate(x);
            int chunkY = ChunkUtil.chunkCoordinate(y);
            int chunkZ = ChunkUtil.chunkCoordinate(z);
            Ref<ChunkStore> section = chunkStore.getChunkSectionReference(chunkX, chunkY, chunkZ);
            if (section == null || !section.isValid()) return null;

            Store<ChunkStore> sectionStore = section.getStore();
            ChunkSection chunkSection = sectionStore.getComponent(section, ChunkSection.getComponentType());
            if (chunkSection == null) return null;

            Ref<ChunkStore> columnRef = chunkSection.getChunkColumnReference();
            if (columnRef == null || !columnRef.isValid()) return null;

            BlockComponentChunk blockComponents = sectionStore.getComponent(
                    columnRef, BlockComponentChunk.getComponentType());
            if (blockComponents == null) return null;

            int blockIndex = ChunkUtil.indexBlockInColumn(x, y, z);
            Ref<ChunkStore> blockRef = blockComponents.getEntityReference(blockIndex);
            if (blockRef == null || !blockRef.isValid()) return null;

            return sectionStore.getComponent(blockRef, TilledSoilBlock.getComponentType());
        } catch (Exception e) {
            return null;
        }
    }
}
