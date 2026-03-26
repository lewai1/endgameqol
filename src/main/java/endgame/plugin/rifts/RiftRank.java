package endgame.plugin.rifts;

import javax.annotation.Nullable;

/**
 * Solo Leveling-style dungeon ranks. Each rank has a configurable mob-level range,
 * spawn weight, and whether the dungeon uses EL tiered scaling.
 * All values are defaults that get overridden by config.
 */
public enum RiftRank {
    E("E-Rank", "#8a8a8a",   1,  10,  30, false),
    D("D-Rank", "#4fc3f7",  10,  25,  25, false),
    C("C-Rank", "#66bb6a",  25,  50,  20, false),
    B("B-Rank", "#ffa726",  50,  80,  13, true),
    A("A-Rank", "#ef5350",  80, 120,   8, true),
    S("S-Rank", "#ab47bc", 120, 200,   4, true);

    public final String label;
    public final String color;
    public final int defaultMinLevel;
    public final int defaultMaxLevel;
    public final int defaultWeight;
    public final boolean defaultTiered;

    RiftRank(String label, String color, int defaultMinLevel, int defaultMaxLevel,
             int defaultWeight, boolean defaultTiered) {
        this.label = label;
        this.color = color;
        this.defaultMinLevel = defaultMinLevel;
        this.defaultMaxLevel = defaultMaxLevel;
        this.defaultWeight = defaultWeight;
        this.defaultTiered = defaultTiered;
    }

    @Nullable
    public static RiftRank fromName(String name) {
        if (name == null) return null;
        for (RiftRank rank : values()) {
            if (rank.name().equalsIgnoreCase(name) || rank.label.equalsIgnoreCase(name)) {
                return rank;
            }
        }
        return null;
    }
}
