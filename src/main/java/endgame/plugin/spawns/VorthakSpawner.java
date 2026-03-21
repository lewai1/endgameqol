package endgame.plugin.spawns;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import endgame.plugin.EndgameQoL;

/**
 * Spawns Vorthak merchant NPC in the Forgotten Temple.
 */
public class VorthakSpawner {

    private static final String VORTHAK_NPC_ID = "Vorthak";

    // Position in Forgotten Temple
    private static final double SPAWN_X = 4983.0;
    private static final double SPAWN_Y = 157.0;
    private static final double SPAWN_Z = 4981.0;

    // Rotation: East = -90 degrees yaw (pitch, yaw, roll)
    private static final float ROTATION_YAW = -90.0f;

    private final EndgameQoL plugin;

    public VorthakSpawner(EndgameQoL plugin) {
        this.plugin = plugin;
    }

    /**
     * Spawn Vorthak in the given store at the predefined position.
     */
    public void spawn(Store<EntityStore> store) {
        if (store == null) {
            plugin.getLogger().atWarning().log("[VorthakSpawner] Cannot spawn - store is null");
            return;
        }

        Vector3d position = new Vector3d(SPAWN_X, SPAWN_Y, SPAWN_Z);
        Vector3f rotation = new Vector3f(0.0f, ROTATION_YAW, 0.0f); // pitch, yaw, roll

        try {
            NPCPlugin.get().spawnNPC(store, VORTHAK_NPC_ID, null, position, rotation);
            plugin.getLogger().atFine().log("[VorthakSpawner] Spawned Vorthak at (%.1f, %.1f, %.1f)",
                    SPAWN_X, SPAWN_Y, SPAWN_Z);
        } catch (Exception e) {
            plugin.getLogger().atWarning().log("[VorthakSpawner] Failed to spawn Vorthak: %s", e.getMessage());
        }
    }
}
