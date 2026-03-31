package endgame.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Custom interaction type that opens the EndgameQoL Trade UI.
 * Replaces vanilla OpenBarterShop in NPC role JSON.
 *
 * Usage in NPC role JSON:
 *   { "Type": "EndgameOpenTradeUI", "Shop": "Endgame_Korvyn", "MerchantName": "Korvyn" }
 */
public class OpenTradeUIInteraction extends SimpleInstantInteraction {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.TradeUI");

    public static final BuilderCodec<OpenTradeUIInteraction> CODEC = BuilderCodec
            .builder(OpenTradeUIInteraction.class, OpenTradeUIInteraction::new, SimpleInstantInteraction.CODEC)
            .append(new KeyedCodec<>("Shop", Codec.STRING),
                    (i, v) -> i.shopId = v, i -> i.shopId)
            .add()
            .append(new KeyedCodec<>("MerchantName", Codec.STRING),
                    (i, v) -> i.merchantName = v, i -> i.merchantName)
            .add()
            .build();

    private String shopId;
    private String merchantName;

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
                            @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        String name = merchantName != null ? merchantName : shopId;
        TradeUI.open(playerRef, store, shopId, name);

        LOGGER.atFine().log("[TradeUI] Opened %s via NPC interaction", shopId);
    }
}
