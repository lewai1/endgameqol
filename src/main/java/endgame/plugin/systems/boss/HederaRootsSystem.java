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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Applies Root effect to players hit by Endgame_Hedera with a chance.
 * Root immobilizes the player for a short duration.
 * Runs in the InspectDamageGroup to apply effects after damage is calculated.
 */
public class HederaRootsSystem extends DamageEventSystem {

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(
            Player.getComponentType(),
            EffectControllerComponent.getComponentType()
    );

    private static final float ROOT_CHANCE = 0.15f; // 15% chance to root on hit
    private static final float ROOT_DURATION = 3.0f; // 3 seconds immobilization
    private static final String ROOT_EFFECT_ID = "Root";

    private final EndgameQoL plugin;

    public HederaRootsSystem(EndgameQoL plugin) {
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

        // Check if the source is Hedera
        if (!isHederaDamage(sourceRef, commandBuffer)) {
            return;
        }

        // Roll for root chance
        if (ThreadLocalRandom.current().nextFloat() > ROOT_CHANCE) {
            return; // Didn't proc
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
            plugin.getLogger().atWarning().log("[HederaRootsSystem] Player has no EffectControllerComponent");
            return;
        }

        // Get the Root effect
        EntityEffect rootEffect = EntityEffect.getAssetMap().getAsset(ROOT_EFFECT_ID);
        if (rootEffect == null) {
            plugin.getLogger().atWarning().log("[HederaRootsSystem] Root effect not found!");
            return;
        }

        // Apply the root effect to the player
        boolean applied = effectController.addEffect(
                targetRef,
                rootEffect,
                ROOT_DURATION,
                OverlapBehavior.OVERWRITE,
                store
        );

        if (applied) {
            plugin.getLogger().atFine().log("[HederaRootsSystem] Applied Root effect to player for %.1f seconds (%.0f%% proc)",
                    ROOT_DURATION, ROOT_CHANCE * 100);
        } else {
            plugin.getLogger().atWarning().log("[HederaRootsSystem] Failed to apply Root effect");
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
                    plugin.getLogger().atFine().log("[HederaRootsSystem] Hedera damage detected: %s", npcTypeId);
                    return true;
                }
            }
        }

        return false;
    }
}
