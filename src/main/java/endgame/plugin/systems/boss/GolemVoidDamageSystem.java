package endgame.plugin.systems.boss;

import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GolemVoidBossManager;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;

/**
 * Listens for damage events on Golem Void bosses.
 * Updates the GolemVoidBossManager when the boss takes damage.
 * Applies difficulty multiplier to boss damage output.
 */
public class GolemVoidDamageSystem extends AbstractBossDamageSystem {

    private final GolemVoidBossManager bossManager;
    private final EndgameQoL plugin;
    private final EnrageTracker enrageTracker;

    public GolemVoidDamageSystem(GolemVoidBossManager bossManager, EnrageTracker enrageTracker) {
        this.bossManager = bossManager;
        this.plugin = bossManager.getPlugin();
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
        // Get the target entity (the one receiving damage)
        Ref<EntityStore> targetRef = archetypeChunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        // Check if ATTACKER is Golem Void - multiply damage dealt BY boss
        Ref<EntityStore> attackerRef = resolveAttacker(damage);
        if (attackerRef != null) {
            String attackerTypeId = resolveNPCTypeId(attackerRef, store);
            if (attackerTypeId != null && attackerTypeId.toLowerCase().contains("endgame_golem_void")) {
                // Skip damage multiplier if target is a friendly Void minion
                ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
                if (npcType != null) {
                    NPCEntity targetNpc = store.getComponent(targetRef, npcType);
                    if (targetNpc != null) {
                        String targetTypeId = targetNpc.getNPCTypeId();
                        if (targetTypeId != null && targetTypeId.toLowerCase().contains("eye_void")) {
                            return;
                        }
                    }
                }

                // Apply difficulty multiplier (base damage now baked into JSON)
                float currentDamage = damage.getAmount();
                float difficultyMult = plugin.getConfig().get().getEffectiveBossDamageMultiplier(endgame.plugin.utils.BossType.GOLEM_VOID);
                float enrageMult = enrageTracker.getEnrageMultiplier(attackerRef);
                damage.setAmount(currentDamage * difficultyMult * enrageMult);

                plugin.getLogger().atFine().log(
                        "[GolemVoidDamage] Boss damage: base=%.1f, diffMult=%.1fx, enrageMult=%.1fx, final=%.1f",
                        currentDamage, difficultyMult, enrageMult, currentDamage * difficultyMult * enrageMult);
            }
        }

        // Check if TARGET is a Golem Void (for invulnerability/phase logic)
        ComponentType<EntityStore, NPCEntity> npcType = NPCEntity.getComponentType();
        if (npcType == null) return;

        NPCEntity npcEntity = store.getComponent(targetRef, npcType);
        if (npcEntity == null)
            return;

        String npcTypeId = npcEntity.getNPCTypeId();
        if (npcTypeId == null || !npcTypeId.toLowerCase().contains("endgame_golem_void")) {
            return;
        }

        // Register boss EARLY (before damage blocking) so the speed floor modifier
        // is applied even if the first damage event comes from a trap.
        if (bossManager.getBossState(targetRef) == null) {
            bossManager.registerBoss(targetRef, npcTypeId, store);
        }

        // Block non-player damage (e.g., bear traps, environment) to the boss
        if (!(damage.getSource() instanceof Damage.EntitySource)) {
            damage.setCancelled(true);
            plugin.getLogger().atFine().log(
                    "[GolemVoidDamage] Blocked non-entity damage to boss (source: %s)",
                    damage.getSource() != null ? damage.getSource().getClass().getSimpleName() : "null");
            return;
        }

        // Get stats and update boss system
        ComponentType<EntityStore, EntityStatMap> statType = EntityStatMap.getComponentType();
        if (statType == null)
            return;

        EntityStatMap statMap = store.getComponent(targetRef, statType);
        if (statMap == null)
            return;

        // Check invulnerability timeout
        bossManager.checkInvulnerabilityTimeout(targetRef);

        // Always update health tracking (even when invulnerable)
        bossManager.updateHealthTracking(targetRef, statMap);

        // Show boss bar to attacker (always, to keep UI updated)
        if (attackerRef != null) {
            PlayerRef playerRef = findPlayerRef(attackerRef);
            if (playerRef != null) {
                bossManager.showBossBarToPlayer(playerRef, store);
            }
        }

        // Check if boss is invulnerable - cancel damage but don't skip UI updates
        if (bossManager.isBossInvulnerable(targetRef)) {
            damage.setCancelled(true);
            return;
        }

        // Notify the boss system about damage (for phase transitions)
        bossManager.onBossDamaged(targetRef, statMap, store);

        // Record damage for enrage tracking
        enrageTracker.recordDamage(targetRef, npcTypeId, damage.getAmount(), System.currentTimeMillis());
    }
}
