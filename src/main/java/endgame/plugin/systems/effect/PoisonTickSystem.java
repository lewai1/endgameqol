package endgame.plugin.systems.effect;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PoisonComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * System that processes poison damage over time.
 * Entities with PoisonComponent take periodic damage until the effect expires.
 */
public class PoisonTickSystem extends EntityTickingSystem<EntityStore> {

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, PoisonComponent> poisonComponentType;

    public PoisonTickSystem(EndgameQoL plugin, ComponentType<EntityStore, PoisonComponent> poisonComponentType) {
        this.plugin = plugin;
        this.poisonComponentType = poisonComponentType;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        PoisonComponent poison = archetypeChunk.getComponent(index, poisonComponentType);
        if (poison == null) {
            return;
        }

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) {
            return;
        }

        poison.addElapsedTime(dt);

        if (poison.getElapsedTime() >= poison.getTickInterval()) {
            poison.resetElapsedTime();

            // Cache config once per damage tick
            endgame.plugin.config.EndgameConfig config = plugin.getConfig().get();

            // Only apply Hedera damage multiplier for boss-sourced poison
            float damageMultiplier = poison.getSource() == PoisonComponent.PoisonSource.BOSS
                    ? config.getEffectiveBossDamageMultiplier(endgame.plugin.utils.BossType.HEDERA) : 1.0f;
            float scaledDamage = poison.getDamagePerTick() * damageMultiplier;

            @SuppressWarnings("deprecation")
            Damage damage = new Damage(Damage.NULL_SOURCE, DamageCause.OUT_OF_WORLD, scaledDamage);
            DamageSystems.executeDamage(ref, commandBuffer, damage);

            poison.decrementRemainingTicks();

            plugin.getLogger().atFine().log("[PoisonTickSystem] Dealt %.1f poison damage (base=%.1f x%.1f), %d ticks remaining",
                    scaledDamage, poison.getDamagePerTick(), damageMultiplier, poison.getRemainingTicks());
        }

        if (poison.isExpired()) {
            commandBuffer.removeComponent(ref, poisonComponentType);
            plugin.getLogger().atFine().log("[PoisonTickSystem] Poison effect expired and removed");
        }
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getGatherDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(this.poisonComponentType);
    }
}
