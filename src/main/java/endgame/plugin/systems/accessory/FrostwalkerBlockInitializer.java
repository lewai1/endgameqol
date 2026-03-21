package endgame.plugin.systems.accessory;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule.BlockStateInfo;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import endgame.plugin.components.FrostwalkerIceComponent;

import javax.annotation.Nonnull;

/**
 * ChunkStore RefSystem that enables ticking on newly-placed Frostwalker ice blocks.
 */
public class FrostwalkerBlockInitializer extends RefSystem<ChunkStore> {

    @Override
    public void onEntityAdded(@Nonnull Ref<ChunkStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
        BlockStateInfo info = commandBuffer.getComponent(ref, BlockStateInfo.getComponentType());
        if (info == null) return;

        FrostwalkerIceComponent ice = commandBuffer.getComponent(ref, FrostwalkerIceComponent.getComponentType());
        if (ice == null) return;

        int x = ChunkUtil.xFromBlockInColumn(info.getIndex());
        int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
        int z = ChunkUtil.zFromBlockInColumn(info.getIndex());

        WorldChunk worldChunk = commandBuffer.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
        if (worldChunk != null) {
            worldChunk.setTicking(x, y, z, true);
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<ChunkStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<ChunkStore> store, @Nonnull CommandBuffer<ChunkStore> commandBuffer) {
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.and(BlockStateInfo.getComponentType(), FrostwalkerIceComponent.getComponentType());
    }
}
