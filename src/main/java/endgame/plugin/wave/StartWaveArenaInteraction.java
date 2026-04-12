package endgame.plugin.wave;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.I18n;
import endgame.wavearena.WaveArenaAPI;
import endgame.wavearena.WaveArenaConfig;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Custom interaction type "StartWaveArena" — triggered by items like Warden Challenge I-IV.
 * Reads ArenaId from JSON config, validates blacklist, starts the arena.
 *
 * JSON: { "Type": "StartWaveArena", "ArenaId": "Warden_Trial_I" }
 */
public class StartWaveArenaInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<StartWaveArenaInteraction> CODEC = BuilderCodec
            .builder(StartWaveArenaInteraction.class, StartWaveArenaInteraction::new,
                    SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("ArenaId", Codec.STRING),
                    StartWaveArenaInteraction::setArenaId,
                    StartWaveArenaInteraction::getArenaId).add()
            .build();

    private String arenaId;

    public String getArenaId() { return arenaId; }
    public void setArenaId(String arenaId) { this.arenaId = arenaId; }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        try {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin == null) return;
            if (arenaId == null || arenaId.isEmpty()) return;

            CommandBuffer<EntityStore> cb = context.getCommandBuffer();
            Ref<EntityStore> entityRef = context.getEntity();
            if (cb == null || entityRef == null) return;

            PlayerRef playerRef = cb.getComponent(entityRef, PlayerRef.getComponentType());
            if (playerRef == null) return;

            UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null) return;

            // Check if already in an arena
            if (WaveArenaAPI.isInArena(playerUuid)) {
                playerRef.sendMessage(Message.raw(
                        I18n.getForPlayer(playerRef, "wavearena.already_active")).color("#ff5555"));
                return;
            }

            // Check arena config exists
            WaveArenaConfig config = WaveArenaAPI.getArenaConfig(arenaId);
            if (config == null) {
                playerRef.sendMessage(Message.raw("[WaveArena] Unknown arena: " + arenaId).color("#ff5555"));
                return;
            }

            // Check instance blacklist
            TransformComponent tc = cb.getComponent(entityRef, TransformComponent.getComponentType());
            if (tc != null) {
                World world = entityRef.getStore().getExternalData().getWorld();
                if (world != null) {
                    String worldName = world.getName().toLowerCase();
                    for (String prefix : config.getInstanceBlacklist()) {
                        if (worldName.contains(prefix.toLowerCase())) {
                            playerRef.sendMessage(Message.raw(config.getBlockedMessage()).color("#ff5555"));
                            return;
                        }
                    }
                }
            }

            // Check if feature is enabled
            if (!plugin.getConfig().get().isWardenTrialEnabled()
                    && arenaId.startsWith("Warden_Trial")) {
                playerRef.sendMessage(Message.raw(
                        I18n.getForPlayer(playerRef, "wavearena.disabled")).color("#ff5555"));
                return;
            }

            // Get position
            TransformComponent transform = cb.getComponent(entityRef, TransformComponent.getComponentType());
            if (transform == null) return;
            Vector3d position = transform.getPosition();

            World world = entityRef.getStore().getExternalData().getWorld();
            if (world == null) return;

            // Consume the item (1 from stack)
            var heldItem = context.getHeldItem();
            if (heldItem != null && !heldItem.isEmpty()) {
                var container = context.getHeldItemContainer();
                byte slot = context.getHeldItemSlot();
                if (container != null) {
                    int newQty = heldItem.getQuantity() - 1;
                    if (newQty <= 0) {
                        container.setItemStackForSlot((short) slot, null);
                    } else {
                        container.setItemStackForSlot((short) slot, heldItem.withQuantity(newQty));
                    }
                }
            }

            // Start the arena
            WaveArenaAPI.startArena(playerUuid, playerRef, position, arenaId, world);

        } catch (Exception e) {
            EndgameQoL plugin = EndgameQoL.getInstance();
            if (plugin != null) {
                plugin.getLogger().atWarning().log("[StartWaveArena] Error: %s", e.getMessage());
            }
        }
    }
}
