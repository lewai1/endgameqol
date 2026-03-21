package endgame.plugin.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import javax.annotation.Nonnull;

public class DifficultyConfig {

    @Nonnull
    public static final BuilderCodec<DifficultyConfig> CODEC = BuilderCodec
            .builder(DifficultyConfig.class, DifficultyConfig::new)
            .append(new KeyedCodec<String>("Preset", Codec.STRING),
                    (c, v) -> c.setDifficultyString(v),
                    DifficultyConfig::getDifficultyString)
            .add()
            .append(new KeyedCodec<Boolean>("AffectsBosses", Codec.BOOLEAN),
                    (c, v) -> c.affectsBosses = v != null ? v : true,
                    c -> c.affectsBosses)
            .add()
            .append(new KeyedCodec<Boolean>("AffectsMobs", Codec.BOOLEAN),
                    (c, v) -> c.affectsMobs = v != null ? v : true,
                    c -> c.affectsMobs)
            .add()
            .append(new KeyedCodec<Float>("CustomHealthMultiplier", Codec.FLOAT),
                    (c, v) -> c.setCustomHealthMultiplier(v != null ? v : 1.0f),
                    c -> c.customHealthMultiplier)
            .add()
            .append(new KeyedCodec<Float>("CustomDamageMultiplier", Codec.FLOAT),
                    (c, v) -> c.setCustomDamageMultiplier(v != null ? v : 1.0f),
                    c -> c.customDamageMultiplier)
            .add()
            .append(new KeyedCodec<Float>("BossDamagePlayerScaling", Codec.FLOAT),
                    (c, v) -> c.bossDamagePlayerScaling = v != null ? Math.max(0f, Math.min(1.0f, v)) : 0.15f,
                    c -> c.bossDamagePlayerScaling)
            .add()
            .build();

    private Difficulty difficulty = Difficulty.MEDIUM;
    private boolean affectsBosses = true;
    private boolean affectsMobs = true;
    private float customHealthMultiplier = 1.0f;
    private float customDamageMultiplier = 1.0f;
    private float bossDamagePlayerScaling = 0.15f;

    // === DIFFICULTY ===

    public Difficulty getDifficulty() { return difficulty; }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty != null ? difficulty : Difficulty.MEDIUM;
    }

    public String getDifficultyString() { return difficulty.name(); }

    public void setDifficultyString(String s) {
        this.difficulty = Difficulty.fromString(s);
    }

    // === CUSTOM MULTIPLIERS ===

    public float getCustomHealthMultiplier() { return customHealthMultiplier; }

    public void setCustomHealthMultiplier(float multiplier) {
        this.customHealthMultiplier = Math.max(0.1f, Math.min(10.0f, multiplier));
    }

    public float getCustomDamageMultiplier() { return customDamageMultiplier; }

    public void setCustomDamageMultiplier(float multiplier) {
        this.customDamageMultiplier = Math.max(0.1f, Math.min(10.0f, multiplier));
    }

    // === EFFECTIVE MULTIPLIERS ===

    public float getEffectiveHealthMultiplier() {
        return difficulty == Difficulty.CUSTOM ? customHealthMultiplier : difficulty.getHealthMultiplier();
    }

    public float getEffectiveDamageMultiplier() {
        return difficulty == Difficulty.CUSTOM ? customDamageMultiplier : difficulty.getDamageMultiplier();
    }

    // === SCOPE ===

    public float getBossDamagePlayerScaling() { return bossDamagePlayerScaling; }

    public boolean isAffectsBosses() { return affectsBosses; }
    public void setAffectsBosses(boolean affects) { this.affectsBosses = affects; }

    public boolean isAffectsMobs() { return affectsMobs; }
    public void setAffectsMobs(boolean affects) { this.affectsMobs = affects; }

    // === GROUP DAMAGE MULTIPLIERS ===

    public float getBossDamageMultiplier() {
        return affectsBosses ? getEffectiveDamageMultiplier() : 1.0f;
    }

    public float getMobDamageMultiplier() {
        return affectsMobs ? getEffectiveDamageMultiplier() : 1.0f;
    }
}
