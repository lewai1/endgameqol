package endgame.plugin.systems.weapon;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
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
import endgame.plugin.utils.WeaponUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deducts mana from the ATTACKER when using Prisma weapon signature attacks.
 * ChangeStat in JSON targets the hit entity (wrong), so this system applies
 * the cost via Java using the attacker's EntityStatMap directly.
 *
 * - Prisma Sword (Vortexstrike): -15 mana
 * - Prisma Daggers (Razorstrike): -12 mana
 *
 * Detection: Inspects the attacker's active InteractionChain via InteractionManager.
 * When a chain with "Signature" in its RootInteraction ID is active, the signature
 * attack is in progress. Triggers once per chain ID to avoid double-deducting on multi-hit.
 */
public class PrismaManaCostSystem extends DamageEventSystem {

    private static final String PRISMA_SWORD_ID = "Endgame_Sword_Prisma";
    private static final String PRISMA_DAGGER_ID = "Endgame_Daggers_Prisma";
    private static final float SWORD_MANA_COST = -15f;
    private static final float DAGGER_MANA_COST = -12f;

    private final EndgameQoL plugin;
    private final ConcurrentHashMap<UUID, Integer> lastTriggeredChainId = new ConcurrentHashMap<>();

    public PrismaManaCostSystem(EndgameQoL plugin) {
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
        if (!(damage.getSource() instanceof Damage.EntitySource entitySource)) return;

        Ref<EntityStore> attackerRef = entitySource.getRef();
        if (attackerRef == null || !attackerRef.isValid()) return;

        // Use attacker's own store — handle() store belongs to the target
        Store<EntityStore> attackerStore = attackerRef.getStore();

        Player attacker = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (attacker == null) return;

        String weaponId = getWieldedPrismaWeaponId(attackerStore, attackerRef);
        if (weaponId == null) return;

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;
        if (attackerRef.equals(targetRef)) return;

        UUID attackerUuid = endgame.plugin.utils.EntityUtils.getUuid(attackerStore, attackerRef);
        if (attackerUuid == null) return;

        // Check if attacker has an active signature interaction chain
        int signatureChainId = WeaponUtils.getActiveSignatureChainId(attackerStore, attackerRef);
        if (signatureChainId == -1) {
            // No signature chain active — nothing to do
            return;
        }

        // Only trigger once per signature chain (signatures are multi-hit)
        // Atomic: putIfAbsent returns null if inserted, or existing value if already present
        Integer prev = lastTriggeredChainId.putIfAbsent(attackerUuid, signatureChainId);
        if (prev != null) {
            if (prev.intValue() == signatureChainId) {
                return; // Already triggered for this chain
            }
            lastTriggeredChainId.put(attackerUuid, signatureChainId);
        }

        plugin.getLogger().atFine().log(
                "[PrismaManaCost] Signature chain detected (chainId=%d, weapon=%s)",
                signatureChainId, weaponId);

        // Deduct mana from attacker
        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType == null) return;

        EntityStatMap attackerStats = attackerStore.getComponent(attackerRef, statType);
        if (attackerStats == null) return;

        float manaCost = PRISMA_SWORD_ID.equals(weaponId) ? SWORD_MANA_COST : DAGGER_MANA_COST;
        int manaStat = DefaultEntityStatTypes.getMana();
        EntityStatValue manaValue = attackerStats.get(manaStat);
        if (manaValue == null) return;
        attackerStats.addStatValue(manaStat, manaCost);

        // Apply damage boost for signature attack
        float boostMultiplier = PRISMA_SWORD_ID.equals(weaponId)
                ? 1.5f
                : 1.3f;
        if (boostMultiplier != 1.0f) {
            damage.setAmount(damage.getAmount() * boostMultiplier);
        }

        plugin.getLogger().atFine().log(
                "[PrismaManaCost] Deducted %.0f mana, damage x%.1f (weapon: %s)",
                -manaCost, boostMultiplier, weaponId);
    }

    @Nullable
    private String getWieldedPrismaWeaponId(Store<EntityStore> store, Ref<EntityStore> playerRef) {
        InventoryComponent.Hotbar hotbar = store.getComponent(playerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return null;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem != null && !ItemStack.isEmpty(activeItem)) {
            Item item = activeItem.getItem();
            if (item != null) {
                String id = item.getId();
                if (PRISMA_SWORD_ID.equals(id) || PRISMA_DAGGER_ID.equals(id)) {
                    return id;
                }
            }
        }
        return null;
    }


    /**
     * Remove stale entries older than 5 minutes. Called periodically from DangerZoneTickSystem.
     */
    public void cleanupStaleEntries(long now) {
        // lastTriggeredChainId has no timestamps — clear all entries older than the map itself
        // Since chain IDs are transient, clearing periodically is safe
        lastTriggeredChainId.clear();
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        if (playerUuid == null) return;
        lastTriggeredChainId.remove(playerUuid);
    }

    public void forceClear() {
        lastTriggeredChainId.clear();
    }
}
