package endgame.plugin.systems.portal;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.Message;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Handles player death inside temporal portal instances.
 * If AllowRespawnInside is enabled for the dungeon, the player respawns
 * at the return portal instead of being kicked to the overworld.
 */
public class InstanceRespawnSystem extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;

    public InstanceRespawnSystem(@Nonnull EndgameQoL plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        World world = store.getExternalData().getWorld();
        if (world == null) return;

        String worldName = world.getName();

        // Check if player is in a temporal portal instance
        var portalMgr = plugin.getTemporalPortalManager();
        if (portalMgr == null) return;

        for (TemporalPortalSession session : portalMgr.getActiveSessions().values()) {
            World instanceWorld = session.getInstanceWorld();
            if (instanceWorld == null || !instanceWorld.isAlive()) continue;
            if (!instanceWorld.getName().equals(worldName)) continue;

            // Player is in this instance
            DungeonDefinition def = session.getDungeonDef();
            if (!def.isAllowRespawnInside()) return;

            // Get spawn point from instance's spawn provider
            var spawnProvider = instanceWorld.getWorldConfig().getSpawnProvider();
            if (spawnProvider == null) return;

            var spawnTransform = spawnProvider.getSpawnPoint(instanceWorld, null);
            if (spawnTransform == null) return;

            Vector3d spawnPos = spawnTransform.getPosition();

            // Schedule respawn teleport after death animation (3s)
            // Using commandBuffer.run() to safely queue the teleport
            commandBuffer.run(s -> {
                if (!ref.isValid()) return;
                try {
                    var teleportType = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.getComponentType();
                    var teleport = com.hypixel.hytale.server.core.modules.entity.teleport.Teleport.createForPlayer(
                            instanceWorld, spawnPos, new Vector3f(0, 0, 0));
                    s.addComponent(ref, teleportType, teleport);
                } catch (Exception e) {
                    plugin.getLogger().atWarning().log("[InstanceRespawn] Teleport failed: %s", e.getMessage());
                }
            });

            // Notify player
            PlayerRef playerRef = findPlayerRef(ref);
            if (playerRef != null) {
                playerRef.sendMessage(Message.raw("[Temporal Portal] Respawning at the portal...").color("#ffaa00"));
            }

            plugin.getLogger().atFine().log("[InstanceRespawn] Player respawning in instance %s", worldName);
            return;
        }
    }

    @Nullable
    private PlayerRef findPlayerRef(@Nonnull Ref<EntityStore> ref) {
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p != null && p.getReference() != null && p.getReference().equals(ref)) {
                return p;
            }
        }
        return null;
    }
}
