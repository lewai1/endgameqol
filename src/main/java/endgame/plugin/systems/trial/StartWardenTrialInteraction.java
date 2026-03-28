package endgame.plugin.systems.trial;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.I18n;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Custom interaction type that starts a Warden Challenge wave encounter.
 * Triggered by using a Warden Challenge consumable item (Primary interaction).
 * Consumes 1 item from the held slot on successful activation.
 *
 * Registered in EndgameQoL.setup() via:
 *   getCodecRegistry(Interaction.CODEC).register("StartWardenTrial", ...)
 *
 * Used in JSON as: { "Type": "StartWardenTrial", "Tier": 1 }
 */
public class StartWardenTrialInteraction extends SimpleInstantInteraction {

    private int tier = 1;

    public static final BuilderCodec<StartWardenTrialInteraction> CODEC = BuilderCodec.builder(
            StartWardenTrialInteraction.class, StartWardenTrialInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<Integer>("Tier", Codec.INTEGER), (i, v) -> i.tier = v != null ? v : 1, i -> i.tier).add()
            .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        EndgameQoL plugin = EndgameQoL.getInstance();
        if (plugin == null) return;
        if (!plugin.getConfig().get().isWardenTrialEnabled()) return;

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) return;

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return;

        WardenTrialManager manager = plugin.getWardenTrialManager();
        if (manager == null) return;

        if (manager.hasActiveTrial(playerUuid)) {
            playerRef.sendMessage(Message.raw("[Warden Challenge] " + I18n.getForPlayer(playerRef, "warden.already_active")).color("#ff5555"));
            return;
        }

        // Consume 1 item from the held slot
        ItemContainer heldContainer = context.getHeldItemContainer();
        byte heldSlot = context.getHeldItemSlot();
        if (heldContainer != null) {
            heldContainer.removeItemStackFromSlot(heldSlot, 1);
        }

        // Start the challenge
        manager.startTrial(playerUuid, playerRef, ref, transform.getPosition(), store, tier);
    }
}
