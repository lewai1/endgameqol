package endgame.plugin.systems.portal;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Tracks the state of one active temporal portal + its linked instance.
 * Thread-safe: all mutable fields are volatile.
 */
public class TemporalPortalSession {

    public enum InstanceState { NONE, SPAWNING, READY, FAILED }

    /** Lifetime status — visual/warning progression inspired by Shards mod. */
    public enum LifetimeStatus { STABLE, DESTABILIZING, CRITICAL, COLLAPSING }

    // Identity
    @Nonnull private final String id;
    @Nonnull private final DungeonDefinition dungeonDef;
    private final long createdAtMs;
    private final int portalDurationSeconds;

    // Overworld portal
    @Nullable private volatile String spawnWorldName;
    @Nullable private volatile Vector3d portalPosition;

    // Instance
    private volatile InstanceState instanceState = InstanceState.NONE;
    @Nullable private volatile World instanceWorld;

    // Lifetime warnings
    private volatile boolean warned5min = false;
    private volatile boolean warned1min = false;
    private volatile LifetimeStatus lifetimeStatus = LifetimeStatus.STABLE;

    public TemporalPortalSession(@Nonnull String id, @Nonnull DungeonDefinition dungeonDef, int portalDurationSeconds) {
        this.id = id;
        this.dungeonDef = dungeonDef;
        this.createdAtMs = System.currentTimeMillis();
        this.portalDurationSeconds = portalDurationSeconds;
    }

    // --- Identity ---
    @Nonnull public String getId() { return id; }
    @Nonnull public DungeonDefinition getDungeonDef() { return dungeonDef; }
    public long getCreatedAtMs() { return createdAtMs; }

    // --- Overworld portal ---
    @Nullable public String getSpawnWorldName() { return spawnWorldName; }
    public void setSpawnWorldName(@Nullable String name) { this.spawnWorldName = name; }
    @Nullable public Vector3d getPortalPosition() { return portalPosition; }
    public void setPortalPosition(@Nullable Vector3d pos) { this.portalPosition = pos; }

    // --- Instance ---
    public InstanceState getInstanceState() { return instanceState; }
    public void setInstanceState(InstanceState s) { this.instanceState = s; }
    @Nullable public World getInstanceWorld() { return instanceWorld; }
    public void setInstanceWorld(@Nullable World w) {
        this.instanceWorld = w;
        if (w != null) this.instanceState = InstanceState.READY;
    }

    // --- Lifetime ---
    public long getRemainingMs() {
        long elapsed = System.currentTimeMillis() - createdAtMs;
        return Math.max(0, portalDurationSeconds * 1000L - elapsed);
    }

    public int getRemainingSeconds() {
        return (int) (getRemainingMs() / 1000L);
    }

    public boolean isPortalExpired() {
        return getRemainingMs() <= 0;
    }

    public boolean shouldWarn5min() {
        if (warned5min) return false;
        if (getRemainingSeconds() <= 300) { warned5min = true; return true; }
        return false;
    }

    public boolean shouldWarn1min() {
        if (warned1min) return false;
        if (getRemainingSeconds() <= 60) { warned1min = true; return true; }
        return false;
    }

    @Nonnull
    public LifetimeStatus updateLifetimeStatus() {
        long remaining = getRemainingMs();
        long total = portalDurationSeconds * 1000L;
        LifetimeStatus newStatus;
        if (remaining <= 0) {
            newStatus = LifetimeStatus.COLLAPSING;
        } else if (remaining < total / 4) {
            newStatus = LifetimeStatus.CRITICAL;
        } else if (remaining < total / 2) {
            newStatus = LifetimeStatus.DESTABILIZING;
        } else {
            newStatus = LifetimeStatus.STABLE;
        }
        this.lifetimeStatus = newStatus;
        return newStatus;
    }

    @Nonnull
    public LifetimeStatus getLifetimeStatus() { return lifetimeStatus; }
}
