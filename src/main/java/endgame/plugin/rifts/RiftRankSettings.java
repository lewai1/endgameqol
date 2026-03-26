package endgame.plugin.rifts;

/**
 * Per-rank configuration loaded from config. Mutable so the config loader can update values.
 */
public class RiftRankSettings {

    final RiftRank rank;
    int minLevel;
    int maxLevel;
    int weight;
    boolean tiered;

    RiftRankSettings(RiftRank rank) {
        this.rank = rank;
        this.minLevel = rank.defaultMinLevel;
        this.maxLevel = rank.defaultMaxLevel;
        this.weight = rank.defaultWeight;
        this.tiered = rank.defaultTiered;
    }
}
