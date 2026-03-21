package endgame.plugin.systems.boss;

import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GenericBossManager;
import endgame.plugin.utils.BossType;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Damage system for bosses handled by GenericBossManager (Frost Dragon, Hedera).
 * Runs in FilterDamageGroup to modify/cancel damage before it's applied.
 *
 * Handles:
 * - Lazy boss registration on first damage
 * - Blocking non-entity damage (traps, environment)
 * - Invulnerability checks during phase transitions
 * - Phase transition triggers via GenericBossManager
 * - Per-boss damage tracking for target switch aggro
 */
public class GenericBossDamageSystem extends AbstractBossDamageSystem {

    // Hedera frost weakness: 30% bonus damage from Endgame_Frozen_Sword
    private static final String FROST_SWORD_ID = "Endgame_Frozen_Sword";
    private static final float HEDERA_FROST_BONUS = 1.30f;
    private static final long DAMAGE_DECAY_MS = 30_000;

    private final GenericBossManager bossManager;
    private final EndgameQoL plugin;
    private final EnrageTracker enrageTracker;

    // Per-boss damage tracking: bossRef -> (playerUUID -> accumulated damage)
    private final Map<Ref<EntityStore>, Map<UUID, Double>> recentDamage = new ConcurrentHashMap<>();
    private final Map<Ref<EntityStore>, Long> lastDecayTime = new ConcurrentHashMap<>();

