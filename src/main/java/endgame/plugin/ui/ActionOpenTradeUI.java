package endgame.plugin.ui;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.role.Role;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import javax.annotation.Nonnull;

/**
 * NPC Action that opens the EndgameQoL custom trade UI for the interacting player.
 */
public class ActionOpenTradeUI extends ActionBase {

    @Nonnull
    private final String shopId;
    private final String merchantName;

    public ActionOpenTradeUI(@Nonnull BuilderActionOpenTradeUI builder, @Nonnull BuilderSupport support) {
        super(builder);
        this.shopId = builder.getShopId(support);
        this.merchantName = builder.getMerchantName();
    }

    @Override
    public boolean canExecute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                               InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        return super.canExecute(ref, role, sensorInfo, dt, store)
                && role.getStateSupport().getInteractionIterationTarget() != null;
    }

    @Override
    public boolean execute(@Nonnull Ref<EntityStore> ref, @Nonnull Role role,
                            InfoProvider sensorInfo, double dt, @Nonnull Store<EntityStore> store) {
        super.execute(ref, role, sensorInfo, dt, store);

        Ref<EntityStore> playerRef = role.getStateSupport().getInteractionIterationTarget();
        if (playerRef == null) return false;

        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) return false;

        Player player = store.getComponent(playerRef, Player.getComponentType());
        if (player == null) return false;

        String name = merchantName != null ? merchantName : shopId;
        player.getPageManager().openCustomPage(playerRef, store,
                new TradeUI(playerRefComponent, shopId, name));

        return true;
    }
}
