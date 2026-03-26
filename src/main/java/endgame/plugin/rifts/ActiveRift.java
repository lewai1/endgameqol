package endgame.plugin.rifts;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Represents an active rift portal in the world.
 */
public final class ActiveRift {

    private final String portalId;
    private final RiftDungeonType dungeonType;
    private final RiftRank rank;
    private final RiftRankSettings rankSettings;
    private final int mobLevel;
    private final String worldName;
    private final Vector3d position;
    private final long spawnTimeMs;
    private final int lifetimeSeconds;

    public enum InstanceState { NONE, SPAWNING, READY, FAILED }

    // Mutable: set once the instance world is ready
    private volatile World instanceWorld;
    private volatile InstanceState instanceState = InstanceState.NONE;

    // Actual position of the return portal inside the instance (set after placement)
    private volatile Vector3d returnPortalPos;

    // Chunk pre-loading: expanding ring from spawn point
    private volatile int preloadRing = 0;
    private volatile boolean preloadComplete = false;

    public ActiveRift(@Nonnull String portalId,
                      @Nonnull RiftDungeonType dungeonType,
                      @Nonnull RiftRank rank,
                      @Nonnull RiftRankSettings rankSettings,
                      int mobLevel,
                      @Nonnull String worldName,
                      @Nonnull Vector3d position,
                      long spawnTimeMs,
                      int lifetimeSeconds) {
        this.portalId = (portalId == null || portalId.isBlank())
                ? UUID.randomUUID().toString().substring(0, 8)
                : portalId;
        this.dungeonType = dungeonType;
        this.rank = rank;
        this.rankSettings = rankSettings;
        this.mobLevel = mobLevel;
        this.worldName = worldName;
        this.position = position;
        this.spawnTimeMs = spawnTimeMs;
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public String portalId() { return portalId; }
    public RiftDungeonType dungeonType() { return dungeonType; }
    public RiftRank rank() { return rank; }
    public RiftRankSettings rankSettings() { return rankSettings; }
    public int mobLevel() { return mobLevel; }
    public String worldName() { return worldName; }
    public Vector3d position() { return position; }
    public long spawnTimeMs() { return spawnTimeMs; }
    public int lifetimeSeconds() { return lifetimeSeconds; }

    @Nullable
    public World instanceWorld() { return instanceWorld; }

    public void setInstanceWorld(@Nullable World world) { this.instanceWorld = world; }

    public InstanceState instanceState() { return instanceState; }
    public void setInstanceState(InstanceState state) { this.instanceState = state; }

    public int getPreloadRing() { return preloadRing; }
    public void setPreloadRing(int ring) { this.preloadRing = ring; }
    public boolean isPreloadComplete() { return preloadComplete; }
    public void setPreloadComplete(boolean complete) { this.preloadComplete = complete; }

    @Nullable
    public Vector3d returnPortalPos() { return returnPortalPos; }
    public void setReturnPortalPos(@Nullable Vector3d pos) { this.returnPortalPos = pos; }

    public boolean isExpired() {
        if (lifetimeSeconds <= 0) return false;
        return System.currentTimeMillis() - spawnTimeMs >= (long) lifetimeSeconds * 1000L;
    }

    public long remainingMs() {
        if (lifetimeSeconds <= 0) return Long.MAX_VALUE;
        long elapsed = System.currentTimeMillis() - spawnTimeMs;
        return Math.max(0, (long) lifetimeSeconds * 1000L - elapsed);
    }

    public boolean isTiered() {
        return rankSettings.tiered;
    }

    public String levelOverrideId() {
        return "hyrift_" + portalId;
    }
}