    public GenericBossDamageSystem(GenericBossManager bossManager, EndgameQoL plugin, EnrageTracker enrageTracker) {
        this.bossManager = bossManager;
        this.plugin = plugin;
        this.enrageTracker = enrageTracker;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return QUERY_ANY;
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) return;

        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) return;

        NPCEntity npcEntity = store.getComponent(targetRef, npcType);
        if (npcEntity == null) return;

        String npcTypeId = npcEntity.getNPCTypeId();
        if (npcTypeId == null) return;

        // Only handle boss/elite types that have a config (Golem Void has its own system)
        BossType bossType = BossType.fromTypeId(npcTypeId);
        if (bossType == null) return;
        GenericBossManager.BossEncounterConfig config = bossManager.getConfigForBossType(bossType);
        if (config == null) return;

        // Lazy registration on first damage
        if (bossManager.getBossState(targetRef) == null) {
            bossManager.registerBoss(targetRef, npcTypeId, store);
        }

        // For elites: just track health and show bar, don't block/modify damage
        if (config.elite) {
            ComponentType<EntityStore, EntityStatMap> eliteStatType = EntityStatMap.getComponentType();
            if (eliteStatType != null) {
                EntityStatMap eliteStatMap = store.getComponent(targetRef, eliteStatType);
                if (eliteStatMap != null) {
                    bossManager.updateHealthTracking(targetRef, eliteStatMap);
                }
            }
            // Show elite bar to attacker
            Ref<EntityStore> attackerRef = resolveAttacker(damage);
            if (attackerRef != null) {
                PlayerRef playerRef = findPlayerRef(attackerRef);
                if (playerRef != null) {
                    bossManager.showBossBarToPlayer(playerRef, targetRef, store);
                }
            }
            return; // Don't filter damage for elites
        }

        // Block non-entity damage (traps, environment) — bosses only
        if (!(damage.getSource() instanceof Damage.EntitySource)) {
            damage.setCancelled(true);
            plugin.getLogger().atFine().log(
                        "[GenericBossDamage] Blocked non-entity damage to %s", bossType.getDisplayName());
            return;
        }

        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType == null) return;
        EntityStatMap statMap = store.getComponent(targetRef, statType);
        if (statMap == null) return;

        // Check invulnerability timeout
        bossManager.checkInvulnerabilityTimeout(targetRef);

        // Always update health tracking
        bossManager.updateHealthTracking(targetRef, statMap);

        // Show boss bar to attacker
        Ref<EntityStore> attackerRef = resolveAttacker(damage);
        if (attackerRef != null) {
            PlayerRef playerRef = findPlayerRef(attackerRef);
            if (playerRef != null) {
                bossManager.showBossBarToPlayer(playerRef, targetRef, store);
            }
        }

        // Cancel damage if invulnerable
        if (bossManager.isBossInvulnerable(targetRef)) {
            damage.setCancelled(true);
            return;
        }

        // Hedera frost weakness: bonus damage when attacker wields Endgame_Frozen_Sword
        if (bossType == BossType.HEDERA && attackerRef != null) {
            if (isAttackerWieldingFrostSword(attackerRef)) {
                float original = damage.getAmount();
                damage.setAmount(original * HEDERA_FROST_BONUS);
                    plugin.getLogger().atFine().log(
                            "[GenericBossDamage] Hedera frost weakness: %.1f -> %.1f (x%.0f%%)",
                            original, damage.getAmount(), (HEDERA_FROST_BONUS - 1.0f) * 100);
            }
        }

        // Notify boss system for phase transitions
        bossManager.onBossDamaged(targetRef, statMap, store);

        // Record damage for enrage tracking
        enrageTracker.recordDamage(targetRef, npcTypeId, damage.getAmount(), System.currentTimeMillis());

        // Record damage for target switch aggro tracking + bounty DAMAGE_DEALT hook
        if (attackerRef != null) {
            PlayerRef attPRef = findPlayerRef(attackerRef);
            if (attPRef != null) {
                UUID attackerUuid = endgame.plugin.utils.EntityUtils.getUuid(attPRef);
                if (attackerUuid != null) {
                    recordDamageForAggro(targetRef, attackerUuid, damage.getAmount());
                    // Phase 2: Bounty DAMAGE_DEALT tracking
                    var bountyManager = plugin.getBountyManager();
                    if (bountyManager != null) {
                        bountyManager.onBossDamageDealt(attackerUuid, damage.getAmount());
                    }
                }
            }
        }
    }

    /**
     * Check if the attacker is a player wielding the Endgame_Frozen_Sword.
     * Uses the attacker's own store for cross-world safety.
     */
    private boolean isAttackerWieldingFrostSword(Ref<EntityStore> attackerRef) {
        if (attackerRef == null || !attackerRef.isValid()) return false;

        // Use attacker's own store — the handle() store is the target's store (different world in instances)
        Store<EntityStore> attackerStore = attackerRef.getStore();

        Player player = attackerStore.getComponent(attackerRef, Player.getComponentType());
        if (player == null) return false;

        InventoryComponent.Hotbar hotbar = attackerStore.getComponent(attackerRef, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) return false;

        ItemStack activeItem = hotbar.getActiveItem();
        if (activeItem == null || ItemStack.isEmpty(activeItem)) return false;

        Item item = activeItem.getItem();
        return item != null && FROST_SWORD_ID.equals(item.getId());
    }

    // =========================================================================
    // Damage tracking for boss target switching
    // =========================================================================

    private void recordDamageForAggro(Ref<EntityStore> bossRef, UUID attackerUuid, float amount) {
        Map<UUID, Double> bossMap = recentDamage.computeIfAbsent(bossRef, k -> new ConcurrentHashMap<>());

        // Decay old values periodically
        long now = System.currentTimeMillis();
        Long lastDecay = lastDecayTime.get(bossRef);
        if (lastDecay == null || now - lastDecay > DAMAGE_DECAY_MS) {
            lastDecayTime.put(bossRef, now);
            // Halve all accumulated damage (exponential decay)
            bossMap.replaceAll((k, v) -> v * 0.5);
            bossMap.values().removeIf(v -> v < 1.0);
        }

        bossMap.merge(attackerUuid, (double) amount, Double::sum);
    }

    /**
     * Get recent damage map for a boss (used by BossTargetSwitchSystem).
     */
    @Nullable
    public Map<UUID, Double> getRecentDamage(Ref<EntityStore> bossRef) {
        return recentDamage.get(bossRef);
    }

    /**
     * Clear damage tracking for a boss (called on death).
     */
    public void clearDamageTracking(Ref<EntityStore> bossRef) {
        recentDamage.remove(bossRef);
        lastDecayTime.remove(bossRef);
    }

    /**
     * Clear all damage tracking (shutdown).
     */
    public void forceClear() {
        recentDamage.clear();
        lastDecayTime.clear();
    }
}
