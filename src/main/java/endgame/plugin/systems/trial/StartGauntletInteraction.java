package endgame.plugin.systems.trial;

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
import endgame.plugin.managers.GauntletManager;
import endgame.plugin.utils.I18n;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Custom interaction type that starts The Gauntlet.
 * Triggered by using a Gauntlet Sigil item (Primary interaction).
 * Consumes 1 item from the held slot on successful activation.
 *
 * Used in JSON as: { "Type": "StartGauntlet" }
 */
public class StartGauntletInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<StartGauntletInteraction> CODEC = BuilderCodec.builder(
            StartGauntletInteraction.class, StartGauntletInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        EndgameQoL plugin = EndgameQoL.getInstance();
        if (plugin == null) return;
        if (!plugin.getConfig().get().isGauntletEnabled()) return;

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

        GauntletManager manager = plugin.getGauntletManager();
        if (manager == null) return;

        if (manager.hasActiveGauntlet(playerUuid)) {
            playerRef.sendMessage(Message.raw("[The Gauntlet] " + I18n.getForPlayer(playerRef, "gauntlet.already_active")).color("#ff5555"));
            return;
        }

        // Also check Warden Trial conflict
        if (plugin.getWardenTrialManager() != null && plugin.getWardenTrialManager().hasActiveTrial(playerUuid)) {
            playerRef.sendMessage(Message.raw("[The Gauntlet] " + I18n.getForPlayer(playerRef, "gauntlet.conflict_warden")).color("#ff5555"));
            return;
        }

        // Consume 1 item
        ItemContainer heldContainer = context.getHeldItemContainer();
        byte heldSlot = context.getHeldItemSlot();
        if (heldContainer != null) {
            heldContainer.removeItemStackFromSlot(heldSlot, 1);
        }

        manager.startGauntlet(playerUuid, playerRef, ref, transform.getPosition(), store);
    }
}
