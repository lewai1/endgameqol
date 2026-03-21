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
import endgame.plugin.components.BurnComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * System that processes burn (fire) damage over time.
 * Entities with BurnComponent take periodic fire damage until the effect expires.
 */
public class BurnTickSystem extends EntityTickingSystem<EntityStore> {

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, BurnComponent> burnComponentType;
    private static volatile DamageCause cachedFireCause;

    public BurnTickSystem(EndgameQoL plugin, ComponentType<EntityStore, BurnComponent> burnComponentType) {
        this.plugin = plugin;
        this.burnComponentType = burnComponentType;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        BurnComponent burn = archetypeChunk.getComponent(index, burnComponentType);
        if (burn == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (ref == null || !ref.isValid()) return;

        burn.addElapsedTime(dt);

        if (burn.getElapsedTime() >= burn.getTickInterval()) {
            burn.resetElapsedTime();

            DamageCause fireCause = cachedFireCause;
            if (fireCause == null) {
                fireCause = DamageCause.getAssetMap().getAsset("Fire");
                cachedFireCause = fireCause;
            }
            @SuppressWarnings("deprecation")
            Damage damage = new Damage(Damage.NULL_SOURCE, fireCause != null ? fireCause : DamageCause.OUT_OF_WORLD, burn.getDamagePerTick());
            DamageSystems.executeDamage(ref, commandBuffer, damage);

            burn.decrementRemainingTicks();

            plugin.getLogger().atFine().log("[BurnTickSystem] Dealt %.1f fire damage, %d ticks remaining",
                    burn.getDamagePerTick(), burn.getRemainingTicks());
        }

        if (burn.isExpired()) {
            commandBuffer.removeComponent(ref, burnComponentType);
            plugin.getLogger().atFine().log("[BurnTickSystem] Burn effect expired and removed");
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
        return Query.and(this.burnComponentType);
    }
}
