package endgame.plugin.systems.block;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;

/**
 * Custom interaction type that unlocks a locked block (door) when the player
 * holds the correct key item. Replaces the block with an unlocked variant
 * and consumes the key.
 *
 * Originally from MajorDungeons mod — reimplemented natively so EndgameQoL
 * does not depend on that mod for Swamp Dungeon locked doors.
 *
 * Registered in EndgameQoL.setup() as "Unlock".
 *
 * JSON usage:
 * {
 *   "Type": "Unlock",
 *   "KeyItemId": "Hedera_Key",
 *   "UnlockDelayInSeconds": 1.2,
 *   "UnlockedBlockId": "Swamp_Dungeon_Door_Unlocked",
 *   "UnlockSfxId": "SFX_Door_Temple_Light_Open"
 * }
 */
public class UnlockInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.Unlock");

    private String keyItemId = "Ingredient_Stick";
    private String unlockedBlockId = "Furniture_Village_Door";
    private float unlockDelayInSeconds = 0.0F;
    private String unlockSfxId = "SFX_Door_Temple_Light_Open";
    private String lockedSfxId = "SFX_Door_Temple_Light_Close";

    public static final BuilderCodec<UnlockInteraction> CODEC = BuilderCodec.builder(
            UnlockInteraction.class, UnlockInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("KeyItemId", Codec.STRING), (i, v) -> { if (v != null) i.keyItemId = v; }, i -> i.keyItemId).add()
            .append(new KeyedCodec<>("UnlockedBlockId", Codec.STRING), (i, v) -> { if (v != null) i.unlockedBlockId = v; }, i -> i.unlockedBlockId).add()
            .append(new KeyedCodec<>("UnlockDelayInSeconds", Codec.FLOAT), (i, v) -> { if (v != null) i.unlockDelayInSeconds = v; }, i -> i.unlockDelayInSeconds).add()
            .append(new KeyedCodec<>("UnlockSfxId", Codec.STRING), (i, v) -> { if (v != null) i.unlockSfxId = v; }, i -> i.unlockSfxId).add()
            .append(new KeyedCodec<>("LockedSfxId", Codec.STRING), (i, v) -> { if (v != null) i.lockedSfxId = v; }, i -> i.lockedSfxId).add()
            .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        if (owningEntity == null) return;
        Store<EntityStore> store = owningEntity.getStore();

        Player player = store.getComponent(owningEntity, Player.getComponentType());
        if (player == null) return;

        World world = player.getWorld();
        BlockPosition targetBlock = context.getTargetBlock();
        if (world == null || targetBlock == null) return;

        String heldItemId = getHeldItemId(context);
        if (heldItemId.equalsIgnoreCase(keyItemId)) {
            handleUnlock(player, world, targetBlock, context, store);
        } else {
            handleLockedAttempt(world, targetBlock, store, owningEntity);
        }

        finishInteraction(context);
    }

    private void handleUnlock(Player player, World world, BlockPosition targetBlock,
                              InteractionContext context, Store<EntityStore> store) {
        Ref<EntityStore> owningEntity = context.getOwningEntity();
        int rotationIndex = world.getBlockRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
        BlockType newBlockType = BlockType.fromString(unlockedBlockId);

        // Play unlock sound at block center
        playCenteredSound(targetBlock, newBlockType, rotationIndex, store, unlockSfxId);

        // Consume 1 key item from the player's hotbar
        consumeItem(player, context);

        if (newBlockType == null) {
            LOGGER.atWarning().log("[Unlock] Unknown UnlockedBlockId: %s", unlockedBlockId);
            return;
        }

        // Notify player
        PlayerRef playerRef = store.getComponent(owningEntity, PlayerRef.getComponentType());
        if (playerRef != null) {
            playerRef.sendMessage(Message.raw("The door has been unlocked!").color("#4ade80"));
        }

        // Schedule delayed block replacement
        scheduleDelayedBlockChange(world, targetBlock, newBlockType);
    }

    private void handleLockedAttempt(World world, BlockPosition targetBlock,
                                     Store<EntityStore> store, Ref<EntityStore> owningEntity) {
        int rotationIndex = world.getBlockRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
        BlockType currentBlockType = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
        playCenteredSound(targetBlock, currentBlockType, rotationIndex, store, lockedSfxId);

        // Notify player they need the key
        PlayerRef playerRef = store.getComponent(owningEntity, PlayerRef.getComponentType());
        if (playerRef != null) {
            playerRef.sendMessage(
                    Message.raw("You need a " + keyItemId.replace("_", " ") + " to unlock this."));
        }
    }

    private void scheduleDelayedBlockChange(World world, BlockPosition pos, BlockType newBlockType) {
        long delayMillis = (long) (unlockDelayInSeconds * 1000.0F);
        int blockX = pos.x;
        int blockY = pos.y;
        int blockZ = pos.z;

        Runnable replaceBlock = () -> {
            if (!world.isAlive()) return;
            world.execute(() -> {
            try {
                Vector3i position = new Vector3i(blockX, blockY, blockZ);
                long chunkIndex = ChunkUtil.indexChunkFromBlock(position.x, position.z);
                WorldChunk chunk = world.getChunk(chunkIndex);
                if (chunk == null) {
                    LOGGER.atFine().log("[Unlock] Chunk not loaded at %d, %d", blockX, blockZ);
                    return;
                }

                int newBlockId = BlockType.getAssetMap().getIndex(newBlockType.getId());
                int rotation = world.getBlockRotationIndex(position.x, position.y, position.z);

                chunk.setBlock(position.x, position.y, position.z, newBlockId, newBlockType,
                        rotation, 0, 198);
                LOGGER.atFine().log("[Unlock] Replaced block at %d,%d,%d with %s",
                        blockX, blockY, blockZ, newBlockType.getId());
            } catch (Exception e) {
                LOGGER.atWarning().log("[Unlock] Failed to replace block: %s", e.getMessage());
            }
        });
        };

        if (delayMillis <= 0) {
            replaceBlock.run();
        } else {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(replaceBlock, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void playCenteredSound(BlockPosition pos, BlockType blockType, int rotationIndex,
                                   Store<EntityStore> store, String sfxId) {
        if (sfxId == null || sfxId.isEmpty()) return;
        int soundIndex = SoundEvent.getAssetMap().getIndex(sfxId);
        if (soundIndex == 0) {
            LOGGER.atFine().log("[Unlock] Sound not found: %s (may be from another mod)", sfxId);
            return;
        }
        if (blockType == null) {
            SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX,
                    new Vector3d(pos.x, pos.y, pos.z), store);
        } else {
            Vector3d localCenter = new Vector3d(0.0, 0.0, 0.0);
            blockType.getBlockCenter(rotationIndex, localCenter);
            Vector3d worldCenter = new Vector3d(
                    pos.x + localCenter.x, pos.y + localCenter.y, pos.z + localCenter.z);
            SoundUtil.playSoundEvent3d(soundIndex, SoundCategory.SFX, worldCenter, store);
        }
    }

    private String getHeldItemId(InteractionContext context) {
        ItemStack heldItem = context.getHeldItem();
        return heldItem != null ? heldItem.getItem().getId() : "Empty";
    }

    private void consumeItem(Player player, InteractionContext context) {
        player.getInventory().getHotbar()
                .removeItemStackFromSlot((short) context.getHeldItemSlot(), 1, true, false);
    }

    private void finishInteraction(InteractionContext context) {
        context.getState().state = InteractionState.Finished;
    }
}
