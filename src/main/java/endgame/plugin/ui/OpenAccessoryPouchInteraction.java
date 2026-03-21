package endgame.plugin.ui;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;

/**
 * Custom interaction type that opens the Accessory Pouch container UI.
 * Triggered by right-clicking with the Accessory Pouch item (Secondary interaction).
 *
 * Registered in EndgameQoL.setup() via:
 *   getCodecRegistry(Interaction.CODEC).register("EndgameAccessoryPouch", ...)
 *
 * Used in JSON as: { "Type": "EndgameAccessoryPouch" }
 */
public class OpenAccessoryPouchInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<OpenAccessoryPouchInteraction> CODEC = BuilderCodec.builder(
            OpenAccessoryPouchInteraction.class, OpenAccessoryPouchInteraction::new, SimpleInstantInteraction.CODEC)
            .build();

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        EndgameQoL plugin = EndgameQoL.getInstance();
        if (plugin == null) return;
        if (!plugin.getConfig().get().isAccessoriesEnabled()) return;

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player player = context.getCommandBuffer().getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = context.getCommandBuffer().getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        AccessoryPouchUI.openPouch(plugin, player, playerRef, ref, store);
    }
}
