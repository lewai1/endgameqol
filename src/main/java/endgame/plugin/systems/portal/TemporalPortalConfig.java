package endgame.plugin.systems.portal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for the Temporal Portal system.
 * Portals spawn randomly near players and teleport them to temporary dungeon instances.
 */
public class TemporalPortalConfig {

    public static final BuilderCodec<TemporalPortalConfig> CODEC = BuilderCodec
            .builder(TemporalPortalConfig.class, TemporalPortalConfig::new)
            .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                    (c, v) -> c.enabled = v != null ? v : true, c -> c.enabled).add()
            .append(new KeyedCodec<Integer>("SpawnIntervalMinSeconds", Codec.INTEGER),
                    (c, v) -> c.spawnIntervalMinSeconds = v != null ? clamp(v, 30, 3600) : 300,
                    c -> c.spawnIntervalMinSeconds).add()
            .append(new KeyedCodec<Integer>("SpawnIntervalMaxSeconds", Codec.INTEGER),
                    (c, v) -> c.spawnIntervalMaxSeconds = v != null ? clamp(v, 30, 7200) : 600,
                    c -> c.spawnIntervalMaxSeconds).add()
            .append(new KeyedCodec<Integer>("PortalDurationSeconds", Codec.INTEGER),
                    (c, v) -> c.portalDurationSeconds = v != null ? clamp(v, 30, 1800) : 180,
                    c -> c.portalDurationSeconds).add()
            .append(new KeyedCodec<Integer>("MaxConcurrentPortals", Codec.INTEGER),
                    (c, v) -> c.maxConcurrentPortals = v != null ? clamp(v, 1, 20) : 3,
                    c -> c.maxConcurrentPortals).add()
            .append(new KeyedCodec<Integer>("InstanceTimeLimitSeconds", Codec.INTEGER),
                    (c, v) -> c.instanceTimeLimitSeconds = v != null ? clamp(v, 60, 3600) : 600,
                    c -> c.instanceTimeLimitSeconds).add()
            .append(new KeyedCodec<Integer>("InstanceIdleTimeoutMinutes", Codec.INTEGER),
                    (c, v) -> c.instanceIdleTimeoutMinutes = v != null ? clamp(v, 1, 60) : 10,
                    c -> c.instanceIdleTimeoutMinutes).add()
            .append(new KeyedCodec<Float>("SpawnOffsetRadius", Codec.FLOAT),
                    (c, v) -> c.spawnOffsetRadius = v != null ? clamp(v, 3f, 30f) : 8f,
                    c -> c.spawnOffsetRadius).add()
            .append(new KeyedCodec<Float>("AnnounceRadius", Codec.FLOAT),
                    (c, v) -> c.announceRadius = v != null ? clamp(v, 10f, 200f) : 50f,
                    c -> c.announceRadius).add()
            // PLACEHOLDER toggles — will be replaced by temporal dungeon definitions
            .append(new KeyedCodec<Boolean>("FrozenDungeonEnabled", Codec.BOOLEAN),
                    (c, v) -> c.frozenDungeonEnabled = v != null ? v : true,
                    c -> c.frozenDungeonEnabled).add()
            .append(new KeyedCodec<Boolean>("SwampDungeonEnabled", Codec.BOOLEAN),
                    (c, v) -> c.swampDungeonEnabled = v != null ? v : true,
                    c -> c.swampDungeonEnabled).add()
            .build();

    // Fields with defaults
    private boolean enabled = true;
    private int spawnIntervalMinSeconds = 300;      // 5 minutes
    private int spawnIntervalMaxSeconds = 600;      // 10 minutes
    private int portalDurationSeconds = 180;        // 3 minutes before portal NPC despawns
    private int maxConcurrentPortals = 3;
    private int instanceTimeLimitSeconds = 600;     // 10 minutes hard limit on instance
    private int instanceIdleTimeoutMinutes = 10;    // cleanup if no players for 10 min
    private float spawnOffsetRadius = 8f;           // blocks from player
    private float announceRadius = 50f;             // chat announcement range
    // PLACEHOLDER per-dungeon toggles
    private boolean frozenDungeonEnabled = true;
    private boolean swampDungeonEnabled = true;

    // Getters
    public boolean isEnabled() { return enabled; }
    public int getSpawnIntervalMinSeconds() { return spawnIntervalMinSeconds; }
    public int getSpawnIntervalMaxSeconds() { return Math.max(spawnIntervalMinSeconds, spawnIntervalMaxSeconds); }
    public int getPortalDurationSeconds() { return portalDurationSeconds; }
    public int getMaxConcurrentPortals() { return maxConcurrentPortals; }
    public int getInstanceTimeLimitSeconds() { return instanceTimeLimitSeconds; }
    public int getInstanceIdleTimeoutMinutes() { return instanceIdleTimeoutMinutes; }
    public float getSpawnOffsetRadius() { return spawnOffsetRadius; }
    public float getAnnounceRadius() { return announceRadius; }
    public boolean isFrozenDungeonEnabled() { return frozenDungeonEnabled; }
    public boolean isSwampDungeonEnabled() { return swampDungeonEnabled; }

    // Setters
    public void setEnabled(boolean e) { this.enabled = e; }
    public void setSpawnIntervalMinSeconds(int v) { this.spawnIntervalMinSeconds = clamp(v, 30, 3600); }
    public void setSpawnIntervalMaxSeconds(int v) { this.spawnIntervalMaxSeconds = clamp(v, 30, 7200); }
    public void setPortalDurationSeconds(int v) { this.portalDurationSeconds = clamp(v, 30, 1800); }
    public void setMaxConcurrentPortals(int v) { this.maxConcurrentPortals = clamp(v, 1, 20); }
    public void setInstanceTimeLimitSeconds(int v) { this.instanceTimeLimitSeconds = clamp(v, 60, 3600); }
    public void setInstanceIdleTimeoutMinutes(int v) { this.instanceIdleTimeoutMinutes = clamp(v, 1, 60); }
    public void setSpawnOffsetRadius(float v) { this.spawnOffsetRadius = clamp(v, 3f, 30f); }
    public void setAnnounceRadius(float v) { this.announceRadius = clamp(v, 10f, 200f); }
    public void setFrozenDungeonEnabled(boolean e) { this.frozenDungeonEnabled = e; }
    public void setSwampDungeonEnabled(boolean e) { this.swampDungeonEnabled = e; }

    private static int clamp(int val, int min, int max) { return Math.max(min, Math.min(max, val)); }
    private static float clamp(float val, float min, float max) { return Math.max(min, Math.min(max, val)); }
}
