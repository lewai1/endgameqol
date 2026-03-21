package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import endgame.plugin.EndgameQoL;
import endgame.plugin.config.EndgameConfig;
import endgame.plugin.utils.WeaponUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prisma Mirage — spawns 2 spectral clones when using Prisma Sword's
 * Vortexstrike signature attack. Clones attack nearby mobs and despawn
 * after 5 seconds (handled by PrismaMirageCleanupSystem).
 *
 * Detection: Same signature chain detection pattern as PrismaManaCostSystem.
 * Triggers once per chain ID, with a configurable cooldown (default 15s).
 */
public class PrismaMirageSystem extends DamageEventSystem {

    private static final String PRISMA_SWORD_ID = "Endgame_Sword_Prisma";
    private static final String CLONE_NPC_ID = "Prisma_Clone";
    private static final int CLONE_COUNT = 2;
    private static final double SPAWN_DISTANCE = 3.5;
    private static final double SPAWN_ANGLE_OFFSET = Math.toRadians(130);

    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, Integer> lastTriggeredChainId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> mirageCooldowns = new ConcurrentHashMap<>();

    public PrismaMirageSystem(EndgameQoL plugin) {
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

        if (!config.isPrismaMirageEnabled()) {
            plugin.getLogger().atFine().log("[PrismaMirage] Disabled in config");
            return;
        }

        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Use attacker's own store — handle() store belongs to the target
        Store<EntityStore> attackerStore = attackerRef.getStore();

        Player attacker = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) return;

        if (!isWieldingPrismaSword(attackerStore, attackerRef)) {
            return;
        }

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;
        if (attackerRef.equals(targetRef)) return;

        UUID attackerUuid = endgame.plugin.utils.EntityUtils.getUuid(attackerStore, attackerRef);
        if (attackerUuid == null) return;

        // Check for active signature chain
        int signatureChainId = WeaponUtils.getActiveSignatureChainId(attackerStore, attackerRef);
        if (signatureChainId == -1) {
            plugin.getLogger().atFine().log("[PrismaMirage] No signature chain active");
            return;
        }

        plugin.getLogger().atFine().log("[PrismaMirage] Signature chain detected (id=%d)", signatureChainId);

        // Dedup: only trigger once per chain
        Integer lastChainId = lastTriggeredChainId.get(attackerUuid);
        if (lastChainId != null && lastChainId.intValue() == signatureChainId) {
            plugin.getLogger().atFine().log("[PrismaMirage] Already triggered for chain %d", signatureChainId);
            return;
        }
        lastTriggeredChainId.put(attackerUuid, signatureChainId);

        // Check cooldown
        long now = System.currentTimeMillis();
        Long cooldownEnd = mirageCooldowns.get(attackerUuid);
        if (cooldownEnd != null && now < cooldownEnd) {
            plugin.getLogger().atFine().log("[PrismaMirage] On cooldown for %dms",
                    cooldownEnd - now);
            return;
        }

        mirageCooldowns.put(attackerUuid, now + config.getPrismaMirageCooldownMs());

        plugin.getLogger().atFine().log("[PrismaMirage] Triggering clone spawn!");

        // Spawn clones
        spawnClones(attacker);
    }

    private void spawnClones(Player attacker) {
        EndgameConfig config = plugin.getConfig().get();

        Ref<EntityStore> playerRef = attacker.getReference();
        if (playerRef == null || !playerRef.isValid()) {
            plugin.getLogger().atWarning().log("[PrismaMirage] Player ref is null/invalid");
            return;
        }

        Store<EntityStore> attackerStore = playerRef.getStore();
        TransformComponent attackerTransform = attackerStore.getComponent(
                playerRef, TransformComponent.getComponentType());
        if (attackerTransform == null) {
            plugin.getLogger().atWarning().log("[PrismaMirage] TransformComponent is null");
            return;
        }

        Vector3d attackerPos = attackerTransform.getPosition();
        float attackerYaw = attackerTransform.getRotation().getYaw();
        double yawRad = Math.toRadians(attackerYaw);

        World world = attacker.getWorld();
        if (world == null) {
            plugin.getLogger().atWarning().log("[PrismaMirage] World is null");
            return;
        }

        plugin.getLogger().atFine().log(
                "[PrismaMirage] Attempting to spawn %d clones of '%s' at %.1f, %.1f, %.1f",
                CLONE_COUNT, CLONE_NPC_ID, attackerPos.x, attackerPos.y, attackerPos.z);

        if (!world.isAlive()) return;
        world.execute(() -> {
            int spawnedCount = 0;
            for (int i = 0; i < CLONE_COUNT; i++) {
                // Left clone at -60°, right clone at +60° from facing direction
                double angleOffset = (i == 0) ? -SPAWN_ANGLE_OFFSET : SPAWN_ANGLE_OFFSET;
                double spawnAngle = yawRad + angleOffset;

                double spawnX = attackerPos.x - Math.sin(spawnAngle) * SPAWN_DISTANCE;
                double spawnZ = attackerPos.z + Math.cos(spawnAngle) * SPAWN_DISTANCE;
                Vector3d spawnPos = new Vector3d(spawnX, attackerPos.y, spawnZ);
                Vector3f rotation = new Vector3f(0, attackerYaw, 0);

                try {
                    Store<EntityStore> worldStore = world.getEntityStore().getStore();
                    var spawnResult = NPCPlugin.get().spawnNPC(worldStore, CLONE_NPC_ID, null, spawnPos, rotation);
                    Ref<EntityStore> spawnedRef = (spawnResult != null) ? spawnResult.first() : null;
                    if (spawnedRef != null && spawnedRef.isValid()) {
                        spawnedCount++;
                        plugin.getLogger().atFine().log(
                                "[PrismaMirage] Clone %d spawned successfully at %.1f, %.1f, %.1f",
                                i, spawnPos.x, spawnPos.y, spawnPos.z);
                    } else {
                        plugin.getLogger().atWarning().log(
                                "[PrismaMirage] Clone %d spawn returned null/invalid ref", i);
                    }
                } catch (Exception e) {
                    plugin.getLogger().atWarning().withCause(e).log(
                            "[PrismaMirage] Failed to spawn clone %d", i);
                }
            }

            plugin.getLogger().atFine().log(
                    "[PrismaMirage] Spawn complete: %d/%d clones (cd %dms)",
                    spawnedCount, CLONE_COUNT, config.getPrismaMirageCooldownMs());
        });
    }

    private boolean isWieldingPrismaSword(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return false;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem != null && !ItemStack.isEmpty(activeItem)) {
            Item item = activeItem.getItem();
            if (item != null && PRISMA_SWORD_ID.equals(item.getId())) {
                return true;
            }
        }
        return false;
    }


    /**
     * Remove stale entries older than 5 minutes. Called periodically from DangerZoneTickSystem.
     */
    public void cleanupStaleEntries(long now) {
        mirageCooldowns.entrySet().removeIf(e -> now - e.getValue() > 300_000);
        lastTriggeredChainId.keySet().retainAll(mirageCooldowns.keySet());
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        lastTriggeredChainId.remove(playerUuid);
        mirageCooldowns.remove(playerUuid);
    }

    public void forceClear() {
        lastTriggeredChainId.clear();
        mirageCooldowns.clear();
    }
}
