package endgame.plugin.systems.portal;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data-driven definition of a temporal portal dungeon.
 * Replaces the hardcoded DungeonType enum. Persisted in TemporalPortalConfig.
 */
public class DungeonDefinition {

    @Nonnull
    public static final BuilderCodec<DungeonDefinition> CODEC = BuilderCodec
            .builder(DungeonDefinition.class, DungeonDefinition::new)
            .append(new KeyedCodec<String>("InstanceId", Codec.STRING),
                    (d, v) -> d.instanceId = v != null ? v : "", d -> d.instanceId).add()
            .append(new KeyedCodec<String>("DisplayName", Codec.STRING),
                    (d, v) -> d.displayName = v != null ? v : "", d -> d.displayName).add()
            .append(new KeyedCodec<String>("Color", Codec.STRING),
                    (d, v) -> d.color = v != null ? v : "#ffffff", d -> d.color).add()
            .append(new KeyedCodec<String>("PortalTypeId", Codec.STRING),
                    (d, v) -> d.portalTypeId = v != null ? v : "", d -> d.portalTypeId).add()
            .append(new KeyedCodec<Integer>("PortalDurationSeconds", Codec.INTEGER),
                    (d, v) -> d.portalDurationSeconds = v != null ? v : 180, d -> d.portalDurationSeconds).add()
            .append(new KeyedCodec<Integer>("InstanceTimeLimitSeconds", Codec.INTEGER),
                    (d, v) -> d.instanceTimeLimitSeconds = v != null ? v : 600, d -> d.instanceTimeLimitSeconds).add()
            .append(new KeyedCodec<Boolean>("Enabled", Codec.BOOLEAN),
                    (d, v) -> d.enabled = v != null ? v : true, d -> d.enabled).add()
            .append(new KeyedCodec<Boolean>("AllowRespawnInside", Codec.BOOLEAN),
                    (d, v) -> d.allowRespawnInside = v != null ? v : false, d -> d.allowRespawnInside).add()
            .build();

    private String instanceId = "";
    private String displayName = "";
    private String color = "#ffffff";
    private String portalTypeId = "";
    private int portalDurationSeconds = 180;
    private int instanceTimeLimitSeconds = 600;
    private boolean enabled = true;
    private boolean allowRespawnInside = false;

    public DungeonDefinition() {}

    public DungeonDefinition(String instanceId, String displayName, String color,
                              String portalTypeId, int portalDurationSeconds,
                              int instanceTimeLimitSeconds, boolean enabled) {
        this.instanceId = instanceId;
        this.displayName = displayName;
        this.color = color;
        this.portalTypeId = portalTypeId;
        this.portalDurationSeconds = portalDurationSeconds;
        this.instanceTimeLimitSeconds = instanceTimeLimitSeconds;
        this.enabled = enabled;
    }

    // Getters
    @Nonnull public String getInstanceId() { return instanceId; }
    @Nonnull public String getDisplayName() { return displayName; }
    @Nonnull public String getColor() { return color; }
    @Nonnull public String getPortalTypeId() { return portalTypeId; }
    public int getPortalDurationSeconds() { return portalDurationSeconds; }
    public int getInstanceTimeLimitSeconds() { return instanceTimeLimitSeconds; }
    public boolean isEnabled() { return enabled; }
    public boolean isAllowRespawnInside() { return allowRespawnInside; }

    // Setters
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setAllowRespawnInside(boolean v) { this.allowRespawnInside = v; }
    public void setPortalDurationSeconds(int v) { this.portalDurationSeconds = v; }
    public void setInstanceTimeLimitSeconds(int v) { this.instanceTimeLimitSeconds = v; }

    /**
     * Default dungeon definitions for the 3 temporal portal instances.
     * Key = dungeon ID (used in admin commands and config).
     */
    @Nonnull
    public static Map<String, DungeonDefinition> defaults() {
        Map<String, DungeonDefinition> map = new LinkedHashMap<>();
        map.put("eldergrove_hollow", new DungeonDefinition(
                "Endgame_Eldergrove_Hollow", "Eldergrove Hollow", "#44cc66",
                "Endgame_Portal_Eldergrove_Hollow", 180, 900, true));
        map.put("oakwood_refuge", new DungeonDefinition(
                "Endgame_Oakwood_Refuge", "Oakwood Refuge", "#88aa44",
                "Endgame_Portal_Oakwood_Refuge", 180, 900, true));
        map.put("canopy_shrine", new DungeonDefinition(
                "Endgame_Canopy_Shrine", "Canopy Shrine", "#33aa55",
                "Endgame_Portal_Canopy_Shrine", 180, 900, true));
        return map;
    }
}
