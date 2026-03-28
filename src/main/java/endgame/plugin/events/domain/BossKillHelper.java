package endgame.plugin.events.domain;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.utils.EntityUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared logic for boss kill credit distribution and event publishing.
 * Eliminates duplication between GenericBossDeathSystem and GolemVoidDeathSystem.
 */
public final class BossKillHelper {

    private BossKillHelper() {}

    /**
     * Determine credited players and publish a BossKillEvent.
     *
     * @param plugin           the plugin instance
     * @param store            the entity store (for world access)
     * @param deathComponent   the death component (for killer info)
     * @param npcTypeId        the boss NPC type ID
     * @param displayName      the boss display name
     * @param spawnTimestamp    when the boss was spawned (millis)
     */
    public static void publishBossKill(EndgameQoL plugin, Store<EntityStore> store,
                                        DeathComponent deathComponent, String npcTypeId,
                                        String displayName, long spawnTimestamp) {
        long elapsedSeconds = (System.currentTimeMillis() - spawnTimestamp) / 1000;
        Set<UUID> creditedPlayers = new HashSet<>();

        try {
            if (plugin.getConfig().get().isSharedBossKillCredit()) {
                // Shared credit: all players in the same world
                World bossWorld = store.getExternalData().getWorld();
                for (PlayerRef pr : Universe.get().getPlayers()) {
                    if (pr == null) continue;
                    Ref<EntityStore> pRef = pr.getReference();
                    if (pRef == null || !pRef.isValid()) continue;
                    Player p = pRef.getStore().getComponent(pRef, Player.getComponentType());
                    if (p == null) continue;
                    World pWorld = pRef.getStore().getExternalData().getWorld();
                    if (!bossWorld.equals(pWorld)) continue;
                    UUID pUuid = EntityUtils.getUuid(pRef.getStore(), pRef);
                    if (pUuid != null) creditedPlayers.add(pUuid);
                }
            } else {
                // Solo credit: only the killer
                Damage deathInfo = deathComponent.getDeathInfo();
                if (deathInfo != null && deathInfo.getSource() instanceof Damage.EntitySource es) {
                    Ref<EntityStore> killerRef = es.getRef();
                    if (killerRef != null && killerRef.isValid()) {
                        Player player = killerRef.getStore().getComponent(killerRef, Player.getComponentType());
                        if (player != null) {
                            UUID killerUuid = EntityUtils.getUuid(killerRef.getStore(), killerRef);
                            if (killerUuid != null) creditedPlayers.add(killerUuid);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().atFine().log("[BossKill] Could not determine credited players: %s", e.getMessage());
        }

        if (!creditedPlayers.isEmpty()) {
            plugin.getGameEventBus().publish(new GameEvent.BossKillEvent(
                    creditedPlayers, npcTypeId, displayName, elapsedSeconds));
        }
    }
}
