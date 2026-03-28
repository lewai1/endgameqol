package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PoisonComponent;
import endgame.plugin.config.EndgameConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies Poison and Life Steal effects when a player attacks with Hedera
 * Daggers.
 *
 * Effects:
 * - Poison: Applies poison damage over time to the target
 * - Life Steal: Heals the player for a percentage of damage dealt
 *
 * Both effects are configurable in EndgameConfig.
 */
public class HederaDaggerEffectSystem extends DamageEventSystem {

    private static final String HEDERA_DAGGER_ID = "Weapon_Daggers_Hedera";
    private static final String POISON_EFFECT_ID = "Poison";

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, PoisonComponent> poisonComponentType;

    public HederaDaggerEffectSystem(EndgameQoL plugin,
            ComponentType<EntityStore, PoisonComponent> poisonComponentType) {
        this.plugin = plugin;
        this.poisonComponentType = poisonComponentType;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getInspectDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return EntityStatMap.getComponentType();
    }

    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        EndgameConfig config = plugin.getConfig().get();

        if (!config.isEnableHederaDaggerPoison() && !config.isEnableHederaDaggerLifesteal()) {
            return;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        // Use attacker's own store — handle() store belongs to the target
        Store<EntityStore> attackerStore = attackerRef.getStore();

        // Verify attacker is a player with Hedera daggers equipped
        Player attacker = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) {
            return;
        }

        if (!isWieldingHederaDaggers(attackerStore, attackerRef)) {
            return;
        }

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Don't apply effects to the attacker themselves
        if (attackerRef.equals(targetRef)) {
            return;
        }

        float damageAmount = damage.getAmount();

        // Apply poison effect
        if (config.isEnableHederaDaggerPoison()) {
            applyPoison(targetRef, commandBuffer, config);
        }

        // Apply life steal effect
        if (config.isEnableHederaDaggerLifesteal() && damageAmount > 0) {
            applyLifesteal(attackerRef, store, damageAmount, config);
        }
    }

    /**
     * Check if the player is wielding Hedera Daggers.
     */
    private boolean isWieldingHederaDaggers(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return false;
        }

        // Check active hotbar item (main weapon)
        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem != null && !ItemStack.isEmpty(activeItem)) {
            Item item = activeItem.getItem();
            if (item != null && HEDERA_DAGGER_ID.equals(item.getId())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Apply poison effect to the target.
     */
    private void applyPoison(Ref<EntityStore> targetRef, CommandBuffer<EntityStore> commandBuffer,
            EndgameConfig config) {
        float poisonDamage = config.getHederaDaggerPoisonDamage();
        int poisonTicks = config.getHederaDaggerPoisonTicks();
        float tickInterval = 1.0f; // 1 second per tick

        // Use commandBuffer.run() to defer check-then-add to buffer consumption time.
        commandBuffer.run(s -> {
            PoisonComponent existingPoison = s.getComponent(targetRef, poisonComponentType);
            if (existingPoison != null) {
                existingPoison.refresh(poisonDamage, tickInterval, poisonTicks, PoisonComponent.PoisonSource.PLAYER);
                plugin.getLogger().atFine().log("[HederaDagger] Refreshed poison on target");
            } else {
                PoisonComponent newPoison = new PoisonComponent(poisonDamage, tickInterval, poisonTicks,
                        PoisonComponent.PoisonSource.PLAYER);
                s.addComponent(targetRef, poisonComponentType, newPoison);
                plugin.getLogger().atFine().log("[HederaDagger] Applied poison: %.1f dmg/tick for %d ticks",
                        poisonDamage, poisonTicks);
            }
        });

        // Apply visual poison effect
        applyPoisonVFX(targetRef, targetRef.getStore(), poisonTicks);
    }

    /**
     * Apply visual poison effect (green tint + icon).
     */
    private void applyPoisonVFX(Ref<EntityStore> targetRef, Store<EntityStore> store, int ticks) {
        EffectControllerComponent effectController = targetRef.getStore().getComponent(
                targetRef, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            return;
        }

        EntityEffect poisonEffect = EntityEffect.getAssetMap().getAsset(POISON_EFFECT_ID);
        if (poisonEffect == null) {
            plugin.getLogger().atWarning().log("[HederaDagger] Poison effect asset not found!");
            return;
        }

        float duration = ticks * 1.0f; // 1 second per tick
        effectController.addEffect(
                targetRef,
                poisonEffect,
                duration,
                OverlapBehavior.EXTEND,
                store);
    }

    /**
     * Apply life steal effect - heal the attacker based on damage dealt.
     */
    private void applyLifesteal(Ref<EntityStore> attackerRef, Store<EntityStore> store,
            float damageDealt, EndgameConfig config) {
        // Get attacker's entity stat map for health
        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType == null) {
            return;
        }

        EntityStatMap statMap = attackerRef.getStore().getComponent(attackerRef, statType);
        if (statMap == null) {
            return;
        }

        int healthStatIndex = DefaultEntityStatTypes.getHealth();
        EntityStatValue healthValue = statMap.get(healthStatIndex);
        if (healthValue == null) {
            return;
        }

        float lifestealPercent = config.getHederaDaggerLifestealPercent();
        float healAmount = damageDealt * lifestealPercent;

        if (healAmount > 0) {
            float currentHealth = healthValue.get();
            float maxHealth = healthValue.getMax();

            // Only heal if not already at max health
            if (currentHealth < maxHealth) {
                // Use addStatValue to heal (it will clamp to max automatically)
                statMap.addStatValue(healthStatIndex, healAmount);

                plugin.getLogger().atFine().log("[HederaDagger] Life steal: +%.1f HP (%.0f%% of %.1f damage)",
                        healAmount, lifestealPercent * 100, damageDealt);
            }
        }
    }
}
