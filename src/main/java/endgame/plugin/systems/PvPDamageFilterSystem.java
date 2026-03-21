package endgame.plugin.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Cancels player-on-player damage when PvP is disabled in the world config.
 * Runs in the FilterDamageGroup (before health loss).
 */
public class PvPDamageFilterSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(Player.getComponentType());

    private final EndgameQoL plugin;

    public PvPDamageFilterSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        // Check if attacker is a player — use attacker's own store
        Player attackerPlayer = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        if (attackerPlayer == null) {
            return;
        }

        // Both attacker and target are players — check PvP setting
        WorldConfig worldConfig = store.getExternalData().getWorld().getWorldConfig();
        if (!worldConfig.isPvpEnabled()) {
            damage.setCancelled(true);
            plugin.getLogger().atFine().log("[PvPFilter] Cancelled player-on-player damage (PvP disabled)");
        }
    }
}
