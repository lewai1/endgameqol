package endgame.plugin.systems.boss;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PoisonComponent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies Poison effect to players hit by Endgame_Hedera or its projectiles.
 * Runs in the InspectDamageGroup to apply effects after damage is calculated.
 */
public class HederaPoisonSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(
            Player.getComponentType(),
            EffectControllerComponent.getComponentType());

    private static final float POISON_TICK_INTERVAL = 1.0f;
    private static final String POISON_EFFECT_ID = "Poison";

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, PoisonComponent> poisonComponentType;

    public HederaPoisonSystem(EndgameQoL plugin, ComponentType<EntityStore, PoisonComponent> poisonComponentType) {
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

        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }

        if (!isHederaDamage(sourceRef, commandBuffer)) {
            return;
        }

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Use commandBuffer.run() to defer check-then-add to buffer consumption time.
        float poisonDmg = plugin.getConfig().get().weapons().getHederaBossPoisonDamage();
        int poisonTicks = plugin.getConfig().get().weapons().getHederaBossPoisonTicks();
        commandBuffer.run(s -> {
            PoisonComponent existingPoison = s.getComponent(targetRef, poisonComponentType);
            if (existingPoison != null) {
                existingPoison.refresh(poisonDmg, POISON_TICK_INTERVAL, poisonTicks,
                        PoisonComponent.PoisonSource.BOSS);
                plugin.getLogger().atFine().log("[HederaPoisonSystem] Refreshed poison effect on player");
            } else {
                PoisonComponent newPoison = new PoisonComponent(poisonDmg, POISON_TICK_INTERVAL, poisonTicks);
                s.addComponent(targetRef, poisonComponentType, newPoison);
                plugin.getLogger().atFine().log(
                        "[HederaPoisonSystem] Applied poison: %.1f dmg/tick for %d ticks", poisonDmg, poisonTicks);
            }
        });

        // Apply vanilla Poison status effect for visual feedback (green tint + icon)
        applyPoisonVFX(targetRef, targetRef.getStore());
    }

    /**
     * Apply vanilla Poison status effect for visual feedback.
     */
    private void applyPoisonVFX(Ref<EntityStore> targetRef, Store<EntityStore> store) {
        EffectControllerComponent effectController = targetRef.getStore().getComponent(
                targetRef, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            plugin.getLogger().atWarning().log("[HederaPoisonSystem] Player has no EffectControllerComponent for VFX");
            return;
        }

        EntityEffect poisonEffect = EntityEffect.getAssetMap().getAsset(POISON_EFFECT_ID);
        if (poisonEffect == null) {
            plugin.getLogger().atWarning().log("[HederaPoisonSystem] Poison effect asset not found!");
            return;
        }

        float vfxDuration = plugin.getConfig().get().weapons().getHederaBossPoisonTicks() * POISON_TICK_INTERVAL;
        boolean applied = effectController.addEffect(
                targetRef,
                poisonEffect,
                vfxDuration,
                OverlapBehavior.OVERWRITE,
                store);

        if (applied) {
            plugin.getLogger().atFine().log("[HederaPoisonSystem] Applied Poison VFX for %.1f seconds", vfxDuration);
        }
    }

    private boolean isHederaDamage(Ref<EntityStore> sourceRef, CommandBuffer<EntityStore> commandBuffer) {
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) {
            return false;
        }

        NPCEntity npcEntity = sourceRef.getStore().getComponent(sourceRef, npcType);
        if (npcEntity != null) {
            String npcTypeId = npcEntity.getNPCTypeId();
            if (npcTypeId != null) {
                String lowerType = npcTypeId.toLowerCase();
                if (lowerType.contains("endgame_hedera")) {
                    plugin.getLogger().atFine().log("[HederaPoisonSystem] Hedera damage detected: %s", npcTypeId);
                    return true;
                }
            }
        }

        return false;
    }
}
