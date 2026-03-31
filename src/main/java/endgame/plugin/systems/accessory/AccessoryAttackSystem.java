package endgame.plugin.systems.accessory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.BurnComponent;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.utils.AccessoryUtils;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Offensive accessory effects — runs in default Inspect group (after damage applied).
 *
 * - Blazefist: Apply burn to hit target + AOE burn to enemies within 3m
 *   Burn damage is configurable via BurnComponent (ticked by BurnTickSystem).
 *   Entity effect is used for visuals only (tint, particles, sounds).
 */
public class AccessoryAttackSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.AccessoryAttack");
    private static final String BURN_EFFECT_ID = "Endgame_Accessory_Burn";
    private static final double AOE_RADIUS = 3.0;

    private final EndgameQoL plugin;
    private final ComponentType<EntityStore, BurnComponent> burnComponentType;

    public AccessoryAttackSystem(EndgameQoL plugin,
                                 ComponentType<EntityStore, BurnComponent> burnComponentType) {
        this.plugin = plugin;
        this.burnComponentType = burnComponentType;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
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

        if (!plugin.getConfig().get().isAccessoriesEnabled()) return;

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;
        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        UUID attackerUuid = EntityUtils.getUuid(store, attackerRef);
        if (attackerUuid == null) return;

        // --- Blazefist: burn target + AOE burn to nearby enemies ---
        if (!AccessoryUtils.hasAccessory(plugin, attackerUuid, "Endgame_Accessory_Blazefist", store, attackerRef)) return;

        EndgameConfig config = plugin.getConfig().get();
        if (!config.isBlazefistBurnEnabled()) return;

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        float burnDamage = config.getBlazefistBurnDamage();
        int burnTicks = config.getBlazefistBurnTicks();
        float burnDuration = burnTicks; // 1 tick per second

        EntityEffect burnVisual = EntityEffect.getAssetMap().getAsset(BURN_EFFECT_ID);

        // Apply burn to direct target
        applyBurn(targetRef, burnVisual, burnDuration, burnDamage, burnTicks, store, commandBuffer);

        // AOE: apply burn to nearby enemies within 3m (skip players if PvP is off)
        TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
        if (targetTransform != null) {
            boolean pvpEnabled = store.getExternalData().getWorld().getWorldConfig().isPvpEnabled();
            Vector3d targetPos = targetTransform.getPosition();
            List<Ref<EntityStore>> nearby = TargetUtil.getAllEntitiesInSphere(targetPos, AOE_RADIUS, store);
            for (Ref<EntityStore> nearbyRef : nearby) {
                if (nearbyRef == null || !nearbyRef.isValid()) continue;
                if (nearbyRef.equals(attackerRef) || nearbyRef.equals(targetRef)) continue;

                // Skip players when PvP is disabled
                if (!pvpEnabled) {
                    Player nearbyPlayer = store.getComponent(nearbyRef, Player.getComponentType());
                    if (nearbyPlayer != null) continue;
                }

                applyBurn(nearbyRef, burnVisual, burnDuration, burnDamage, burnTicks, store, commandBuffer);
            }
        }

        LOGGER.atFine().log("[AccessoryAttack] Blazefist burn (%.0f x%d) for %s", burnDamage, burnTicks, attackerUuid);
    }

    private void applyBurn(Ref<EntityStore> ref, @Nullable EntityEffect burnVisual,
                           float visualDuration, float damagePerTick, int ticks,
                           Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        // Apply visual entity effect (tint, particles, sound) — no damage
        if (burnVisual != null) {
            EffectControllerComponent effectController = store.getComponent(
                    ref, EffectControllerComponent.getComponentType());
            if (effectController != null) {
                effectController.addEffect(ref, burnVisual, visualDuration,
                        OverlapBehavior.OVERWRITE, store);
            } else {
                commandBuffer.run(s -> {
                    if (!ref.isValid()) return;
                    EffectControllerComponent ec = s.ensureAndGetComponent(
                            ref, EffectControllerComponent.getComponentType());
                    if (ec != null) {
                        ec.addEffect(ref, burnVisual, visualDuration,
                                OverlapBehavior.OVERWRITE, s);
                    }
                });
            }
        }

        // Apply configurable BurnComponent for actual damage (ticked by BurnTickSystem)
        commandBuffer.run(s -> {
            if (!ref.isValid()) return;
            BurnComponent existing = s.getComponent(ref, burnComponentType);
            if (existing != null) {
                existing.refresh(damagePerTick, 1.0f, ticks);
            } else {
                s.addComponent(ref, burnComponentType, new BurnComponent(damagePerTick, 1.0f, ticks));
            }
        });
    }
}
