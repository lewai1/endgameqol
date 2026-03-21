package endgame.plugin.systems.trial;

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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.managers.GauntletManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * FILTER group: applies wave scaling + buff damage multipliers for Gauntlet.
 * Also handles crit chance (buff).
 */
public class GauntletDamageBoostSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(NPCEntity.getComponentType());

    private final EndgameQoL plugin;
    private final GauntletManager gauntletManager;

    public GauntletDamageBoostSystem(EndgameQoL plugin, GauntletManager gauntletManager) {
        this.plugin = plugin;
        this.gauntletManager = gauntletManager;
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

        if (!(damage.getSource() instanceof Damage.EntitySource es)) return;

        Ref<EntityStore> attackerRef = es.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player player = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        if (player == null) return;

        UUID uuid = findPlayerUuid(attackerRef);
        if (uuid == null || !gauntletManager.hasActiveGauntlet(uuid)) return;

        // Advance hit counter for Unstable Power (once per damage event)
        gauntletManager.onPlayerHit(uuid);
        // Wave scaling + player buff multiplier + wave modifier
        float waveMult = gauntletManager.getWaveDamageMultiplier(uuid);
        float playerMult = gauntletManager.getPlayerDamageMultiplier(uuid);
        float modifierDefense = gauntletManager.getModifierEnemyDefenseMult(uuid);
        float totalMult = waveMult * playerMult * modifierDefense;

        if (Math.abs(totalMult - 1.0f) < 0.001f) return;

        float original = damage.getAmount();
        damage.setAmount(original * totalMult);

        plugin.getLogger().atFine().log("[GauntletBoost] %s damage: %.1f -> %.1f (mult=%.2f)",
                uuid, original, original * totalMult, totalMult);
    }

    private UUID findPlayerUuid(Ref<EntityStore> playerEntityRef) {
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef == null) continue;
            Ref<EntityStore> ref = pRef.getReference();
            if (ref != null && ref.equals(playerEntityRef)) {
                return pRef.getUuid();
            }
        }
        return null;
    }
}
