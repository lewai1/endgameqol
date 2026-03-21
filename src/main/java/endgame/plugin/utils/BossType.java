package endgame.plugin.utils;

import javax.annotation.Nullable;

/**
 * All boss/elite mob types in the EndgameQoL mod.
 * Default values here serve as fallbacks when config has no override (value = 0).
 */
public enum BossType {
    DRAGON_FROST("Dragon Frost", 1400, 50, 700, true, "endgame_dragon_frost"),
    HEDERA("Hedera", 1800, 50, 900, true, "endgame_hedera"),
    GOLEM_VOID("Golem Void", 3500, 50, 3500, true, "endgame_golem_void"),
    ALPHA_REX("Alpha Rex", 700, 0, 350, false, "alpha_rex"),
    SPECTRE_VOID("Spectre Void", 120, 0, 250, false, "spectre_void", "spectrevoid"),
    DRAGON_FIRE("Dragon Fire", 1000, 0, 500, false, "endgame_dragon_fire", "dragon_fire", "fire_dragon"),
    ZOMBIE_ABERRANT("Zombie Aberrant", 400, 0, 200, false, "zombie_aberrant"),
    SWAMP_CROCODILE("Swamp Crocodile", 900, 0, 500, false, "swamp_crocodile"),
    BRAMBLE_ELITE("Bramble Elite", 550, 0, 350, false, "bramble_elite");

    private final String displayName;
    private final int defaultHealth;
    private final int defaultPlayerScaling;
    private final int defaultXpReward;
    private final boolean boss; // true = boss, false = mob/elite
    private final String[] patterns;

    BossType(String displayName, int defaultHealth, int defaultPlayerScaling, int defaultXpReward,
             boolean boss, String... patterns) {
        this.displayName = displayName;
        this.defaultHealth = defaultHealth;
        this.defaultPlayerScaling = defaultPlayerScaling;
        this.defaultXpReward = defaultXpReward;
        this.boss = boss;
        this.patterns = patterns;
    }

    @Nullable
    public static BossType fromTypeId(String npcTypeId) {
        if (npcTypeId == null) return null;
        String lower = npcTypeId.toLowerCase();
        for (BossType type : values()) {
            for (String pattern : type.patterns) {
                if (lower.contains(pattern)) return type;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getDefaultHealth() {
        return defaultHealth;
    }

    public int getDefaultPlayerScaling() {
        return defaultPlayerScaling;
    }

    public int getDefaultXpReward() {
        return defaultXpReward;
    }

    public boolean isBoss() {
        return boss;
    }

    /**
     * Config map key — same as enum name (e.g. "DRAGON_FROST").
     */
    public String getConfigKey() {
        return name();
    }
}
