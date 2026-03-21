package endgame.plugin.systems.combat;

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
import endgame.plugin.managers.ComboMeterManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * FILTER group damage system that applies combo damage multipliers.
 * When a player with an active combo hits an NPC, damage is boosted.
 *
 * Query targets all entities (NPCs receiving damage), then checks if attacker is a player.
 */
public class ComboDamageBoostSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(NPCEntity.getComponentType());

    private final EndgameQoL plugin;
    private final ComboMeterManager comboManager;

    public ComboDamageBoostSystem(EndgameQoL plugin, ComboMeterManager comboManager) {
        this.plugin = plugin;
        this.comboManager = comboManager;
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

        if (!plugin.getConfig().get().isComboEnabled()) return;

        // Source must be a player
        if (!(damage.getSource() instanceof Damage.EntitySource es)) return;

        Ref<EntityStore> attackerRef = es.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        Player player = attackerRef.getStore().getComponent(attackerRef, Player.getComponentType());
        if (player == null) return;

        // Find player UUID
        UUID uuid = findPlayerUuid(attackerRef);
        if (uuid == null) return;

        float multiplier = comboManager.getDamageMultiplier(uuid);

        // C1: Precision — 20% crit chance at x4+ tier (1.5x multiplier)
        float critChance = comboManager.getCritChance(uuid);
        if (critChance > 0 && ThreadLocalRandom.current().nextFloat() < critChance) {
            multiplier *= 1.5f;
        }

        if (multiplier <= 1.0f) return;

        float original = damage.getAmount();
        float modified = original * multiplier;
        damage.setAmount(modified);

        plugin.getLogger().atFine().log("[ComboBoost] Player %s combo damage: %.1f -> %.1f (x%.2f)",
                uuid, original, modified, multiplier);
    }

    private UUID findPlayerUuid(Ref<EntityStore> playerEntityRef) {
        for (PlayerRef pRef : Universe.get().getPlayers()) {
            if (pRef == null) continue;
            Ref<EntityStore> ref = pRef.getReference();
            if (ref != null && ref.equals(playerEntityRef)) {
                return endgame.plugin.utils.EntityUtils.getUuid(pRef);
            }
        }
        return null;
    }
}
