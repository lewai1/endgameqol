package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.VoidMarkComponent;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.utils.TeleportSafety;
import endgame.plugin.utils.VoidMarkTracker;
import endgame.plugin.utils.WeaponUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DaggerVanishSystem - Teleports the player behind the target when using
 * Prisma Daggers signature attack (Razorstrike).
 *
 * Detection: Inspects the attacker's active InteractionChain via InteractionManager.
 * When a chain with "Signature" in its RootInteraction ID is active, the signature
 * attack is in progress. Triggers once per chain ID to avoid re-firing on multi-hit.
 *
 * Effects:
 * - Teleport 2 blocks behind the target (based on target's facing direction)
 * - Brief invulnerability (configurable, default 2s)
 * - Cooldown between vanishes (configurable, default 10s)
 */
public class DaggerVanishSystem extends DamageEventSystem {

    private static final String PRISMA_DAGGER_ID = "Endgame_Daggers_Prisma";
    private static final double TELEPORT_DISTANCE = 2.0;

    private final EndgameQoL plugin;

    // Track the last signature chain ID that triggered vanish, to avoid re-firing on multi-hit signatures
    private final ConcurrentHashMap<UUID, Integer> lastTriggeredChainId = new ConcurrentHashMap<>();
    // Cooldown tracking
    private final ConcurrentHashMap<UUID, Long> vanishCooldowns = new ConcurrentHashMap<>();
    // Invulnerability end times for cleanup
    private final ConcurrentHashMap<UUID, Long> vanishActiveUntil = new ConcurrentHashMap<>();

    // BlinkTrailDamageSystem reference (set after registration via SystemRegistry)
    private volatile BlinkTrailDamageSystem blinkTrailSystem;

    // Void Mark support (set after component registration)
    private volatile ComponentType<EntityStore, VoidMarkComponent> voidMarkType;
    private volatile VoidMarkTracker voidMarkTracker;

    public DaggerVanishSystem(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Set the BlinkTrailDamageSystem instance for trail damage on blink.
     */
    public void setBlinkTrailSystem(BlinkTrailDamageSystem system) {
        this.blinkTrailSystem = system;
    }

    /**
     * Enable void mark blink support. Called after VoidMarkComponent is registered.
     */
    public void setVoidMarkSupport(ComponentType<EntityStore, VoidMarkComponent> voidMarkType,
                                    VoidMarkTracker tracker) {
        this.voidMarkType = voidMarkType;
        this.voidMarkTracker = tracker;
    }

    @Nullable
    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
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
            @Nonnull Damage damage
    ) {
        EndgameConfig config = plugin.getConfig().get();

        // Check if the damage source is a player
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Use attacker's own store — handle() store belongs to the target
        Store<EntityStore> attackerStore = attackerRef.getStore();

        Player attacker = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) return;

        if (!isWieldingPrismaDaggers(attackerStore, attackerRef)) return;

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        // Don't vanish onto yourself
        if (attackerRef.equals(targetRef)) return;

        UUID attackerUuid = endgame.plugin.utils.EntityUtils.getUuid(attackerStore, attackerRef);
        if (attackerUuid == null) return;

        // Clean up expired invulnerability
        cleanupInvulnerability(attackerRef, commandBuffer, attackerUuid);

        // Check if attacker has an active signature interaction chain
        int signatureChainId = WeaponUtils.getActiveSignatureChainId(attackerStore, attackerRef);
        if (signatureChainId == -1) {
            // No signature chain active — nothing to do
            return;
        }

        // Only trigger once per signature chain (signatures are multi-hit)
        Integer lastChainId = lastTriggeredChainId.get(attackerUuid);
        if (lastChainId != null && lastChainId.intValue() == signatureChainId) {
            return;
        }
        lastTriggeredChainId.put(attackerUuid, signatureChainId);

        plugin.getLogger().atFine().log(
                "[DaggerVanish] Signature chain detected (chainId=%d)", signatureChainId);

        // Attempt vanish teleport
        long now = System.currentTimeMillis();
        Long cooldownEnd = vanishCooldowns.get(attackerUuid);
        if (cooldownEnd != null && now < cooldownEnd) {
            plugin.getLogger().atFine().log("[DaggerVanish] On cooldown for %dms",
                    cooldownEnd - now);
            return;
        }

        // Branch: Blink mode (forward teleport) vs Combat mode (behind target)
        if (isBlinkMode(attackerStore, attackerRef) && config.isDaggerBlinkEnabled()) {
            executeBlinkForward(attacker, attackerRef, attackerStore, attackerUuid, config);
        } else {
            // Check for void-marked target — blink to marked entity instead of current target
            Ref<EntityStore> vanishTarget = targetRef;

            if (voidMarkTracker != null && config.isVoidMarkEnabled()) {
                TransformComponent attackerTransform = attackerStore.getComponent(attackerRef, TransformComponent.getComponentType());
                if (attackerTransform != null && attackerTransform.getPosition() != null) {
                    VoidMarkTracker.MarkedEntity nearestMark = voidMarkTracker.findNearestMark(
                            attackerUuid, attackerTransform.getPosition(), 20.0);
                    if (nearestMark != null && nearestMark.ref() != null && nearestMark.ref().isValid()) {
                        vanishTarget = nearestMark.ref();

                        // Check for execution: target below 25% HP → 3x damage
                        float executionMultiplier = getExecutionMultiplier(vanishTarget.getStore(), vanishTarget, config);
                        if (executionMultiplier > 1.0f) {
                            damage.setAmount(damage.getAmount() * executionMultiplier);
                            plugin.getLogger().atFine().log(
                                "[DaggerVanish] Void Mark execution! Damage x%.1f", executionMultiplier);
                        }

                        // Consume the mark
                        voidMarkTracker.removeMark(vanishTarget);
                        if (voidMarkType != null) {
                            commandBuffer.removeComponent(vanishTarget, voidMarkType);
                        }

                        plugin.getLogger().atFine().log("[DaggerVanish] Void Mark blink — teleporting to marked target");
                    }
                }
            }

            // Execute vanish (teleport behind target)
            executeVanish(attacker, attackerRef, vanishTarget, store, commandBuffer, attackerUuid, config);
        }
    }

    /**
     * Teleport the attacker behind the target and apply invulnerability.
     */
    private void executeVanish(Player attacker, Ref<EntityStore> attackerRef, Ref<EntityStore> targetRef,
                                Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
                                UUID attackerUuid, EndgameConfig config) {
        ComponentType<EntityStore, TransformComponent> transformType = TransformComponent.getComponentType();
        if (transformType == null) return;

        // Use target's own store — targetRef may be redirected to a void-marked entity in a different store
        TransformComponent targetTransform = targetRef.getStore().getComponent(targetRef, transformType);
        if (targetTransform == null) return;

        Vector3d targetPos = targetTransform.getPosition();
        float targetYaw = targetTransform.getRotation().getYaw();

        // Calculate position behind the target (opposite of where they're facing)
        double yawRad = Math.toRadians(targetYaw);
        double behindX = targetPos.getX() - Math.sin(yawRad) * TELEPORT_DISTANCE;
        double behindZ = targetPos.getZ() + Math.cos(yawRad) * TELEPORT_DISTANCE;
        Vector3d behindPos = new Vector3d(behindX, targetPos.getY(), behindZ);

        // Face the same direction as the target (looking at their back from behind)
        Vector3f teleportRotation = new Vector3f(0, targetYaw, 0);

        // Set cooldown immediately to prevent double-triggers
        long cooldownEnd = System.currentTimeMillis() + config.getDaggerVanishCooldownMs();
        vanishCooldowns.put(attackerUuid, cooldownEnd);

        // Use world.execute() for thread-safe teleport + invulnerability
        World world = attacker.getWorld();
        if (world == null || !world.isAlive()) return;

        world.execute(() -> {
            // Capture ref once to avoid stale/changing refs between calls
            Ref<EntityStore> ref = attacker.getReference();
            if (ref == null || !ref.isValid()) return;

            // Safe teleport: check for solid blocks at destination
            Vector3d backDir = new Vector3d(
                    behindPos.getX() - targetPos.getX(), 0,
                    behindPos.getZ() - targetPos.getZ());
            Vector3d safePos = TeleportSafety.findSafePosition(world, behindPos, backDir);
            if (safePos == null) {
                plugin.getLogger().atFine().log("[DaggerVanish] Combat teleport cancelled — no safe position");
                return;
            }

            // Teleport using the proper Teleport component API
            Store<EntityStore> worldStore = ref.getStore();
            Teleport teleport = Teleport.createForPlayer(world, safePos, teleportRotation);
            worldStore.addComponent(ref, Teleport.getComponentType(), teleport);

            // Apply invulnerability on the world thread
            EffectControllerComponent effectController = worldStore.getComponent(
                    ref, EffectControllerComponent.getComponentType()
            );
            if (effectController != null) {
                effectController.setInvulnerable(true);
                long invulEnd = System.currentTimeMillis() + config.getDaggerVanishInvulnerabilityMs();
                vanishActiveUntil.put(attackerUuid, invulEnd);
            }

            plugin.getLogger().atFine().log(
                    "[DaggerVanish] Player teleported behind target at %.1f, %.1f, %.1f (invuln %dms, cd %dms)",
                    safePos.getX(), safePos.getY(), safePos.getZ(),
                    config.getDaggerVanishInvulnerabilityMs(), config.getDaggerVanishCooldownMs());
        });
    }

    /**
     * Teleport the attacker forward in their facing direction (Blink mode).
     * Uses configurable distance, same invulnerability + cooldown as combat vanish.
     *
     * All transform reads and teleport happen inside world.execute() to ensure
     * the position and rotation are current on the world thread (reading from the
     * damage event thread gives stale rotation, causing random blink directions).
     */
    private void executeBlinkForward(Player attacker, Ref<EntityStore> attackerRef,
                                      Store<EntityStore> attackerStore,
                                      UUID attackerUuid, EndgameConfig config) {
        // Set cooldown immediately to prevent double-triggers
        long cooldownEnd = System.currentTimeMillis() + config.getDaggerVanishCooldownMs();
        vanishCooldowns.put(attackerUuid, cooldownEnd);

        World world = attacker.getWorld();
        if (world == null || !world.isAlive()) return;

        double blinkDistance = config.getDaggerBlinkDistance();

        world.execute(() -> {
            Ref<EntityStore> ref = attacker.getReference();
            if (ref == null || !ref.isValid()) return;

            Store<EntityStore> worldStore = ref.getStore();

            // Read transform on the world thread for current position + look direction
            TransformComponent transform = worldStore.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d pos = transform.getPosition();
            float yaw = transform.getRotation().getYaw();

            double yawRad = Math.toRadians(yaw);

            // Safe blink: walk along path checking for solid blocks
            Vector3d safePos = TeleportSafety.stepAlongPath(world, pos, yawRad, blinkDistance);
            if (safePos == null) {
                plugin.getLogger().atFine().log("[DaggerVanish] Blink cancelled — no safe position ahead");
                return;
            }

            Vector3f teleportRotation = new Vector3f(0, yaw, 0);

            Teleport teleport = Teleport.createForPlayer(world, safePos, teleportRotation);
            worldStore.addComponent(ref, Teleport.getComponentType(), teleport);

            EffectControllerComponent effectController = worldStore.getComponent(
                    ref, EffectControllerComponent.getComponentType()
            );
            if (effectController != null) {
                effectController.setInvulnerable(true);
                long invulEnd = System.currentTimeMillis() + config.getDaggerVanishInvulnerabilityMs();
                vanishActiveUntil.put(attackerUuid, invulEnd);
            }

            // Shadow Trail: damage enemies along the blink path
            if (config.isDaggerTrailEnabled() && blinkTrailSystem != null) {
                blinkTrailSystem.addTrail(attackerUuid,
                        new BlinkTrailDamageSystem.TrailData(pos, safePos, ref, System.currentTimeMillis()));
            }

            plugin.getLogger().atFine().log(
                    "[DaggerVanish] Blink forward to %.1f, %.1f, %.1f (yaw=%.1f, distance=%.1f)",
                    safePos.getX(), safePos.getY(), safePos.getZ(), yaw, blinkDistance);
        });
    }

    /**
     * Get execution multiplier based on target HP ratio.
     * Returns 3.0 if target is below 25% HP, 1.0 otherwise.
     */
    private float getExecutionMultiplier(Store<EntityStore> store, Ref<EntityStore> targetRef,
                                          EndgameConfig config) {
        if (!config.isVoidMarkExecutionEnabled()) return 1.0f;

        try {
            ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
            if (statType == null) return 1.0f;

            EntityStatMap statMap = store.getComponent(targetRef, statType);
            if (statMap == null) return 1.0f;

            EntityStatValue healthValue = statMap.get(DefaultEntityStatTypes.getHealth());
            if (healthValue == null) return 1.0f;

            float current = healthValue.get();
            float max = healthValue.getMax();
            if (max <= 0) return 1.0f;

            float hpRatio = current / max;
            if (hpRatio < config.getVoidMarkExecutionThreshold()) {
                return config.getVoidMarkExecutionMultiplier();
            }
        } catch (Exception e) {
            // Silently fall through
        }
        return 1.0f;
    }

    /**
     * Clean up expired invulnerability for this player.
     */
    private void cleanupInvulnerability(Ref<EntityStore> playerRef, CommandBuffer<EntityStore> commandBuffer,
                                         UUID playerUuid) {
        Long invulEnd = vanishActiveUntil.get(playerUuid);
        if (invulEnd != null && System.currentTimeMillis() >= invulEnd) {
            vanishActiveUntil.remove(playerUuid);

            EffectControllerComponent effectController = playerRef.getStore().getComponent(
                    playerRef, EffectControllerComponent.getComponentType()
            );
            if (effectController != null) {
                effectController.setInvulnerable(false);
                plugin.getLogger().atFine().log("[DaggerVanish] Invulnerability expired for player");
            }
        }
    }

    /**
     * Check if the player is wielding Prisma Daggers (any state variant).
     */
    private boolean isWieldingPrismaDaggers(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return false;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem != null && !ItemStack.isEmpty(activeItem)) {
            Item item = activeItem.getItem();
            if (item != null && item.getId() != null && item.getId().contains("Daggers_Prisma")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the player's Prisma Daggers are in Blink mode.
     * Blink mode = item state variant (ID differs from base PRISMA_DAGGER_ID).
     */
    private boolean isBlinkMode(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return false;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem != null && !ItemStack.isEmpty(activeItem)) {
            Item item = activeItem.getItem();
            if (item != null && item.getId() != null) {
                // Base item = "Endgame_Daggers_Prisma", blink variant will have a different ID
                return item.getId().contains("Daggers_Prisma") && !PRISMA_DAGGER_ID.equals(item.getId());
            }
        }
        return false;
    }


    /**
     * Remove stale entries older than 5 minutes. Called periodically from DangerZoneTickSystem.
     */
    public void cleanupStaleEntries(long now) {
        vanishCooldowns.entrySet().removeIf(e -> now - e.getValue() > 300_000);
        vanishActiveUntil.entrySet().removeIf(e -> now - e.getValue() > 300_000);
        // lastTriggeredChainId doesn't have timestamps — clear entries not in cooldowns map
        lastTriggeredChainId.keySet().retainAll(vanishCooldowns.keySet());
    }

    /**
     * Clean up state for a disconnecting player.
     */
    public void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        lastTriggeredChainId.remove(playerUuid);
        vanishCooldowns.remove(playerUuid);
        vanishActiveUntil.remove(playerUuid);
    }

    /**
     * Force clear all state. Called on plugin shutdown.
     */
    public void forceClear() {
        lastTriggeredChainId.clear();
        vanishCooldowns.clear();
        vanishActiveUntil.clear();
    }
}
