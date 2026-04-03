package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.knockback.KnockbackComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.protocol.AnimationSlot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;

/**
 * Suppresses knockback and hurt animation for boss NPCs.
 * Runs in the INSPECT damage group (after damage applied).
 * Pattern from MonsterExpansion mod (Saksolm).
 */
public class BossKnockbackSuppressionSystem extends DamageEventSystem {

    private static final Set<String> BOSS_ROLE_NAMES = Set.of(
            "Endgame_Dragon_Frost", "Dragon_Frost_Hybrid",
            "Endgame_Hedera",
            "Endgame_Golem_Void"
    );

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {
        if (damage.getAmount() <= 0.0f) return;

        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        String roleName = npc.getRole().getRoleName();
        if (!BOSS_ROLE_NAMES.contains(roleName)) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);

        // Suppress knockback
        commandBuffer.tryRemoveComponent(ref, KnockbackComponent.getComponentType());

        // Clear hurt animation (prevents stagger)
        AnimationUtils.playAnimation(ref, AnimationSlot.Status, null, true, commandBuffer);
    }
}
