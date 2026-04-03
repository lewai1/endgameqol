package endgame.plugin.systems.portal;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the state of one active temporal portal + its linked instance.
 * Thread-safe: all mutable fields are volatile, player set is concurrent.
 */
public class TemporalPortalSession {

    /**
     * Dungeon types available for temporal portals.
     * PLACEHOLDER — will be replaced by real temporal dungeon definitions.
     */
    public enum DungeonType {
        FROZEN_DUNGEON("Endgame_Frozen_Dungeon", "Frozen Dungeon", "#5bceff"),
        SWAMP_DUNGEON("Endgame_Swamp_Dungeon", "Swamp Dungeon", "#23970c");

        private final String instanceId;
        private final String displayName;
        private final String color;

        DungeonType(String instanceId, String displayName, String color) {
            this.instanceId = instanceId;
            this.displayName = displayName;
            this.color = color;
        }

        public String getInstanceId() { return instanceId; }
        public String getDisplayName() { return displayName; }
        public String getColor() { return color; }
    }

    // Identity
    @Nonnull private final String id;
    @Nonnull private final DungeonType dungeonType;
    private final long createdAtMs;

    // Portal entity (overworld)
    @Nullable private volatile Ref<EntityStore> portalEntityRef;
    @Nullable private volatile String spawnWorldName;
    @Nullable private volatile Vector3d portalPosition;

    // Instance world
    @Nullable private volatile String instanceWorldName;
    private volatile boolean instanceReady;
    private volatile long instanceStartedAtMs;
    private volatile long lastPlayerActivityMs;

    // Return portal (inside instance)
    @Nullable private volatile Ref<EntityStore> returnPortalRef;

    // Warning flags (prevent repeated warnings)
    private volatile boolean warnedPortalExpiry;
    private volatile boolean warnedInstanceExpiry;

    // Players currently inside the instance
    private final Set<UUID> playersInside = ConcurrentHashMap.newKeySet();

    public TemporalPortalSession(@Nonnull String id, @Nonnull DungeonType dungeonType) {
        this.id = id;
        this.dungeonType = dungeonType;
        this.createdAtMs = System.currentTimeMillis();
        this.lastPlayerActivityMs = this.createdAtMs;
    }

    // --- Identity ---
    @Nonnull public String getId() { return id; }
    @Nonnull public DungeonType getDungeonType() { return dungeonType; }
    public long getCreatedAtMs() { return createdAtMs; }

    // --- Portal entity ---
    @Nullable public Ref<EntityStore> getPortalEntityRef() { return portalEntityRef; }
    public void setPortalEntityRef(@Nullable Ref<EntityStore> ref) { this.portalEntityRef = ref; }
    @Nullable public String getSpawnWorldName() { return spawnWorldName; }
    public void setSpawnWorldName(@Nullable String name) { this.spawnWorldName = name; }
    @Nullable public Vector3d getPortalPosition() { return portalPosition; }
    public void setPortalPosition(@Nullable Vector3d pos) { this.portalPosition = pos; }

    // --- Instance ---
    @Nullable public String getInstanceWorldName() { return instanceWorldName; }
    public void setInstanceWorldName(@Nullable String name) { this.instanceWorldName = name; }
    public boolean isInstanceReady() { return instanceReady; }
    public void setInstanceReady(boolean ready) {
        this.instanceReady = ready;
        if (ready) this.instanceStartedAtMs = System.currentTimeMillis();
    }
    public long getInstanceStartedAtMs() { return instanceStartedAtMs; }

    // --- Return portal ---
    @Nullable public Ref<EntityStore> getReturnPortalRef() { return returnPortalRef; }
    public void setReturnPortalRef(@Nullable Ref<EntityStore> ref) { this.returnPortalRef = ref; }

    // --- Players ---
    public Set<UUID> getPlayersInside() { return playersInside; }
    public void recordPlayerActivity() { this.lastPlayerActivityMs = System.currentTimeMillis(); }

    // --- Warning flags ---
    public boolean hasWarnedPortalExpiry() { return warnedPortalExpiry; }
    public void setWarnedPortalExpiry(boolean v) { this.warnedPortalExpiry = v; }
    public boolean hasWarnedInstanceExpiry() { return warnedInstanceExpiry; }
    public void setWarnedInstanceExpiry(boolean v) { this.warnedInstanceExpiry = v; }

    // --- Lifecycle checks ---

    /** Has the overworld portal NPC expired? */
    public boolean isPortalExpired(int durationSeconds) {
        return System.currentTimeMillis() - createdAtMs > durationSeconds * 1000L;
    }

    /** Is the portal about to expire (within 30s)? */
    public boolean isPortalAboutToExpire(int durationSeconds) {
        long remaining = (durationSeconds * 1000L) - (System.currentTimeMillis() - createdAtMs);
        return remaining > 0 && remaining <= 30_000;
    }

    /** Is the instance about to close (within 30s of time limit)? */
    public boolean isInstanceAboutToExpire(int timeLimitSeconds) {
        if (!instanceReady || instanceStartedAtMs == 0) return false;
        long remaining = (timeLimitSeconds * 1000L) - (System.currentTimeMillis() - instanceStartedAtMs);
        return remaining > 0 && remaining <= 30_000;
    }

    /** Has the instance exceeded its hard time limit? */
    public boolean isInstanceTimeLimitReached(int timeLimitSeconds) {
        if (!instanceReady || instanceStartedAtMs == 0) return false;
        return System.currentTimeMillis() - instanceStartedAtMs > timeLimitSeconds * 1000L;
    }

    /** Has the instance been idle (no players) for too long? */
    public boolean isInstanceIdle(int idleTimeoutMinutes) {
        if (!instanceReady || !playersInside.isEmpty()) return false;
        return System.currentTimeMillis() - lastPlayerActivityMs > idleTimeoutMinutes * 60_000L;
    }

    /** Is the portal entity still valid in the world? */
    public boolean isPortalEntityValid() {
        Ref<EntityStore> ref = portalEntityRef;
        return ref != null && ref.isValid();
    }
}
