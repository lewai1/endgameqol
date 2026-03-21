package endgame.plugin.systems.trial;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import endgame.plugin.EndgameQoL;

import javax.annotation.Nonnull;

/**
 * Detects death of NPCs that are part of an active Warden Challenge wave.
 * When a tracked NPC dies, notifies the WardenTrialManager to update wave state.
 *
 * Pattern matches GenericBossDeathSystem.
 */
public class WardenTrialDeathSystem extends DeathSystems.OnDeathSystem {

    private final EndgameQoL plugin;
    private final WardenTrialManager manager;

    public WardenTrialDeathSystem(EndgameQoL plugin, WardenTrialManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType());
    }

    @Override
    public void onComponentAdded(@Nonnull Ref<EntityStore> ref, @Nonnull DeathComponent component,
                                  @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            if (manager.isTrackedNpc(ref)) {
                // Clean up RPG Leveling spawn level override to prevent memory leak
                if (plugin.isRPGLevelingActive()) {
                    plugin.getRpgLevelingBridge().clearMobLevel(store, ref);
                }

                manager.onEnemyDeath(ref);
                plugin.getLogger().atFine().log("[WardenChallenge] Wave NPC died: %s", ref);
            }
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[WardenChallenge] Error handling NPC death: %s", e.getMessage());
        }
    }
}
