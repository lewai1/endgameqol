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
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Applies Burn effect to players hit by Fire Dragon's fire projectiles.
 * Runs in the InspectDamageGroup to apply effects after damage is calculated.
 */
public class FireBurnSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(
            Player.getComponentType(),
            EffectControllerComponent.getComponentType()
    );

    private static final String BURN_EFFECT_ID = "Burn";
    private static final float BURN_DURATION = 5.0f;
    private static volatile EntityEffect cachedBurnEffect;

    private final EndgameQoL plugin;

    public FireBurnSystem(EndgameQoL plugin) {
        this.plugin = plugin;
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
            @Nonnull Damage damage
    ) {
        // Only process if damage source is an entity
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) {
            return;
        }

        Ref<EntityStore> sourceRef = entitySource.getRef();
        if (sourceRef == null || !sourceRef.isValid()) {
            return;
        }

        // Check if the source is a Fire Dragon
        if (!isFireDragonDamage(sourceRef, commandBuffer)) {
            return;
        }

        // Get the target player's ref
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Get the EffectControllerComponent of the target
        EffectControllerComponent effectController = store.getComponent(
                targetRef, EffectControllerComponent.getComponentType()
        );
        if (effectController == null) {
            plugin.getLogger().atWarning().log("[FireBurnSystem] Player has no EffectControllerComponent");
            return;
        }

        // Get the Burn effect
        EntityEffect burnEffect = cachedBurnEffect;
        if (burnEffect == null) {
            burnEffect = EntityEffect.getAssetMap().getAsset(BURN_EFFECT_ID);
            cachedBurnEffect = burnEffect;
        }
        if (burnEffect == null) {
            plugin.getLogger().atWarning().log("[FireBurnSystem] Burn effect not found!");
            return;
        }

        // Apply the burn effect to the player
        boolean applied = effectController.addEffect(
                targetRef,
                burnEffect,
                BURN_DURATION,
                OverlapBehavior.OVERWRITE,
                store
        );

        if (applied) {
            plugin.getLogger().atFine().log("[FireBurnSystem] Applied Burn effect to player for %.1f seconds", BURN_DURATION);
        } else {
            plugin.getLogger().atFine().log("[FireBurnSystem] Burn effect not applied (already active or overlap)");
        }
    }

    private boolean isFireDragonDamage(Ref<EntityStore> sourceRef, CommandBuffer<EntityStore> commandBuffer) {
        // Check if source is an NPC
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) {
            return false;
        }

        NPCEntity npcEntity = sourceRef.getStore().getComponent(sourceRef, npcType);
        if (npcEntity != null) {
            String npcTypeId = npcEntity.getNPCTypeId();
            if (npcTypeId != null) {
                String lowerType = npcTypeId.toLowerCase();
                if (lowerType.contains("dragon_fire") || lowerType.contains("fire_dragon")) {
                    plugin.getLogger().atFine().log("[FireBurnSystem] Fire Dragon damage detected: %s", npcTypeId);
                    return true;
                }
            }
        }

        return false;
    }
}
