package endgame.plugin.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Pet tier progression: D (base) → C → B → A → S → SS (max).
 * Each tier provides damage scaling and visual scale bonus.
 */
public enum PetTier {
    D("D", 1.0f, 0.00f, "#888888"),
    C("C", 1.3f, 0.05f, "#55ff55"),
    B("B", 1.5f, 0.10f, "#5599ff"),
    A("A", 1.8f, 0.20f, "#aa55ff"),
    S("S", 2.2f, 0.30f, "#ffaa00"),
    SS("SS", 2.5f, 0.40f, "#ff5555");

    private final String label;
    private final float damageMultiplier;
    private final float scaleBonus;
    private final String color;

    PetTier(String label, float damageMultiplier, float scaleBonus, String color) {
        this.label = label;
        this.damageMultiplier = damageMultiplier;
        this.scaleBonus = scaleBonus;
        this.color = color;
    }

    @Nonnull public String getLabel() { return label; }
    public float getDamageMultiplier() { return damageMultiplier; }
    public float getScaleBonus() { return scaleBonus; }
    @Nonnull public String getColor() { return color; }

    /**
     * Next tier in progression, or null if already SS.
     */
    @Nullable
    public PetTier next() {
        PetTier[] vals = values();
        int next = ordinal() + 1;
        return next < vals.length ? vals[next] : null;
    }

    /**
     * Parse tier from label string. Defaults to D if null or unrecognized.
     */
    @Nonnull
    public static PetTier fromLabel(@Nullable String label) {
        if (label == null || label.isEmpty()) return D;
        for (PetTier t : values()) {
            if (t.label.equals(label)) return t;
        }
        return D;
    }
}
