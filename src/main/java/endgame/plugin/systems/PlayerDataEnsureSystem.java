package endgame.plugin.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import endgame.plugin.EndgameQoL;
import endgame.plugin.components.PlayerEndgameComponent;
import endgame.plugin.migration.LegacyDataMigration;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * RefSystem that ensures PlayerEndgameComponent exists on every player entity.
 *
 * Uses RefSystem (NOT RefChangeSystem) so that onEntityAdded fires for BOTH:
 *   - New entities (first connect — Player component added via command buffer)
 *   - BSON-loaded entities (reconnect — entity restored from save file)
 *
 * RefChangeSystem only fires when a component is added via command buffer,
 * which does NOT happen for BSON-loaded entities on reconnect.
 *
 * If the component is new (dataVersion == 0), migrates legacy data from old config files.
 * Then notifies EndgameQoL to cache the component for manager access.
 */
public class PlayerDataEnsureSystem extends RefSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.get("EndgameQoL.PlayerData");

    private final ComponentType<EntityStore, PlayerEndgameComponent> playerDataType;

    public PlayerDataEnsureSystem(ComponentType<EntityStore, PlayerEndgameComponent> playerDataType) {
        this.playerDataType = playerDataType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        // Only match entities that have a Player component
        return Query.and(Player.getComponentType());
    }

    @Override
    public void onEntityAdded(@Nonnull Ref<EntityStore> ref, @Nonnull AddReason reason,
                              @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        UUID uuid = endgame.plugin.utils.EntityUtils.getUuid(store, ref);
        if (uuid == null) return;

        // Use commandBuffer.run() to ensure we see any pending component additions
        cb.run(s -> {
            PlayerEndgameComponent comp = s.ensureAndGetComponent(ref, playerDataType);
            if (comp == null) {
                LOGGER.atWarning().log("[PlayerData] Failed to ensure component for %s", uuid);
                return;
            }

            // Migration: if component is empty (version 0), migrate legacy data
            if (comp.getDataVersion() == 0) {
                LegacyDataMigration migration = EndgameQoL.getInstance().getLegacyMigration();
                if (migration != null) {
                    migration.migratePlayer(uuid.toString(), comp);
                    LOGGER.atFine().log("[PlayerData] Migrated legacy data for %s", uuid);
                }
                comp.setDataVersion(1);
            }

            // Cache component in EndgameQoL for manager access
            EndgameQoL.getInstance().onPlayerComponentReady(uuid, comp);
            LOGGER.atFine().log("[PlayerData] Component ready for %s (v%d)", uuid, comp.getDataVersion());
        });
    }

    @Override
    public void onEntityRemove(@Nonnull Ref<EntityStore> ref, @Nonnull RemoveReason reason,
                               @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> cb) {
        // No cleanup needed — handled by EventRegistry.onPlayerDisconnect
    }
}
