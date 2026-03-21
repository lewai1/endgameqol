package endgame.plugin.config;

/**
 * Difficulty levels for boss encounters and mobs. Some tinkering huh ?
 * Each difficulty modifies boss health and damage.
 * 
 * Medium is the default/baseline with no modifications.
 */
public enum Difficulty {
    EASY("Easy", 0.6f, 0.5f), // 60% health, 50% damage
    MEDIUM("Medium", 1.0f, 1.0f), // 100% health, 100% damage (default)
    HARD("Hard", 1.5f, 1.5f), // 150% health, 150% damage
    EXTREME("Extreme", 2.5f, 2.0f), // 250% health, 200% damage
    CUSTOM("Custom", 1.0f, 1.0f); // User-defined multipliers (stored in config)

    private final String displayName;
    private final float healthMultiplier;
    private final float damageMultiplier;

    Difficulty(String displayName, float healthMultiplier, float damageMultiplier) {
        this.displayName = displayName;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getHealthMultiplier() {
        return healthMultiplier;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    /**
     * Get difficulty from string name (case-insensitive).
     * Returns MEDIUM if not found.
     */
    public static Difficulty fromString(String name) {
        if (name == null)
            return MEDIUM;
        for (Difficulty d : values()) {
            if (d.name().equalsIgnoreCase(name) || d.displayName.equalsIgnoreCase(name)) {
                return d;
            }
        }
        return MEDIUM;
    }
}
