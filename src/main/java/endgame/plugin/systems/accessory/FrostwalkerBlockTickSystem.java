package endgame.plugin.systems.accessory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktick.BlockTickStrategy;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.ChunkSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import endgame.plugin.components.FrostwalkerIceComponent;
import endgame.plugin.utils.AccessoryBlockUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ChunkStore ticking system that counts down Frostwalker ice blocks.
 * When the timer expires, the block is replaced with Empty and water is restored.
 */
public class FrostwalkerBlockTickSystem extends EntityTickingSystem<ChunkStore> {

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<ChunkStore> archetypeChunk,
                     @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        BlockSection blocks = archetypeChunk.getComponent(index, BlockSection.getComponentType());
        if (blocks == null || blocks.getTickingBlocksCountCopy() == 0) return;

        ChunkSection section = archetypeChunk.getComponent(index, ChunkSection.getComponentType());
        if (section == null) return;

        BlockComponentChunk blockComponentChunk = commandBuffer.getComponent(
                section.getChunkColumnReference(), BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) return;

        blocks.forEachTicking(blockComponentChunk, commandBuffer, section.getY(),
                (bcc, cb, localX, localY, localZ, blockId) -> {
                    Ref<ChunkStore> blockRef = bcc.getEntityReference(
                            ChunkUtil.indexBlockInColumn(localX, localY, localZ));
                    if (blockRef == null) return BlockTickStrategy.IGNORED;

                    FrostwalkerIceComponent ice = cb.getComponent(blockRef, FrostwalkerIceComponent.getComponentType());
                    if (ice == null) return BlockTickStrategy.IGNORED;

                    ice.decreaseTimer(dt);
                    if (ice.isExpired()) {
                        WorldChunk worldChunk = commandBuffer.getComponent(
                                section.getChunkColumnReference(), WorldChunk.getComponentType());
                        if (worldChunk != null) {
                            World world = worldChunk.getWorld();
                            if (world == null || !world.isAlive()) return BlockTickStrategy.CONTINUE;
                            int globalX = localX + worldChunk.getX() * 32;
                            int globalZ = localZ + worldChunk.getZ() * 32;
                            world.execute(() -> {
                                world.setBlock(globalX, localY, globalZ, "Empty");
                                AccessoryBlockUtils.setFluid(world, globalX, localY, globalZ, "Water_Source", 100);
                            });
                            ice.reset();
                        }
                    }
                    return BlockTickStrategy.CONTINUE;
                });
    }

    @Nullable
    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(BlockSection.getComponentType(), ChunkSection.getComponentType());
    }
}
