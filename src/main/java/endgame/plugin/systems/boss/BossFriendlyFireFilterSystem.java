package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.utils.BossType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Blocks friendly fire from boss NPCs to allied minion NPCs.
 * Prevents Hedera Scream from damaging Spirit_Root, etc.
 * Runs in the FILTER damage group (before damage is applied).
 */
public class BossFriendlyFireFilterSystem extends AbstractBossDamageSystem {

    private static final Query<EntityStore> QUERY = Query.and(NPCEntity.getComponentType());

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        // Resolve attacker
        Ref<EntityStore> sourceRef = resolveAttacker(damage);
        if (sourceRef == null) return;

        // Check if attacker is a boss
        String attackerTypeId = resolveNPCTypeId(sourceRef, store);
        if (attackerTypeId == null) return;
        if (BossType.fromTypeId(attackerTypeId) == null) return;

        // Attacker is a boss, target is an NPC → block the damage
        damage.setAmount(0);
    }
}
