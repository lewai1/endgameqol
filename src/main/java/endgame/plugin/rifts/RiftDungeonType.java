package endgame.plugin.rifts;

import javax.annotation.Nullable;

/**
 * Registry of dungeon types that portals can lead to.
 * Add new entries here to support additional dungeon mods.
 */
public enum RiftDungeonType {

    // EndgameAndQoL dungeons — baseX/Y/Z must match each PortalType JSON's SpawnProviderOverride
    FROZEN_DUNGEON("frozen_dungeon", "Frozen Dungeon",
            "Endgame_Portal_Frozen_Dungeon",
            "Endgame_Dragon_Frost", new String[]{"Spirit_Frost"},
            -17, 104, 65, true),

    SWAMP_DUNGEON("swamp_dungeon", "Swamp Dungeon",
            "Endgame_Portal_Swamp_Dungeon",
            "Endgame_Hedera", new String[]{},
            4, 115, 145, true),

    // Golem Void — no SpawnProviderOverride. Vanilla handles the Fragment Exit
    // and spawn position. baseX/Y/Z unused (fixedSpawn=false).
    GOLEM_VOID("golem_void", "Golem Void",
            "Endgame_Portal_Void_Golem",
            "Endgame_Golem_Void", new String[]{"Eye_Void"},
            0, 0, 0, false);

    public final String id;
    public final String displayName;
    /** The PortalType asset ID (matches Server/PortalTypes/{id}.json) */
    public final String portalTypeId;
    public final String bossNpcId;
    public final String[] minionNpcIds;
    /** Base spawn position defined in the dungeon's instance.bson */
    public final double baseX, baseY, baseZ;
    /** true if the PortalType has a SpawnProviderOverride (fixed spawn point) */
    public final boolean fixedSpawn;

    RiftDungeonType(String id, String displayName,
                    String portalTypeId, String bossNpcId, String[] minionNpcIds,
                    double baseX, double baseY, double baseZ, boolean fixedSpawn) {
        this.id = id;
        this.displayName = displayName;
        this.portalTypeId = portalTypeId;
        this.bossNpcId = bossNpcId;
        this.minionNpcIds = minionNpcIds;
        this.baseX = baseX;
        this.baseY = baseY;
        this.baseZ = baseZ;
        this.fixedSpawn = fixedSpawn;
    }

    @Nullable
    public static RiftDungeonType fromId(String id) {
        if (id == null) return null;
        for (RiftDungeonType type : values()) {
            if (type.id.equalsIgnoreCase(id)) return type;
        }
        return null;
    }
}
