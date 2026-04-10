package endgame.plugin.systems.pet;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PetOwnerComponent;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.config.PetTier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * FILTER damage system that scales pet damage output by the pet's tier multiplier.
 * When a pet NPC deals damage, multiply it by the tier's damageMultiplier.
 *
 * Pattern: identical to ComboDamageBoostSystem.
 */
public class PetDamageScalingSystem extends DamageEventSystem {

    private final EndgameQoL plugin;

    public PetDamageScalingSystem(@Nonnull EndgameQoL plugin) {
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
        return Query.any();
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        if (!plugin.getConfig().get().pets().isEnabled()) return;

        if (!(damage.getSource() instanceof Damage.EntitySource es)) return;

        Ref<EntityStore> attackerRef = es.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Check if attacker is a pet
        PetOwnerComponent petComp = attackerRef.getStore().getComponent(
                attackerRef, PetOwnerComponent.getComponentType());
        if (petComp == null) return;

        UUID ownerUuid = petComp.getOwnerUuid();
        String petId = petComp.getPetId();
        if (ownerUuid == null || petId.isEmpty()) return;

        // Look up owner's pet tier
        PlayerEndgameComponent playerComp = plugin.getPlayerComponent(ownerUuid);
        if (playerComp == null) return;

        PetTier tier = playerComp.getPetData().getPetTier(petId);
        if (tier.getDamageMultiplier() <= 1.0f) return;

        float original = damage.getAmount();
        float modified = original * tier.getDamageMultiplier();
        damage.setAmount(modified);

        plugin.getLogger().atFine().log("[PetDmgScale] Pet %s (tier %s) damage: %.1f -> %.1f (x%.2f)",
                petId, tier.getLabel(), original, modified, tier.getDamageMultiplier());
    }
}
