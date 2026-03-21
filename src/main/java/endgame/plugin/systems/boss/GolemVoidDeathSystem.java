package endgame.plugin.systems.boss;

import endgame.plugin.managers.BountyManager;
import endgame.plugin.managers.boss.EnrageTracker;
import endgame.plugin.managers.boss.GolemVoidBossManager;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.EntityUtils;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * System to detect when the Golem Void boss dies.
 * Extends DeathSystems.OnDeathSystem to receive death events for NPCs.
 * 
 * When the boss dies:
 * - Removes boss bar from all players
 * - Unregisters the boss from the system
 * - Can trigger death particles/effects
 */
public class GolemVoidDeathSystem extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;
    private final GolemVoidBossManager bossManager;
    private final EnrageTracker enrageTracker;

    public GolemVoidDeathSystem(EndgameQoL plugin, GolemVoidBossManager bossManager, EnrageTracker enrageTracker) {
        this.plugin = plugin;
        this.bossManager = bossManager;
        this.enrageTracker = enrageTracker;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Query for NPCs with death component
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Check if this is a tracked Golem Void boss
            GolemVoidBossManager.GolemVoidState state = bossManager.getBossState(ref);
            if (state == null) return;
            
            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss death detected: %s", state.npcTypeId);

            // Unregister the boss (internally calls hideAllBossBars)
            bossManager.unregisterBoss(ref);
            enrageTracker.removeBoss(ref);

            // Bounty + Achievement hooks
            long elapsedSeconds = (System.currentTimeMillis() - state.spawnTimestamp) / 1000;
            try {
                if (plugin.getConfig().get().isSharedBossKillCredit()) {
                    // Shared credit: all players in the same world get bounty + achievement
                    World bossWorld = store.getExternalData().getWorld();
                    for (PlayerRef pr : Universe.get().getPlayers()) {
                        if (pr == null) continue;
                        Ref<EntityStore> pRef = pr.getReference();
                        if (pRef == null || !pRef.isValid()) continue;
                        Player p = pRef.getStore().getComponent(pRef, Player.getComponentType());
                        if (p == null || !bossWorld.equals(p.getWorld())) continue;
                        UUID pUuid = EntityUtils.getUuid(pRef.getStore(), pRef);
                        if (pUuid == null) continue;
                        notifyBossKill(pUuid, state.npcTypeId, elapsedSeconds);
                    }
                } else {
                    // Solo credit: only the killer
                    Damage deathInfo = component.getDeathInfo();
                    if (deathInfo != null && deathInfo.getSource() instanceof Damage.EntitySource es) {
                        Ref<EntityStore> killerRef = es.getRef();
                        if (killerRef != null && killerRef.isValid()) {
                            Player player = killerRef.getStore().getComponent(killerRef, Player.getComponentType());
                            if (player != null) {
                                UUID killerUuid = EntityUtils.getUuid(killerRef.getStore(), killerRef);
                                if (killerUuid != null) {
                                    notifyBossKill(killerUuid, state.npcTypeId, elapsedSeconds);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().atFine().log("[GolemVoidBoss] Could not notify bounty/achievement of boss kill: %s", ex.getMessage());
            }

            plugin.getLogger().atFine().log("[GolemVoidBoss] Boss cleanup complete - bars hidden, boss unregistered");

        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[GolemVoidBoss] Error handling boss death: %s", e.getMessage());
        }
    }

    private void notifyBossKill(UUID playerUuid, String npcTypeId, long elapsedSeconds) {
        BountyManager bountyManager = plugin.getBountyManager();
        if (bountyManager != null) {
            bountyManager.onBossKill(playerUuid, npcTypeId, elapsedSeconds);
        }
        if (plugin.getAchievementManager() != null) {
            plugin.getAchievementManager().onBossKill(playerUuid, npcTypeId);
        }
    }
}
