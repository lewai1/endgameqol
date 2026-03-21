package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;

/**
 * System to clear boss bar when player dies.
 */
public class PlayerDeathBossBarSystem extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;

    public PlayerDeathBossBarSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        // Find the PlayerRef for this entity
        Player playerComponent = store.getComponent(ref, Player.getComponentType());
        if (playerComponent == null) return;

        // Find matching PlayerRef by entity reference
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef != null && pRef.getReference() != null && pRef.getReference().equals(ref)) {
                // Clear the boss bar for this player (Golem Void)
                if (plugin.getGolemVoidBossManager() != null) {
                    plugin.getGolemVoidBossManager().hideBossBarForPlayer(pRef);
                }
                // Clear generic boss bars (Frost Dragon, Hedera)
                if (plugin.getGenericBossManager() != null) {
                    plugin.getGenericBossManager().hideBossBarForPlayer(pRef);
                }
                // Clear hysteresis state so boss bar can re-show after respawn
                if (plugin.getDangerZoneTickSystem() != null) {
                    plugin.getDangerZoneTickSystem().clearPlayerState(endgame.plugin.utils.EntityUtils.getUuid(pRef));
                }
                // Reset combo on death
                if (plugin.getComboMeterManager() != null) {
                    plugin.getComboMeterManager().onPlayerDeath(endgame.plugin.utils.EntityUtils.getUuid(pRef));
                }
                plugin.getLogger().atFine().log("[PlayerDeathBossBar] Cleared boss bars for dead player: %s",
                        playerComponent.getDisplayName());
                break;
            }
        }
    }
}
