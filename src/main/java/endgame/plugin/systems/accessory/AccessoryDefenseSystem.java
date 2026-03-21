package endgame.plugin.systems.accessory;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.AccessoryUtils;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Defensive accessory effects — runs in FilterDamageGroup (before damage applied).
 *
 * - Void Amulet: Survive lethal hit at 1 HP + 3s invulnerability (5min CD)
 * - Hedera Seed: 15% chance to root attacker for 2s when hit
 */
public class AccessoryDefenseSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.AccessoryDefense");

    private static final long VOID_AMULET_COOLDOWN_MS = 5 * 60 * 1000L; // 5 minutes
    private static final long VOID_AMULET_INVULN_MS = 3000L; // 3 seconds
    private static final String INVULN_EFFECT_ID = "Endgame_Accessory_Invuln";
    private static final String ROOT_EFFECT_ID = "Root";
    private static final float ROOT_DURATION = 2.0f;
    private static final float ROOT_CHANCE = 0.15f;
    private static volatile EntityEffect cachedInvulnEffect;
    private static volatile EntityEffect cachedRootEffect;

    @Nonnull
    private static final Query<EntityStore> QUERY = Query.and(Player.getComponentType());

    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, Long> voidAmuletCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> voidAmuletInvuln = new ConcurrentHashMap<>();

    public AccessoryDefenseSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSystemRegistered() {
        LOGGER.atInfo().log("[AccessoryDefense] System registered! Group=%s, Query=%s",
                getGroup(), getQuery());
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        if (!plugin.getConfig().get().isAccessoriesEnabled()) return;

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        UUID uuid = EntityUtils.getUuid(store, targetRef);
        if (uuid == null) return;

        long now = System.currentTimeMillis();

        // --- Void Amulet: invulnerability window blocks all damage ---
        if (AccessoryUtils.hasAccessory(plugin, uuid, "Endgame_Accessory_Void_Amulet", store, targetRef)) {
            Long invulnEnd = voidAmuletInvuln.get(uuid);
            if (invulnEnd != null && now < invulnEnd) {
                damage.setCancelled(true);
                LOGGER.atFine().log("[AccessoryDefense] Void Amulet invuln cancelled damage for %s", uuid);
                return;
            }

            // Lethal save: if this hit would kill the player
            EntityStatMap statMap = store.getComponent(targetRef, EntityStatMap.getComponentType());
            if (statMap != null) {
                int healthIndex = DefaultEntityStatTypes.getHealth();
                EntityStatValue health = statMap.get(healthIndex);
                if (health != null && damage.getAmount() >= health.get()) {
                    Long lastTrigger = voidAmuletCooldowns.get(uuid);
                    if (lastTrigger == null || now - lastTrigger >= VOID_AMULET_COOLDOWN_MS) {
                        // Reduce damage to leave player at 1 HP
                        float survivalDamage = health.get() - 1.0f;
                        if (survivalDamage < 0) survivalDamage = 0;
                        damage.setAmount(survivalDamage);

                        // Start invuln window + cooldown
                        voidAmuletCooldowns.put(uuid, now);
                        voidAmuletInvuln.put(uuid, now + VOID_AMULET_INVULN_MS);

                        // Apply visual effect
                        EffectControllerComponent effectController = store.getComponent(
                                targetRef, EffectControllerComponent.getComponentType());
                        if (effectController != null) {
                            EntityEffect invulnEffect = cachedInvulnEffect;
                            if (invulnEffect == null) {
                                invulnEffect = EntityEffect.getAssetMap().getAsset(INVULN_EFFECT_ID);
                                cachedInvulnEffect = invulnEffect;
                            }
                            if (invulnEffect != null) {
                                effectController.addEffect(targetRef, invulnEffect, 3.0f,
                                        OverlapBehavior.OVERWRITE, store);
                            }
                        }

                        LOGGER.atFine().log("[AccessoryDefense] Void Amulet lethal save for %s (%.1f HP -> 1 HP)",
                                uuid, health.get());
                        return;
                    }
                }
            }
        }

        // --- Hedera Seed: 15% chance to root attacker for 2s ---
        if (AccessoryUtils.hasAccessory(plugin, uuid, "Endgame_Accessory_Hedera_Seed", store, targetRef)) {
            if (damage.getSource() instanceof Damage.EntitySource entitySource) {
                Ref<EntityStore> attackerRef = entitySource.getRef();
                if (attackerRef != null && attackerRef.isValid()) {
                    float roll = ThreadLocalRandom.current().nextFloat();
                    if (roll < ROOT_CHANCE) {
                        EntityEffect rootEffect = cachedRootEffect;
                        if (rootEffect == null) {
                            rootEffect = EntityEffect.getAssetMap().getAsset(ROOT_EFFECT_ID);
                            cachedRootEffect = rootEffect;
                        }
                        if (rootEffect == null) {
                            LOGGER.atWarning().log("[AccessoryDefense] Hedera Seed: Root effect '%s' not found!", ROOT_EFFECT_ID);
                            return;
                        }
                        final EntityEffect finalRootEffect = rootEffect;
                        EffectControllerComponent attackerEffects = store.getComponent(
                                attackerRef, EffectControllerComponent.getComponentType());
                        if (attackerEffects == null) {
                            // NPC may not have EffectControllerComponent — add it via commandBuffer
                            commandBuffer.run(s -> {
                                EffectControllerComponent ec = s.ensureAndGetComponent(
                                        attackerRef, EffectControllerComponent.getComponentType());
                                if (ec != null) {
                                    ec.addEffect(attackerRef, finalRootEffect, ROOT_DURATION,
                                            OverlapBehavior.OVERWRITE, s);
                                    LOGGER.atFine().log("[AccessoryDefense] Hedera Seed rooted attacker (ensured component) for %s", uuid);
                                }
                            });
                        } else {
                            attackerEffects.addEffect(attackerRef, finalRootEffect, ROOT_DURATION,
                                    OverlapBehavior.OVERWRITE, store);
                            LOGGER.atFine().log("[AccessoryDefense] Hedera Seed rooted attacker for %s", uuid);
                        }
                    }
                }
            }
        }
    }

    public void forceClear() {
        voidAmuletCooldowns.clear();
        voidAmuletInvuln.clear();
    }
}
