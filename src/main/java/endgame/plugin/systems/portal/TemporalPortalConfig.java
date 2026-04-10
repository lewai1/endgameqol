package endgame.plugin.systems.portal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.map.MapCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for the Temporal Portal system.
 * Dungeons are data-driven — stored as a Map<String, DungeonDefinition>.
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
            .append(new KeyedCodec<Integer>("MaxConcurrentPortals", Codec.INTEGER),
                    (c, v) -> c.maxConcurrentPortals = v != null ? clamp(v, 1, 20) : 3,
                    c -> c.maxConcurrentPortals).add()
            .append(new KeyedCodec<Float>("SpawnMinDistance", Codec.FLOAT),
                    (c, v) -> c.spawnMinDistance = v != null ? clamp(v, 20f, 200f) : 80f,
                    c -> c.spawnMinDistance).add()
            .append(new KeyedCodec<Float>("SpawnMaxDistance", Codec.FLOAT),
                    (c, v) -> c.spawnMaxDistance = v != null ? clamp(v, 50f, 500f) : 300f,
                    c -> c.spawnMaxDistance).add()
            .append(new KeyedCodec<Float>("SpawnChance", Codec.FLOAT),
                    (c, v) -> c.spawnChance = v != null ? clamp(v, 0.05f, 1.0f) : 0.40f,
                    c -> c.spawnChance).add()
            .append(new KeyedCodec<Float>("MinDistanceBetweenPortals", Codec.FLOAT),
                    (c, v) -> c.minDistanceBetweenPortals = v != null ? clamp(v, 20f, 1000f) : 100f,
                    c -> c.minDistanceBetweenPortals).add()
            .append(new KeyedCodec<Integer>("GracePeriodSeconds", Codec.INTEGER),
                    (c, v) -> c.gracePeriodSeconds = v != null ? clamp(v, 5, 120) : 30,
                    c -> c.gracePeriodSeconds).add()
            .append(new KeyedCodec<Float>("AnnounceRadius", Codec.FLOAT),
                    (c, v) -> c.announceRadius = v != null ? clamp(v, 10f, 500f) : 100f,
                    c -> c.announceRadius).add()
            // Data-driven dungeons — Map<dungeonId, DungeonDefinition>
            .append(new KeyedCodec<Map<String, DungeonDefinition>>("Dungeons",
                    new MapCodec<>(DungeonDefinition.CODEC, ConcurrentHashMap::new, false)),
                    (c, v) -> { if (v != null && !v.isEmpty()) c.dungeons.putAll(v); },
                    c -> c.dungeons).add()
            .build();

    // Fields
    private boolean enabled = true;
    private int spawnIntervalMinSeconds = 300;
    private int spawnIntervalMaxSeconds = 600;
    private int maxConcurrentPortals = 3;
    private float spawnMinDistance = 80f;
    private float spawnMaxDistance = 300f;
    private float spawnChance = 0.40f;
    private float minDistanceBetweenPortals = 100f;
    private int gracePeriodSeconds = 30;
    private float announceRadius = 100f;

    // Data-driven dungeon list — defaults populated in constructor
    private final Map<String, DungeonDefinition> dungeons = new ConcurrentHashMap<>();

    public TemporalPortalConfig() {
        // Populate defaults if empty (first load)
        dungeons.putAll(DungeonDefinition.defaults());
    }

    // Getters — general
    public boolean isEnabled() { return enabled; }
    public int getSpawnIntervalMinSeconds() { return spawnIntervalMinSeconds; }
    public int getSpawnIntervalMaxSeconds() { return Math.max(spawnIntervalMinSeconds, spawnIntervalMaxSeconds); }
    public int getMaxConcurrentPortals() { return maxConcurrentPortals; }
    public float getSpawnMinDistance() { return spawnMinDistance; }
    public float getSpawnMaxDistance() { return Math.max(spawnMinDistance + 20f, spawnMaxDistance); }
    public float getSpawnChance() { return spawnChance; }
    public float getMinDistanceBetweenPortals() { return minDistanceBetweenPortals; }
    public int getGracePeriodSeconds() { return gracePeriodSeconds; }
    public float getAnnounceRadius() { return announceRadius; }

    // Getters — dungeons (data-driven)
    @Nonnull
    public Map<String, DungeonDefinition> getDungeons() { return dungeons; }

    @Nonnull
    public List<DungeonDefinition> getEnabledDungeons() {
        List<DungeonDefinition> result = new ArrayList<>();
        for (DungeonDefinition def : dungeons.values()) {
            if (def.isEnabled()) result.add(def);
        }
        return result;
    }

    @Nullable
    public DungeonDefinition getDungeonById(@Nonnull String id) {
        return dungeons.get(id);
    }

    // Setters
    public void setEnabled(boolean e) { this.enabled = e; }
    public void setSpawnIntervalMinSeconds(int v) { this.spawnIntervalMinSeconds = clamp(v, 30, 3600); }
    public void setSpawnIntervalMaxSeconds(int v) { this.spawnIntervalMaxSeconds = clamp(v, 30, 7200); }
    public void setMaxConcurrentPortals(int v) { this.maxConcurrentPortals = clamp(v, 1, 20); }
    public void setSpawnMinDistance(float v) { this.spawnMinDistance = clamp(v, 20f, 200f); }
    public void setSpawnMaxDistance(float v) { this.spawnMaxDistance = clamp(v, 50f, 500f); }
    public void setSpawnChance(float v) { this.spawnChance = clamp(v, 0.05f, 1.0f); }
    public void setMinDistanceBetweenPortals(float v) { this.minDistanceBetweenPortals = clamp(v, 20f, 1000f); }
    public void setGracePeriodSeconds(int v) { this.gracePeriodSeconds = clamp(v, 5, 120); }
    public void setAnnounceRadius(float v) { this.announceRadius = clamp(v, 10f, 500f); }

    private static int clamp(int val, int min, int max) { return Math.max(min, Math.min(max, val)); }
    private static float clamp(float val, float min, float max) { return Math.max(min, Math.min(max, val)); }
}
